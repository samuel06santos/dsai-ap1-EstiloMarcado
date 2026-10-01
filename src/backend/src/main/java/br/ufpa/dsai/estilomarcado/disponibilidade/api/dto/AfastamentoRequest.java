package br.ufpa.dsai.estilomarcado.disponibilidade.api.dto;

import java.time.LocalDate;

import br.ufpa.dsai.estilomarcado.disponibilidade.model.TipoAfastamento;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Afastamento de um profissional por um periodo inclusivo.
 */
public record AfastamentoRequest(
        @NotNull(message = "dataInicio e obrigatoria") LocalDate dataInicio,
        @NotNull(message = "dataFim e obrigatoria") LocalDate dataFim,
        @NotNull(message = "tipo e obrigatorio") TipoAfastamento tipo,
        @Size(max = 500, message = "descricao deve ter no maximo 500 caracteres") String descricao) {
}
