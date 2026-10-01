package br.ufpa.dsai.estilomarcado.autenticacao.api;

import java.util.Map;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.ufpa.dsai.estilomarcado.autenticacao.api.dto.AtualizarPerfilRequest;
import br.ufpa.dsai.estilomarcado.autenticacao.api.dto.MeuPerfilResponse;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioAtual;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioPrincipal;
import br.ufpa.dsai.estilomarcado.autenticacao.service.PerfilService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/usuarios/me")
public class PerfilController {

    private final PerfilService service;
    private final UsuarioAtual usuarioAtual;
    private final SecurityContextRepository contextRepository;

    public PerfilController(PerfilService service, UsuarioAtual usuarioAtual,
                            SecurityContextRepository contextRepository) {
        this.service = service;
        this.usuarioAtual = usuarioAtual;
        this.contextRepository = contextRepository;
    }

    @GetMapping
    public MeuPerfilResponse consultar() {
        return service.consultar();
    }

    @PatchMapping
    public MeuPerfilResponse atualizar(@RequestBody Map<String, Object> campos,
                                      HttpServletRequest request, HttpServletResponse response) {
        UsuarioPrincipal atual = usuarioAtual.get();
        MeuPerfilResponse perfil = service.atualizar(AtualizarPerfilRequest.from(campos));
        UsuarioPrincipal atualizado = new UsuarioPrincipal(atual.id(), perfil.nome(), atual.email(),
                atual.perfil(), atual.unidadeId(), atual.profissionalId());
        var autenticacao = UsernamePasswordAuthenticationToken.authenticated(
                atualizado, null, atualizado.getAuthorities());
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticacao);
        SecurityContextHolder.setContext(contexto);
        contextRepository.saveContext(contexto, request, response);
        return perfil;
    }
}
