package br.ufpa.dsai.estilomarcado.painel.api.dto;

import java.time.LocalDateTime;

import br.ufpa.dsai.estilomarcado.agendamento.model.Atendimento;

/**
 * Item da agenda exibido no painel profissional.
 */
public record AgendaAtendimentoResponse(
        Long id,
        LocalDateTime inicio,
        String servico,
        String cliente,
        String status) {

    public static AgendaAtendimentoResponse from(Atendimento atendimento) {
        return new AgendaAtendimentoResponse(
                atendimento.getId(),
                atendimento.getInicio(),
                atendimento.getServico().getNome(),
                atendimento.getCliente().getNome(),
                atendimento.getStatus().name());
    }
}
