package br.ufpa.dsai.estilomarcado.disponibilidade.api.dto;

import java.time.LocalTime;

import br.ufpa.dsai.estilomarcado.disponibilidade.service.JanelaTrabalho;

/**
 * Janela de trabalho resultante da composicao das regras de disponibilidade.
 */
public record JanelaResponse(LocalTime inicio, LocalTime fim) {

    public static JanelaResponse from(JanelaTrabalho janela) {
        return new JanelaResponse(janela.inicio(), janela.fim());
    }
}
