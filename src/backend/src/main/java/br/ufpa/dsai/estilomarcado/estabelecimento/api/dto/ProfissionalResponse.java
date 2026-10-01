package br.ufpa.dsai.estilomarcado.estabelecimento.api.dto;

import br.ufpa.dsai.estilomarcado.catalogo.model.Profissional;

public record ProfissionalResponse(Long id, Long unidadeId, String nome,
                                   String apresentacao, boolean ativo) {
    public static ProfissionalResponse from(Profissional profissional) {
        return new ProfissionalResponse(profissional.getId(), profissional.getUnidade().getId(),
                profissional.getNome(), profissional.getApresentacao(), profissional.isAtivo());
    }
}
