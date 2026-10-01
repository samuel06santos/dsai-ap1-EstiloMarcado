package br.ufpa.dsai.estilomarcado.disponibilidade.api.dto;

import java.time.LocalDate;

import br.ufpa.dsai.estilomarcado.disponibilidade.model.Feriado;

public record FeriadoResponse(Long id, Long unidadeId, LocalDate data, String nome) {

    public static FeriadoResponse from(Feriado feriado) {
        return new FeriadoResponse(feriado.getId(), feriado.getUnidade().getId(),
                feriado.getData(), feriado.getNome());
    }
}
