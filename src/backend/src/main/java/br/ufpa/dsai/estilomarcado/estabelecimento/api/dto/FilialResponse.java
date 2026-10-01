package br.ufpa.dsai.estilomarcado.estabelecimento.api.dto;

import br.ufpa.dsai.estilomarcado.catalogo.model.Unidade;

public record FilialResponse(Long id, Long estabelecimentoId, String nome, String endereco,
                             String telefone, String fusoHorario, boolean ativa, boolean principal) {
    public static FilialResponse from(Unidade unidade) {
        return new FilialResponse(unidade.getId(), unidade.getEstabelecimento().getId(), unidade.getNome(),
                unidade.getEndereco(), unidade.getTelefone(), unidade.getFusoHorario(),
                unidade.isAtiva(), unidade.isPrincipal());
    }
}
