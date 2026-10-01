package br.ufpa.dsai.estilomarcado.disponibilidade.service;

import java.time.LocalTime;

/**
 * Janela de trabalho em um dia, semiaberta: inclui {@code inicio} e exclui
 * {@code fim}.
 */
public record JanelaTrabalho(LocalTime inicio, LocalTime fim) {

    /**
     * Indica se o intervalo {@code [inicio, fim)} cabe inteiramente nesta
     * janela.
     */
    public boolean contem(LocalTime outroInicio, LocalTime outroFim) {
        return !outroInicio.isBefore(inicio) && !outroFim.isAfter(fim);
    }

    /**
     * Indica se o intervalo {@code [inicio, fim)} tem interseccao com esta
     * janela.
     */
    public boolean intersecta(LocalTime outroInicio, LocalTime outroFim) {
        return outroInicio.isBefore(fim) && inicio.isBefore(outroFim);
    }
}
