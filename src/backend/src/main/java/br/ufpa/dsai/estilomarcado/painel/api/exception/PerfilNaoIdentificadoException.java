package br.ufpa.dsai.estilomarcado.painel.api.exception;

/**
 * Indica que a requisicao ao painel nao trouxe a identificacao do profissional.
 */
public class PerfilNaoIdentificadoException extends RuntimeException {

    public PerfilNaoIdentificadoException(String mensagem) {
        super(mensagem);
    }
}
