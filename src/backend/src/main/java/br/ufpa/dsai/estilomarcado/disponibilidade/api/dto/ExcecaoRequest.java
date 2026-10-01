package br.ufpa.dsai.estilomarcado.disponibilidade.api.dto;

import java.time.LocalDate;
import java.util.List;

import br.ufpa.dsai.estilomarcado.disponibilidade.model.TipoExcecaoJornada;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Excecao de data de um profissional: folga ou jornada especial.
 */
public record ExcecaoRequest(
        @NotNull(message = "data e obrigatoria") LocalDate data,
        @NotNull(message = "tipo e obrigatorio") TipoExcecaoJornada tipo,
        @Size(max = 500, message = "motivo deve ter no maximo 500 caracteres") String motivo,
        List<@Valid HorarioRequest> intervalos) {

    public List<HorarioRequest> intervalosSeguros() {
        return intervalos == null ? List.of() : intervalos;
    }
}
