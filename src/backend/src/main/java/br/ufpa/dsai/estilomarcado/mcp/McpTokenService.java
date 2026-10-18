package br.ufpa.dsai.estilomarcado.mcp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import br.ufpa.dsai.estilomarcado.autenticacao.model.EstadoConta;
import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;
import br.ufpa.dsai.estilomarcado.autenticacao.repository.UsuarioRepository;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioPrincipal;
import tools.jackson.databind.ObjectMapper;

@Service
public class McpTokenService {
    public record Identidade(UsuarioPrincipal principal, String clienteId, String escopos) {
        public void exigir(String escopo) {
            if (!java.util.Arrays.asList(escopos.split(" ")).contains(escopo))
                throw new AccessDeniedException("escopo MCP insuficiente");
        }
    }

    private final RSAPrivateCrtKey privateKey;
    private final RSAPublicKey publicKey;
    private final String kid;
    private final String issuer;
    private final String audience;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final UsuarioRepository usuarios;
    private final Clock clock;

    public McpTokenService(@Value("${app.mcp.private-key-path:}") String privateKeyPath,
            @Value("${app.mcp.issuer:http://localhost:8080}") String issuer,
            @Value("${app.mcp.audience:http://localhost:4200/mcp}") String audience,
            ObjectMapper mapper, JdbcTemplate jdbc, UsuarioRepository usuarios, Clock clock) {
        try {
            if (privateKeyPath.isBlank()) {
                KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
                gerador.initialize(2048);
                KeyPair par = gerador.generateKeyPair();
                privateKey = (RSAPrivateCrtKey) par.getPrivate();
                publicKey = (RSAPublicKey) par.getPublic();
            } else {
                String pem = Files.readString(Path.of(privateKeyPath));
                byte[] der = Base64.getMimeDecoder().decode(pem.replaceAll("-----[^-]+-----", "")
                        .replaceAll("\\s", ""));
                privateKey = (RSAPrivateCrtKey) KeyFactory.getInstance("RSA")
                        .generatePrivate(new PKCS8EncodedKeySpec(der));
                publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                        .generatePublic(new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
            }
            kid = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(publicKey.getEncoded())).substring(0, 16);
        } catch (Exception ex) { throw new IllegalStateException("chave MCP invalida", ex); }
        this.issuer = issuer;
        this.audience = audience;
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.usuarios = usuarios;
        this.clock = clock;
    }

    public String issuer() { return issuer; }
    public String audience() { return audience; }

    public Map<String, Object> jwks() {
        return Map.of("keys", java.util.List.of(Map.of("kty", "RSA", "kid", kid, "use", "sig",
                "alg", "RS256", "n", b64(unsigned(publicKey.getModulus().toByteArray())),
                "e", b64(unsigned(publicKey.getPublicExponent().toByteArray())))));
    }

    public String emitir(Long usuarioId, String clienteId, String escopos) {
        try {
            var usuario = usuarios.findForSessionById(usuarioId).orElseThrow();
            if (usuario.getEstado() != EstadoConta.ATIVA || usuario.getPerfil() != PerfilUsuario.CLIENTE)
                throw new BadCredentialsException("conta MCP sem acesso");
            Integer versao = jdbc.queryForObject("""
                    select versao from mcp_autorizacao where usuario_id=? and cliente_id=? and revogado_em is null
                    """, Integer.class, usuarioId, clienteId);
            if (versao == null) throw new BadCredentialsException("consentimento ausente");
            long agora = clock.instant().getEpochSecond();
            String cabecalho = b64(mapper.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT", "kid", kid)));
            String corpo = b64(mapper.writeValueAsBytes(Map.ofEntries(
                    Map.entry("iss", issuer), Map.entry("aud", audience),
                    Map.entry("sub", usuarioId.toString()), Map.entry("client_id", clienteId),
                    Map.entry("scope", escopos), Map.entry("av", versao),
                    Map.entry("iat", agora), Map.entry("nbf", agora),
                    Map.entry("exp", agora + 300), Map.entry("jti", UUID.randomUUID().toString()))));
            String mensagem = cabecalho + "." + corpo;
            Signature assinatura = Signature.getInstance("SHA256withRSA");
            assinatura.initSign(privateKey);
            assinatura.update(mensagem.getBytes(StandardCharsets.US_ASCII));
            return mensagem + "." + b64(assinatura.sign());
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw new BadCredentialsException("consentimento ausente");
        } catch (BadCredentialsException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalStateException("falha ao emitir token MCP", ex); }
    }

    @SuppressWarnings("unchecked")
    public Identidade validar(String authorization) {
        if (authorization == null || !authorization.matches("Bearer [A-Za-z0-9_.-]+"))
            throw new BadCredentialsException("Bearer MCP invalido");
        try {
            String[] partes = authorization.substring(7).split("\\.", -1);
            if (partes.length != 3) throw new IllegalArgumentException();
            Map<String, Object> cabecalho = mapper.readValue(Base64.getUrlDecoder().decode(partes[0]), Map.class);
            Map<String, Object> claims = mapper.readValue(Base64.getUrlDecoder().decode(partes[1]), Map.class);
            if (!"RS256".equals(cabecalho.get("alg")) || !kid.equals(cabecalho.get("kid")))
                throw new IllegalArgumentException();
            Signature assinatura = Signature.getInstance("SHA256withRSA");
            assinatura.initVerify(publicKey);
            assinatura.update((partes[0] + "." + partes[1]).getBytes(StandardCharsets.US_ASCII));
            if (!assinatura.verify(Base64.getUrlDecoder().decode(partes[2]))) throw new IllegalArgumentException();
            long agora = clock.instant().getEpochSecond();
            if (!issuer.equals(claims.get("iss")) || !audience.equals(claims.get("aud"))
                    || ((Number) claims.get("exp")).longValue() <= agora
                    || ((Number) claims.get("nbf")).longValue() > agora
                    || ((Number) claims.get("iat")).longValue() > agora
                    || ((Number) claims.get("exp")).longValue() - ((Number) claims.get("iat")).longValue() > 300)
                throw new IllegalArgumentException();
            Long usuarioId = Long.valueOf((String) claims.get("sub"));
            String clienteId = (String) claims.get("client_id");
            String escopos = (String) claims.get("scope");
            UUID jti = UUID.fromString((String) claims.get("jti"));
            Integer versao = ((Number) claims.get("av")).intValue();
            var usuario = usuarios.findForSessionById(usuarioId).orElseThrow();
            if (usuario.getEstado() != EstadoConta.ATIVA || usuario.getPerfil() != PerfilUsuario.CLIENTE)
                throw new IllegalArgumentException();
            Integer atual = jdbc.queryForObject("""
                    select versao from mcp_autorizacao where usuario_id=? and cliente_id=? and revogado_em is null
                    """, Integer.class, usuarioId, clienteId);
            if (!versao.equals(atual) || Boolean.TRUE.equals(jdbc.queryForObject(
                    "select exists(select 1 from mcp_token_revogado where jti=?)", Boolean.class, jti)))
                throw new IllegalArgumentException();
            return new Identidade(UsuarioPrincipal.from(usuario), clienteId, escopos);
        } catch (Exception ex) { throw new BadCredentialsException("Bearer MCP invalido", ex); }
    }

    private byte[] unsigned(byte[] bytes) {
        return bytes.length > 1 && bytes[0] == 0 ? java.util.Arrays.copyOfRange(bytes, 1, bytes.length) : bytes;
    }
    private String b64(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
}
