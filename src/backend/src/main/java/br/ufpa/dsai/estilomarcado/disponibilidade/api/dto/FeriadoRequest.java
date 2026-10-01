package br.ufpa.dsai.estilomarcado.disponibilidade.api.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Feriado de uma filial.
 */
public record FeriadoRequest(
        @NotNull(message = "data e obrigatoria") LocalDate data,
        @NotBlank(message = "nome e obrigatorio")
        @Size(max = 120, message = "nome deve ter no maximo 120 caracteres") String nome) {
}
