package br.ufpa.dsai.estilomarcado.autenticacao.security;

import java.util.function.Supplier;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Mantem o token mascarado nas respostas, mas aceita o token bruto enviado
 * pelo interceptor XSRF do Angular a partir do cookie XSRF-TOKEN.
 */
final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler tokenBruto = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler tokenMascarado = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       Supplier<CsrfToken> csrfToken) {
        tokenMascarado.handle(request, response, csrfToken);
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String cabecalho = request.getHeader(csrfToken.getHeaderName());
        return cabecalho != null && !cabecalho.isBlank()
                ? tokenBruto.resolveCsrfTokenValue(request, csrfToken)
                : tokenMascarado.resolveCsrfTokenValue(request, csrfToken);
    }
}
