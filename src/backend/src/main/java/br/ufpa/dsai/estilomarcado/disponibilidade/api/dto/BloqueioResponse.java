package br.ufpa.dsai.estilomarcado.disponibilidade.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import br.ufpa.dsai.estilomarcado.disponibilidade.model.BloqueioAgenda;

public record BloqueioResponse(
        Long id, Long unidadeId, Long profissionalId, LocalDate data, boolean diaInteiro,
        LocalTime horaInicio, LocalTime horaFim, String motivo) {

    public static BloqueioResponse from(BloqueioAgenda bloqueio) {
        return new BloqueioResponse(
                bloqueio.getId(),
                bloqueio.getUnidade().getId(),
                bloqueio.getProfissional() == null ? null : bloqueio.getProfissional().getId(),
                bloqueio.getData(),
                bloqueio.isDiaInteiro(),
                bloqueio.getHoraInicio(),
                bloqueio.getHoraFim(),
                bloqueio.getMotivo());
    }
}
