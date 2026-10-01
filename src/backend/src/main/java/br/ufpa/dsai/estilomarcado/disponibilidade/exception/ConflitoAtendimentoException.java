package br.ufpa.dsai.estilomarcado.disponibilidade.exception;

import java.util.List;

import br.ufpa.dsai.estilomarcado.catalogo.api.exception.ConflitoException;

/**
 * Conflito entre uma alteracao de disponibilidade e atendimentos futuros ativos.
 *
 * <p>A resposta HTTP inclui a lista de atendimentos conflitantes para que a
 * operacao possa ser corrigida antes de repetida.</p>
 */
public class ConflitoAtendimentoException extends ConflitoException {

    private final transient List<ItemConflito> conflitos;

    public ConflitoAtendimentoException(String mensagem, List<ItemConflito> conflitos) {
        super(mensagem);
        this.conflitos = List.copyOf(conflitos);
    }

    public List<ItemConflito> getConflitos() {
        return conflitos;
    }
}
