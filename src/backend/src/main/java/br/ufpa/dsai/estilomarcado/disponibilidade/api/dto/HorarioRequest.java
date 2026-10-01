package br.ufpa.dsai.estilomarcado.disponibilidade.api.dto;

import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;

/**
 * Intervalo de horario sem dia da semana, usado por jornadas especiais.
 */
public record HorarioRequest(
        @NotNull(message = "horaInicio e obrigatoria") LocalTime horaInicio,
        @NotNull(message = "horaFim e obrigatoria") LocalTime horaFim) {
}
