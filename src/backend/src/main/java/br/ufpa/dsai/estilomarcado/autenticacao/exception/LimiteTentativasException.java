package br.ufpa.dsai.estilomarcado.autenticacao.exception;

public class LimiteTentativasException extends RuntimeException {
    public LimiteTentativasException() {
        super("muitas solicitacoes; tente novamente mais tarde");
    }
}
