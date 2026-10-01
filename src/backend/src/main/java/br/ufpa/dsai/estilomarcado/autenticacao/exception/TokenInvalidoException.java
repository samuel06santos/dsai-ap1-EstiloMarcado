package br.ufpa.dsai.estilomarcado.autenticacao.exception;

public class TokenInvalidoException extends RuntimeException {
    public TokenInvalidoException() {
        super("token invalido, expirado ou ja utilizado");
    }
}
