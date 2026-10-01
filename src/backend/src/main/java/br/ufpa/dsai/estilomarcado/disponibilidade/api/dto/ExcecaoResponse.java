package br.ufpa.dsai.estilomarcado.disponibilidade.api.dto;

import java.time.LocalDate;
import java.util.List;

import br.ufpa.dsai.estilomarcado.disponibilidade.model.ExcecaoJornada;
import br.ufpa.dsai.estilomarcado.disponibilidade.model.TipoExcecaoJornada;

public record ExcecaoResponse(
        Long id, Long profissionalId, LocalDate data, TipoExcecaoJornada tipo,
        String motivo, List<HorarioResponse> intervalos) {

    public static ExcecaoResponse from(ExcecaoJornada excecao) {
        return new ExcecaoResponse(
                excecao.getId(),
                excecao.getProfissional().getId(),
                excecao.getData(),
                excecao.getTipo(),
                excecao.getMotivo(),
                excecao.getIntervalos().stream().map(HorarioResponse::from).toList());
    }
}
