package br.ufpa.dsai.estilomarcado.autenticacao.security;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class SessaoAbsolutaFilter extends OncePerRequestFilter {

    public static final String AUTENTICADO_EM = "AUTENTICADO_EM";
    private static final Duration DURACAO_MAXIMA = Duration.ofHours(12);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpSession sessao = request.getSession(false);
        if (sessao != null && sessao.getAttribute(AUTENTICADO_EM) instanceof Instant inicio
                && inicio.plus(DURACAO_MAXIMA).isBefore(Instant.now())) {
            sessao.invalidate();
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }
}
