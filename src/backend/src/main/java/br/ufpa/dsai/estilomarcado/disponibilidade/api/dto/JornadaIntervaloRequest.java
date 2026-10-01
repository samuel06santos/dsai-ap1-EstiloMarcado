package br.ufpa.dsai.estilomarcado.disponibilidade.api.dto;

import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Um intervalo da jornada semanal: dia da semana ISO (1 a 7) e horario.
 */
public record JornadaIntervaloRequest(
        @Min(value = 1, message = "diaSemana deve estar entre 1 e 7")
        @Max(value = 7, message = "diaSemana deve estar entre 1 e 7")
        int diaSemana,

        @NotNull(message = "horaInicio e obrigatoria")
        LocalTime horaInicio,

        @NotNull(message = "horaFim e obrigatoria")
        LocalTime horaFim) {
}
