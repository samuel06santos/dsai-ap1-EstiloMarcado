package br.ufpa.dsai.estilomarcado.catalogo.api.exception;

/**
 * Indica que um recurso nao foi encontrado.
 */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
