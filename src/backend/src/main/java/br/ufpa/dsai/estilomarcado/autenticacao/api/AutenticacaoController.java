package br.ufpa.dsai.estilomarcado.autenticacao.api;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.ufpa.dsai.estilomarcado.autenticacao.api.dto.CadastroRequest;
import br.ufpa.dsai.estilomarcado.autenticacao.api.dto.EmailRequest;
import br.ufpa.dsai.estilomarcado.autenticacao.api.dto.FirebaseTokenRequest;
import br.ufpa.dsai.estilomarcado.autenticacao.api.dto.LoginRequest;
import br.ufpa.dsai.estilomarcado.autenticacao.api.dto.MensagemResponse;
import br.ufpa.dsai.estilomarcado.autenticacao.api.dto.NovaSenhaRequest;
import br.ufpa.dsai.estilomarcado.autenticacao.api.dto.SessaoResponse;
import br.ufpa.dsai.estilomarcado.autenticacao.api.dto.TokenRequest;
import br.ufpa.dsai.estilomarcado.autenticacao.exception.CredenciaisInvalidasException;
import br.ufpa.dsai.estilomarcado.autenticacao.security.SessaoAbsolutaFilter;
import br.ufpa.dsai.estilomarcado.autenticacao.security.FirebaseSessionValidationFilter;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioAtual;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioPrincipal;
import br.ufpa.dsai.estilomarcado.autenticacao.service.AutenticacaoService;
import br.ufpa.dsai.estilomarcado.autenticacao.service.LimiteRequisicoesService;
import br.ufpa.dsai.estilomarcado.autenticacao.service.NormalizadorEmail;
import br.ufpa.dsai.estilomarcado.autenticacao.service.AuditoriaService;
import br.ufpa.dsai.estilomarcado.autenticacao.service.FirebaseIdentityService;
import br.ufpa.dsai.estilomarcado.autenticacao.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import com.google.firebase.auth.FirebaseAuthException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/autenticacao")
public class AutenticacaoController {

    private static final String MENSAGEM_CADASTRO =
            "Se o e-mail estiver disponivel, enviaremos as instrucoes de ativacao.";
    private static final String MENSAGEM_RECUPERACAO =
            "Se existir uma conta apta para este e-mail, enviaremos as instrucoes de recuperacao.";

    private final AutenticacaoService service;
    private final LimiteRequisicoesService limiteService;
    private final SecurityContextRepository contextRepository;
    private final UsuarioAtual usuarioAtual;
    private final AuditoriaService auditoria;
    private final FirebaseIdentityService firebase;
    private final UsuarioRepository users;
    private final String projectId;
    private final String webApiKey;
    private final String authDomain;
    private final String appId;
    private final String emulatorBrowserUrl;

    public AutenticacaoController(AutenticacaoService service,
                                  LimiteRequisicoesService limiteService,
                                  SecurityContextRepository contextRepository,
                                  UsuarioAtual usuarioAtual,
                                  AuditoriaService auditoria,
                                  FirebaseIdentityService firebase,
                                  UsuarioRepository users,
                                  @Value("${app.auth.firebase.project-id:}") String projectId,
                                  @Value("${app.auth.firebase.web-api-key:}") String webApiKey,
                                  @Value("${app.auth.firebase.web-auth-domain:}") String authDomain,
                                  @Value("${app.auth.firebase.web-app-id:}") String appId,
                                  @Value("${app.auth.firebase.emulator-browser-url:}") String emulatorBrowserUrl) {
        this.service = service;
        this.limiteService = limiteService;
        this.contextRepository = contextRepository;
        this.usuarioAtual = usuarioAtual;
        this.auditoria = auditoria;
        this.firebase = firebase;
        this.users = users;
        this.projectId = projectId;
        this.webApiKey = webApiKey;
        this.authDomain = authDomain;
        this.appId = appId;
        this.emulatorBrowserUrl = emulatorBrowserUrl;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @GetMapping("/firebase/config")
    public Map<String, Object> firebaseConfig() {
        if (!firebase.enabled()) return Map.of("enabled", false);
        return Map.of("enabled", true, "apiKey", webApiKey, "authDomain", authDomain,
                "projectId", projectId, "appId", appId, "emulatorUrl", emulatorBrowserUrl);
    }

    @PostMapping("/cadastros")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MensagemResponse cadastrar(@Valid @RequestBody CadastroRequest request,
                                      HttpServletRequest servletRequest) {
        String origem = origem(servletRequest);
        limiteService.verificarEnvio("cadastro", origem, NormalizadorEmail.normalizar(request.email()));
        service.cadastrarCliente(request, origem);
        return new MensagemResponse(MENSAGEM_CADASTRO);
    }

    @PostMapping("/ativacoes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ativar(@Valid @RequestBody TokenRequest request, HttpServletRequest servletRequest) {
        service.ativar(request.token(), origem(servletRequest));
    }

    @PostMapping("/ativacoes/reenviar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MensagemResponse reenviar(@Valid @RequestBody EmailRequest request,
                                     HttpServletRequest servletRequest) {
        String origem = origem(servletRequest);
        limiteService.verificarEnvio("ativacao", origem, NormalizadorEmail.normalizar(request.email()));
        service.reenviarAtivacao(request.email(), origem);
        return new MensagemResponse(MENSAGEM_CADASTRO);
    }

    @PostMapping("/sessoes")
    public SessaoResponse login(@Valid @RequestBody LoginRequest request,
                                HttpServletRequest servletRequest,
                                HttpServletResponse servletResponse) {
        String origem = origem(servletRequest);
        limiteService.verificarLogin(origem);
        var resultado = service.autenticar(request.email(), request.senha(), origem);
        if (!resultado.sucesso()) {
            throw new CredenciaisInvalidasException();
        }

        return criarSessao(resultado, servletRequest, servletResponse);
    }

    @PostMapping("/sessoes/firebase")
    public SessaoResponse loginFirebase(@Valid @RequestBody FirebaseTokenRequest request,
                                        HttpServletRequest servletRequest,
                                        HttpServletResponse servletResponse) {
        String origem = origem(servletRequest);
        limiteService.verificarLogin(origem);
        var resultado = service.autenticarFirebase(request.idToken(), origem);
        if (!resultado.sucesso()) throw new CredenciaisInvalidasException();
        return criarSessao(resultado, servletRequest, servletResponse);
    }

    private SessaoResponse criarSessao(AutenticacaoService.ResultadoLogin resultado,
                                      HttpServletRequest servletRequest,
                                      HttpServletResponse servletResponse) {
        UsuarioPrincipal principal = resultado.principal();
        var autenticacao = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticacao);
        SecurityContextHolder.setContext(contexto);

        HttpSession sessao = servletRequest.getSession(false);
        if (sessao == null) {
            sessao = servletRequest.getSession(true);
        } else {
            servletRequest.changeSessionId();
        }
        sessao.setMaxInactiveInterval(30 * 60);
        sessao.setAttribute(SessaoAbsolutaFilter.AUTENTICADO_EM, Instant.now());
        if (resultado.firebaseUid() != null) {
            sessao.setAttribute(FirebaseSessionValidationFilter.UID, resultado.firebaseUid());
            sessao.setAttribute(FirebaseSessionValidationFilter.AUTH_TIME, resultado.authTimeMillis());
            sessao.setAttribute(FirebaseSessionValidationFilter.CHECKED_AT, Instant.now());
        }
        contextRepository.saveContext(contexto, servletRequest, servletResponse);
        return SessaoResponse.from(principal);
    }

