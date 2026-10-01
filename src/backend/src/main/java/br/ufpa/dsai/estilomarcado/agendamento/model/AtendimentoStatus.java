package br.ufpa.dsai.estilomarcado.agendamento.model;

/**
 * Situacao de um atendimento na agenda do profissional.
 *
 * <p>Conjunto minimo exigido pelo painel profissional; a spec de agendamentos
 * definira as demais transicoes.</p>
 */
public enum AtendimentoStatus {
    AGENDADO,
    CONFIRMADO,
    CANCELADO
}
