package br.ufpa.dsai.estilomarcado.disponibilidade.api.dto;

import java.time.LocalTime;

import br.ufpa.dsai.estilomarcado.disponibilidade.model.JornadaIntervalo;

public record JornadaIntervaloResponse(
        Long id, int diaSemana, LocalTime horaInicio, LocalTime horaFim) {

    public static JornadaIntervaloResponse from(JornadaIntervalo intervalo) {
        return new JornadaIntervaloResponse(intervalo.getId(), intervalo.getDiaSemana(),
                intervalo.getHoraInicio(), intervalo.getHoraFim());
    }
}
