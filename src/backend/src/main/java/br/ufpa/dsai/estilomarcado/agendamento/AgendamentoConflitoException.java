package br.ufpa.dsai.estilomarcado.agendamento;

public class AgendamentoConflitoException extends RuntimeException {
    private final String codigo;

    public AgendamentoConflitoException(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public String getCodigo() { return codigo; }
}
