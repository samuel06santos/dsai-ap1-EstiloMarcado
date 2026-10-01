package br.ufpa.dsai.estilomarcado.disponibilidade.api.dto;

import java.util.List;

public record JornadaResponse(
        Long profissionalId, Long unidadeId, List<JornadaIntervaloResponse> intervalos) {
}
