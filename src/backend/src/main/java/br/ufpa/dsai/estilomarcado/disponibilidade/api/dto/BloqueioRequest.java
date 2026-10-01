package br.ufpa.dsai.estilomarcado.disponibilidade.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Bloqueio de agenda de uma filial (profissionalId nulo) ou de um profissional.
 */
public record BloqueioRequest(
        Long profissionalId,
        @NotNull(message = "data e obrigatoria") LocalDate data,
        boolean diaInteiro,
        LocalTime horaInicio,
        LocalTime horaFim,
        @Size(max = 500, message = "motivo deve ter no maximo 500 caracteres") String motivo) {
}
