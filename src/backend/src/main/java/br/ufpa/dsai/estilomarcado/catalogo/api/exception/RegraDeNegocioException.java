package br.ufpa.dsai.estilomarcado.catalogo.api.exception;

/**
 * Indica violacao de uma regra de negocio do dominio.
 */
public class RegraDeNegocioException extends RuntimeException {

    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
