package br.ufpa.dsai.estilomarcado.mcp;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.ufpa.dsai.estilomarcado.autenticacao.model.EstadoConta;
import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;
import br.ufpa.dsai.estilomarcado.autenticacao.repository.UsuarioRepository;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioPrincipal;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class McpOAuthController {
    public record Autorizacao(String response_type, String client_id, String redirect_uri,
                             String code_challenge, String code_challenge_method, String scope,
                             String state, String resource, String decisao) {}

    private final McpTokenService tokens;
    private final JdbcTemplate jdbc;
    private final UsuarioRepository usuarios;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public McpOAuthController(McpTokenService tokens, JdbcTemplate jdbc,
            UsuarioRepository usuarios, Clock clock) {
        this.tokens = tokens; this.jdbc = jdbc; this.usuarios = usuarios; this.clock = clock;
    }

    @GetMapping({"/.well-known/oauth-protected-resource/mcp", "/.well-known/oauth-protected-resource"})
    public Map<String, Object> recurso() {
        return Map.of("resource", tokens.audience(), "authorization_servers", List.of(tokens.issuer()),
                "bearer_methods_supported", List.of("header"),
                "scopes_supported", List.of("mcp:read", "mcp:write"));
    }

    @GetMapping("/.well-known/oauth-authorization-server")
    public Map<String, Object> metadados() {
        return Map.ofEntries(Map.entry("issuer", tokens.issuer()),
                Map.entry("authorization_endpoint", tokens.issuer() + "/oauth/authorize"),
                Map.entry("token_endpoint", tokens.issuer() + "/oauth/token"),
                Map.entry("registration_endpoint", tokens.issuer() + "/oauth/register"),
                Map.entry("jwks_uri", tokens.issuer() + "/oauth/jwks"),
                Map.entry("response_types_supported", List.of("code")),
                Map.entry("grant_types_supported", List.of("authorization_code", "refresh_token")),
                Map.entry("code_challenge_methods_supported", List.of("S256")),
                Map.entry("scopes_supported", List.of("mcp:read", "mcp:write")),
                Map.entry("token_endpoint_auth_methods_supported", List.of("none")));
    }

    @GetMapping("/oauth/jwks")
    public Map<String, Object> jwks() { return tokens.jwks(); }

    @PostMapping("/oauth/register")
    @Transactional
    public ResponseEntity<Map<String, Object>> registrar(@RequestBody Map<String, Object> metadata) {
        if (metadata == null) throw new IllegalArgumentException("registro OAuth invalido");
        Object uris = metadata.get("redirect_uris");
        if (!(uris instanceof List<?> lista) || lista.isEmpty() || lista.size() > 5
                || lista.stream().anyMatch(u -> !(u instanceof String s) || !loopback(s)))
            throw new IllegalArgumentException("redirect_uris invalidas");
        List<String> redirectUris = lista.stream().map(String.class::cast).toList();
        Object clientName = metadata.get("client_name");
        if (clientName != null && !(clientName instanceof String))
            throw new IllegalArgumentException("client_name invalido");
        String nome = clientName == null ? "Cliente MCP" : ((String) clientName).trim();
        if (nome.isBlank() || nome.length() > 120) throw new IllegalArgumentException("client_name invalido");
        String id = "em-" + UUID.randomUUID();
        jdbc.update("insert into mcp_cliente_oauth(id,nome,redirect_uris) values (?,?,?)",
                id, nome, String.join("\n", redirectUris));
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(Map.of(
                "client_id", id, "client_name", nome, "redirect_uris", redirectUris,
                "token_endpoint_auth_method", "none", "grant_types", List.of("authorization_code", "refresh_token"),
                "response_types", List.of("code")));
    }

    @GetMapping(value = "/oauth/authorize", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> autorizar(@RequestParam Map<String, String> parametros,
            HttpServletRequest request, CsrfToken csrf) {
        Autorizacao a = from(parametros, null);
        String nome = validarPedido(a);
        UsuarioPrincipal usuario = principalOuNulo();
        if (usuario == null) {
            String retorno = request.getRequestURI() + "?" + request.getQueryString();
            return ResponseEntity.status(302).location(URI.create("/entrar?retorno=" + encode(retorno))).build();
        }
        exigirClienteAtivo(usuario);
        String permissoes = "";
        if (escopos(a.scope()).contains("mcp:read"))
            permissoes += "<li><span class=\"permission-icon\" aria-hidden=\"true\">✓</span> Consultar seus agendamentos e lista de espera</li>";
        if (escopos(a.scope()).contains("mcp:write"))
            permissoes += "<li><span class=\"permission-icon\" aria-hidden=\"true\">✓</span> Criar, cancelar e reagendar reservas quando você confirmar</li>";
        String html = """
                <!doctype html>
                <html lang="pt-BR">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Autorizar acesso · Estilo Marcado</title>
                  <link rel="stylesheet" href="/oauth/consent.css">
                </head>
                <body>
                  <header class="site-header"><div class="site-header-inner"><span class="brand-mark">EM</span><span class="brand">Estilo Marcado</span></div></header>
                  <main class="page">
                    <section class="consent-card" aria-labelledby="title">
                      <p class="eyebrow">Acesso à sua conta</p>
                      <h1 id="title">Autorizar aplicativo</h1>
                      <p class="intro"><strong>%s</strong> quer acessar sua conta do Estilo Marcado.</p>
                      <div class="account"><span class="account-label">Você está conectado como</span><strong>%s</strong></div>
                      <h2>O aplicativo poderá</h2>
                      <ul class="permissions">%s</ul>
                      <p class="notice">Você poderá revogar o acesso depois. A autorização não confirma nenhuma reserva por si só.</p>
                      <form method="post" action="/oauth/authorize">
                %s
                        <input type="hidden" name="_csrf" value="%s">
                        <div class="actions">
                          <button class="button-primary" type="submit" name="decisao" value="permitir">Autorizar acesso</button>
                          <button class="button-secondary" type="submit" name="decisao" value="recusar">Recusar</button>
                        </div>
                      </form>
                    </section>
                    <p class="footer-note">Confira o nome do aplicativo antes de autorizar.</p>
                  </main>
                </body>
                </html>
                """.formatted(esc(nome), esc(usuario.nome()), permissoes, campos(a), esc(csrf.getToken()));
        URI callback = URI.create(a.redirect_uri());
        String callbackOrigin = callback.getScheme() + "://" + callback.getRawAuthority();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Content-Security-Policy", "default-src 'none'; style-src 'self' https://fonts.googleapis.com; "
                        + "font-src https://fonts.gstatic.com; form-action 'self' " + callbackOrigin
                        + "; base-uri 'none'")
                .body(html);
    }

    @PostMapping(value = "/oauth/authorize", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Transactional
    public ResponseEntity<Void> consentir(@RequestParam Map<String, String> parametros) {
        Autorizacao a = from(parametros, parametros.get("decisao"));
        validarPedido(a);
        UsuarioPrincipal usuario = principalOuNulo();
        if (usuario == null) throw new AccessDeniedException("sessao necessaria");
        exigirClienteAtivo(usuario);
        if (!"permitir".equals(a.decisao()))
            return ResponseEntity.status(302).location(URI.create(a.redirect_uri() + separador(a.redirect_uri())
                    + "error=access_denied&state=" + encode(a.state()))).build();
        jdbc.update("""
                insert into mcp_autorizacao(usuario_id,cliente_id,versao) values (?,?,1)
                on conflict (usuario_id,cliente_id) do update set
                    versao=mcp_autorizacao.versao+case when mcp_autorizacao.revogado_em is null then 0 else 1 end,
                    revogado_em=null
                """, usuario.id(), a.client_id());
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String codigo = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("""
                insert into mcp_codigo_oauth(codigo_hash,usuario_id,cliente_id,redirect_uri,
                    code_challenge,escopos,expira_em) values (?,?,?,?,?,?,clock_timestamp()+interval '2 minutes')
                """, sha256(codigo), usuario.id(), a.client_id(), a.redirect_uri(),
                a.code_challenge(), escopos(a.scope()));
        return ResponseEntity.status(302).location(URI.create(a.redirect_uri() + separador(a.redirect_uri())
                + "code=" + encode(codigo) + "&state=" + encode(a.state()))).build();
    }

    @PostMapping(value = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Transactional
    public ResponseEntity<Map<String, Object>> token(@RequestParam Map<String, String> parametros) {
        if (parametros.containsKey("resource") && !tokens.audience().equals(parametros.get("resource")))
            throw new InvalidGrant();
        if ("refresh_token".equals(parametros.get("grant_type")))
            return renovar(parametros);
        if (!"authorization_code".equals(parametros.get("grant_type")))
            throw new IllegalArgumentException("grant_type invalido");
        String codigo = parametros.get("code"), verifier = parametros.get("code_verifier");
        if (codigo == null || verifier == null || !verifier.matches("[A-Za-z0-9._~-]{43,128}"))
            throw new InvalidGrant();
        List<Map<String, Object>> itens = jdbc.queryForList("""
                select * from mcp_codigo_oauth where codigo_hash=? for update
                """, sha256(codigo));
        if (itens.isEmpty()) throw new InvalidGrant();
        Map<String, Object> item = itens.getFirst();
        if (item.get("consumido_em") != null
                || !((java.sql.Timestamp) item.get("expira_em")).toInstant().isAfter(clock.instant())
                || !item.get("cliente_id").equals(parametros.get("client_id"))
                || !item.get("redirect_uri").equals(parametros.get("redirect_uri"))
                || !item.get("code_challenge").equals(pkce(verifier)))
            throw new InvalidGrant();
        jdbc.update("update mcp_codigo_oauth set consumido_em=clock_timestamp() where codigo_hash=?", sha256(codigo));
        String escopos = (String) item.get("escopos");
        return emitirPar(((Number) item.get("usuario_id")).longValue(),
                (String) item.get("cliente_id"), escopos);
    }

    private ResponseEntity<Map<String, Object>> renovar(Map<String, String> parametros) {
        String bruto = parametros.get("refresh_token");
        String clienteId = parametros.get("client_id");
        if (bruto == null || !bruto.matches("[A-Za-z0-9_-]{43}")
                || clienteId == null || clienteId.isBlank()) throw new InvalidGrant();
        List<Map<String, Object>> itens = jdbc.queryForList("""
                select r.usuario_id,r.cliente_id,r.escopos,r.autorizacao_versao,r.expira_em,r.consumido_em,
                       a.versao as versao_atual,a.revogado_em
                from mcp_refresh_token r
                left join mcp_autorizacao a on a.usuario_id=r.usuario_id and a.cliente_id=r.cliente_id
                where r.token_hash=? for update of r
                """, sha256(bruto));
        if (itens.isEmpty()) throw new InvalidGrant();
        Map<String, Object> item = itens.getFirst();
        if (!clienteId.equals(item.get("cliente_id")) || item.get("consumido_em") != null
                || !((java.sql.Timestamp) item.get("expira_em")).toInstant().isAfter(clock.instant())
                || item.get("revogado_em") != null || item.get("versao_atual") == null
                || !item.get("autorizacao_versao").equals(item.get("versao_atual")))
            throw new InvalidGrant();
        Long usuarioId = ((Number) item.get("usuario_id")).longValue();
        String escopos = (String) item.get("escopos");
        try {
            String jwt = tokens.emitir(usuarioId, clienteId, escopos);
            jdbc.update("update mcp_refresh_token set consumido_em=clock_timestamp() where token_hash=?",
                    sha256(bruto));
            return respostaTokens(jwt, criarRefreshToken(usuarioId, clienteId, escopos,
                    ((Number) item.get("autorizacao_versao")).intValue()), escopos);
        } catch (org.springframework.security.authentication.BadCredentialsException ex) {
            throw new InvalidGrant();
        }
    }

    private ResponseEntity<Map<String, Object>> emitirPar(Long usuarioId, String clienteId, String escopos) {
        String jwt = tokens.emitir(usuarioId, clienteId, escopos);
        Integer versao = jdbc.queryForObject("""
                select versao from mcp_autorizacao where usuario_id=? and cliente_id=? and revogado_em is null
                """, Integer.class, usuarioId, clienteId);
        return respostaTokens(jwt, criarRefreshToken(usuarioId, clienteId, escopos, versao), escopos);
    }

    private String criarRefreshToken(Long usuarioId, String clienteId, String escopos, int versao) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String bruto = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("""
                insert into mcp_refresh_token(token_hash,usuario_id,cliente_id,escopos,autorizacao_versao,expira_em)
                values (?,?,?,?,?,clock_timestamp()+interval '30 days')
                """, sha256(bruto), usuarioId, clienteId, escopos, versao);
        return bruto;
    }

    private ResponseEntity<Map<String, Object>> respostaTokens(String jwt, String refresh, String escopos) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
                "access_token", jwt, "refresh_token", refresh, "token_type", "Bearer",
                "expires_in", 300, "scope", escopos));
    }

    private static final class InvalidGrant extends RuntimeException {}

    @ExceptionHandler(InvalidGrant.class)
    public ResponseEntity<Map<String, Object>> invalidGrant() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(Map.of("error", "invalid_grant"));
    }

    @PostMapping("/oauth/revoke")
    @Transactional
    public ResponseEntity<Void> revogar(@RequestParam String client_id) {
        UsuarioPrincipal usuario = principalOuNulo();
        if (usuario == null) throw new AccessDeniedException("sessao necessaria");
        jdbc.update("update mcp_autorizacao set versao=versao+1,revogado_em=clock_timestamp() where usuario_id=? and cliente_id=?",
                usuario.id(), client_id);
        jdbc.update("delete from mcp_refresh_token where usuario_id=? and cliente_id=?", usuario.id(), client_id);
        return ResponseEntity.noContent().build();
    }

    private Autorizacao from(Map<String, String> p, String decisao) {
        return new Autorizacao(p.get("response_type"), p.get("client_id"), p.get("redirect_uri"),
                p.get("code_challenge"), p.get("code_challenge_method"), p.get("scope"),
                p.get("state"), p.get("resource"), decisao);
    }

    private String validarPedido(Autorizacao a) {
        if (!"code".equals(a.response_type()) || !"S256".equals(a.code_challenge_method())
                || a.code_challenge() == null || !a.code_challenge().matches("[A-Za-z0-9_-]{43}")
                || a.state() == null || a.state().isBlank() || a.state().length() > 256
                || !tokens.audience().equals(a.resource()) || a.client_id() == null
                || !loopback(a.redirect_uri())) throw new IllegalArgumentException("pedido OAuth invalido");
        escopos(a.scope());
        List<Map<String, Object>> clientes = jdbc.queryForList(
                "select nome,redirect_uris from mcp_cliente_oauth where id=?", a.client_id());
        if (clientes.isEmpty() || !Arrays.asList(((String) clientes.getFirst().get("redirect_uris")).split("\\n"))
                .contains(a.redirect_uri())) throw new IllegalArgumentException("cliente OAuth invalido");
        return (String) clientes.getFirst().get("nome");
    }

    private String escopos(String value) {
        String texto = value == null || value.isBlank() ? "mcp:read mcp:write" : value.trim();
        for (String escopo : texto.split(" +"))
            if (!escopo.equals("mcp:read") && !escopo.equals("mcp:write"))
                throw new IllegalArgumentException("escopo invalido");
        return texto;
    }

    private boolean loopback(String value) {
        try {
            URI uri = URI.create(value);
            return "http".equals(uri.getScheme()) && ("127.0.0.1".equals(uri.getHost())
                    || "localhost".equals(uri.getHost()) || "[::1]".equals(uri.getHost())
                    || "::1".equals(uri.getHost()))
                    && uri.getPort() > 0 && uri.getUserInfo() == null && uri.getFragment() == null;
        } catch (Exception ex) { return false; }
    }

    private UsuarioPrincipal principalOuNulo() {
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();
        return autenticacao != null && autenticacao.getPrincipal() instanceof UsuarioPrincipal p ? p : null;
    }
    private void exigirClienteAtivo(UsuarioPrincipal principal) {
        var usuario = usuarios.findForSessionById(principal.id()).orElseThrow();
        if (usuario.getEstado() != EstadoConta.ATIVA || usuario.getPerfil() != PerfilUsuario.CLIENTE)
            throw new AccessDeniedException("acesso negado");
    }
    private String campos(Autorizacao a) {
        Map<String, String> valores = Map.of("response_type", a.response_type(), "client_id", a.client_id(),
                "redirect_uri", a.redirect_uri(), "code_challenge", a.code_challenge(),
                "code_challenge_method", a.code_challenge_method(), "scope", escopos(a.scope()),
                "state", a.state(), "resource", a.resource());
        StringBuilder html = new StringBuilder();
        valores.forEach((k, v) -> html.append("<input type=\"hidden\" name=\"").append(k)
                .append("\" value=\"").append(esc(v)).append("\">"));
        return html.toString();
    }
    private String esc(String texto) {
        return texto == null ? "" : texto.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
    private String encode(String texto) { return URLEncoder.encode(texto, StandardCharsets.UTF_8); }
    private String separador(String uri) { return uri.contains("?") ? "&" : "?"; }
    private String sha256(String texto) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(texto.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private String pkce(String verifier) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
