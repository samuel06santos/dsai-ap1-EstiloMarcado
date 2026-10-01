package br.ufpa.dsai.estilomarcado.disponibilidade.api.dto;

import java.time.LocalTime;

import br.ufpa.dsai.estilomarcado.disponibilidade.model.ExcecaoJornadaIntervalo;

public record HorarioResponse(Long id, LocalTime horaInicio, LocalTime horaFim) {

    public static HorarioResponse from(ExcecaoJornadaIntervalo intervalo) {
        return new HorarioResponse(intervalo.getId(), intervalo.getHoraInicio(), intervalo.getHoraFim());
    }
}
