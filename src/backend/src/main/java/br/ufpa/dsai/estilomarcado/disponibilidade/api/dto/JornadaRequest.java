package br.ufpa.dsai.estilomarcado.disponibilidade.api.dto;

import java.util.List;

import jakarta.validation.Valid;

/**
 * Substitui a jornada semanal completa de um profissional.
 */
public record JornadaRequest(
        List<@Valid JornadaIntervaloRequest> intervalos) {

    public List<JornadaIntervaloRequest> intervalosSeguros() {
        return intervalos == null ? List.of() : intervalos;
    }
}
