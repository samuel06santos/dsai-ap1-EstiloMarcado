package br.ufpa.dsai.estilomarcado.autenticacao.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class UsuarioAtual {

    public UsuarioPrincipal get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UsuarioPrincipal principal)) {
            throw new AuthenticationCredentialsNotFoundException("autenticacao necessaria");
        }
        return principal;
    }

    public void exigirAdministradorDaUnidade(Long unidadeId) {
        UsuarioPrincipal principal = get();
        if (!principal.perfil().name().equals("ADMINISTRADOR") || !unidadeId.equals(principal.unidadeId())) {
            throw new AccessDeniedException("acesso negado");
        }
    }
}
