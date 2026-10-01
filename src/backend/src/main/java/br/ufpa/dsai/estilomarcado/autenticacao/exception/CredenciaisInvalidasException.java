package br.ufpa.dsai.estilomarcado.autenticacao.exception;

public class CredenciaisInvalidasException extends RuntimeException {
    public CredenciaisInvalidasException() {
        super("e-mail ou senha invalidos");
    }
}
