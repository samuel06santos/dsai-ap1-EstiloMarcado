package br.ufpa.dsai.estilomarcado.disponibilidade.api.dto;

import java.time.LocalDate;

import br.ufpa.dsai.estilomarcado.disponibilidade.model.Afastamento;
import br.ufpa.dsai.estilomarcado.disponibilidade.model.TipoAfastamento;

public record AfastamentoResponse(
        Long id, Long profissionalId, LocalDate dataInicio, LocalDate dataFim,
        TipoAfastamento tipo, String descricao) {

    public static AfastamentoResponse from(Afastamento afastamento) {
        return new AfastamentoResponse(
                afastamento.getId(),
                afastamento.getProfissional().getId(),
                afastamento.getDataInicio(),
                afastamento.getDataFim(),
                afastamento.getTipo(),
                afastamento.getDescricao());
    }
}
