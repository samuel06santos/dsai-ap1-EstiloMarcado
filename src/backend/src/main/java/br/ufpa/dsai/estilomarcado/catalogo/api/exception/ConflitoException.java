package br.ufpa.dsai.estilomarcado.catalogo.api.exception;

/**
 * Indica um conflito de estado, como nome de servico duplicado em uma unidade.
 */
public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