    @PostMapping("/contas/google")
    public Map<String, Boolean> confirmarGoogle(@Valid @RequestBody FirebaseTokenRequest request,
                                                  HttpServletRequest servletRequest) {
        var principal = usuarioAtual.get();
        var session = servletRequest.getSession(false);
        String uid = session == null ? null : (String) session.getAttribute(FirebaseSessionValidationFilter.UID);
        if (!firebase.enabled() || uid == null) throw new CredenciaisInvalidasException();
        try {
            var token = firebase.verify(request.idToken());
            if (!uid.equals(token.getUid()) ||
                    firebase.authTimeMillis(token) < Instant.now().minusSeconds(300).toEpochMilli()) {
                throw new CredenciaisInvalidasException();
            }
            var remote = firebase.getUser(uid);
            boolean linked = java.util.Arrays.stream(remote.getProviderData())
                    .anyMatch(provider -> "google.com".equals(provider.getProviderId()));
            if (!linked) throw new CredenciaisInvalidasException();
            auditoria.registrar(principal.id(), "GOOGLE_VINCULADO", true, origem(servletRequest), null);
            return Map.of("google", true);
        } catch (FirebaseAuthException ex) { throw new CredenciaisInvalidasException(); }
    }

    @GetMapping("/contas/metodos")
    public Map<String, Object> metodos() {
        var principal = usuarioAtual.get();
        var local = users.findById(principal.id()).orElseThrow(CredenciaisInvalidasException::new);
        if (!firebase.enabled() || local.getFirebaseUid() == null) {
            return Map.of("firebaseUid", "", "google", false, "emailSenha", true);
        }
        try {
            var remote = firebase.getUser(local.getFirebaseUid());
            boolean google = java.util.Arrays.stream(remote.getProviderData())
                    .anyMatch(provider -> "google.com".equals(provider.getProviderId()));
            return Map.of("firebaseUid", local.getFirebaseUid(), "google", google,
                    "emailSenha", local.isSenhaFirebase());
        } catch (FirebaseAuthException ex) { throw new CredenciaisInvalidasException(); }
    }

    @GetMapping("/sessao")
    public SessaoResponse sessao() {
        return SessaoResponse.from(usuarioAtual.get());
    }

    @DeleteMapping("/sessao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UsuarioPrincipal principal) {
            auditoria.registrar(principal.id(), "LOGOUT", true, origem(request), null);
        }
        HttpSession sessao = request.getSession(false);
        if (sessao != null) {
            sessao.invalidate();
        }
        SecurityContextHolder.clearContext();
        Cookie cookie = new Cookie("SESSION", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    @PostMapping("/recuperacoes")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MensagemResponse recuperar(@Valid @RequestBody EmailRequest request,
                                      HttpServletRequest servletRequest) {
        String origem = origem(servletRequest);
        limiteService.verificarEnvio("recuperacao", origem, NormalizadorEmail.normalizar(request.email()));
        service.solicitarRecuperacao(request.email(), origem);
        return new MensagemResponse(MENSAGEM_RECUPERACAO);
    }

    @PostMapping("/redefinicoes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redefinir(@Valid @RequestBody NovaSenhaRequest request, HttpServletRequest servletRequest) {
        service.redefinirSenha(request, origem(servletRequest));
    }

    @PostMapping("/convites")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void aceitarConvite(@Valid @RequestBody NovaSenhaRequest request, HttpServletRequest servletRequest) {
        service.aceitarConvite(request, origem(servletRequest));
    }

    private String origem(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
