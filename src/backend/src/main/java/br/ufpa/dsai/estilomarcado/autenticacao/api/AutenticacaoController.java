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
import br.ufpa.dsai.estilomarcado.autenticacao.api.dto.LoginRequest;
import br.ufpa.dsai.estilomarcado.autenticacao.api.dto.MensagemResponse;
import br.ufpa.dsai.estilomarcado.autenticacao.api.dto.NovaSenhaRequest;
import br.ufpa.dsai.estilomarcado.autenticacao.api.dto.SessaoResponse;
import br.ufpa.dsai.estilomarcado.autenticacao.api.dto.TokenRequest;
import br.ufpa.dsai.estilomarcado.autenticacao.exception.CredenciaisInvalidasException;
import br.ufpa.dsai.estilomarcado.autenticacao.security.SessaoAbsolutaFilter;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioAtual;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioPrincipal;
import br.ufpa.dsai.estilomarcado.autenticacao.service.AutenticacaoService;
import br.ufpa.dsai.estilomarcado.autenticacao.service.LimiteRequisicoesService;
import br.ufpa.dsai.estilomarcado.autenticacao.service.NormalizadorEmail;
import br.ufpa.dsai.estilomarcado.autenticacao.service.AuditoriaService;
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

    public AutenticacaoController(AutenticacaoService service,
                                  LimiteRequisicoesService limiteService,
                                  SecurityContextRepository contextRepository,
                                  UsuarioAtual usuarioAtual,
                                  AuditoriaService auditoria) {
        this.service = service;
        this.limiteService = limiteService;
        this.contextRepository = contextRepository;
        this.usuarioAtual = usuarioAtual;
        this.auditoria = auditoria;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
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
        contextRepository.saveContext(contexto, servletRequest, servletResponse);
        return SessaoResponse.from(principal);
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
