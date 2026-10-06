package br.ufpa.dsai.estilomarcado.estabelecimento.service;

public class ConsultaFiliaisInvalidaException extends RuntimeException {
    private final String campo;

    public ConsultaFiliaisInvalidaException(String campo, String mensagem) {
        super(mensagem);
        this.campo = campo;
    }

    public String getCampo() { return campo; }
}
