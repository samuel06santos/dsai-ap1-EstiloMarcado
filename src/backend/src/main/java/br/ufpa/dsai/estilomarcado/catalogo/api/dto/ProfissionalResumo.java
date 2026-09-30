package br.ufpa.dsai.estilomarcado.catalogo.api.dto;

/**
 * Resumo de um profissional habilitado, exibido na resposta do servico.
 */
public record ProfissionalResumo(Long id, String nome, boolean ativo) {
}
