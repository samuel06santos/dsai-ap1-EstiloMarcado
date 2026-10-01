package br.ufpa.dsai.estilomarcado.disponibilidade.exception;

import java.time.LocalDateTime;

import br.ufpa.dsai.estilomarcado.agendamento.model.Atendimento;

/**
 * Atendimento futuro ativo que ficaria invalido por uma alteracao de
 * disponibilidade.
 */
public record ItemConflito(Long id, LocalDateTime inicio, Long servicoId, Long clienteId) {

    public static ItemConflito from(Atendimento atendimento) {
        return new ItemConflito(
                atendimento.getId(),
                atendimento.getInicio(),
                atendimento.getServico().getId(),
                atendimento.getCliente().getId());
    }
}
