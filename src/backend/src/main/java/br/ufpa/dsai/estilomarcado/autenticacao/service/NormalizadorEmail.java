package br.ufpa.dsai.estilomarcado.autenticacao.service;

import java.util.Locale;

public final class NormalizadorEmail {
    private NormalizadorEmail() {
    }

    public static String normalizar(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
