package br.ufpa.dsai.estilomarcado.autenticacao.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.DefaultCsrfToken;

class SpaCsrfTokenRequestHandlerTest {
    @Test
    void aceitaTokenBrutoDoCookieNoCabecalhoDoAngular() {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var token = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "token-bruto");
        var handler = new SpaCsrfTokenRequestHandler();
        handler.handle(request, response, () -> token);
        request.addHeader("X-XSRF-TOKEN", "token-bruto");

        assertEquals("token-bruto", handler.resolveCsrfTokenValue(request, token));
    }
}
