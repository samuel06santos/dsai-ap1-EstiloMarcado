package br.ufpa.dsai.estilomarcado.autenticacao.security;

import java.io.IOException;
import java.time.Instant;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

final class RespostaSeguranca {
    private RespostaSeguranca() {
    }

    static void escrever(ObjectMapper mapper, HttpServletResponse response, int status, String mensagem)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        mapper.writeValue(response.getOutputStream(), new Erro(status, mensagem, Instant.now()));
    }

    private record Erro(int status, String mensagem, Instant timestamp) {
    }
}
