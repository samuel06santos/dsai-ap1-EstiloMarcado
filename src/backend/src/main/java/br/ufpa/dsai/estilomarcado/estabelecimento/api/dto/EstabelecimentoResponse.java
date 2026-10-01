package br.ufpa.dsai.estilomarcado.estabelecimento.api.dto;

import java.util.List;

import br.ufpa.dsai.estilomarcado.catalogo.model.Estabelecimento;

public record EstabelecimentoResponse(Long id, String nome, boolean administradorPrincipal,
                                      List<FilialResumo> filiais) {
    public static EstabelecimentoResponse from(Estabelecimento estabelecimento,
                                                boolean administradorPrincipal,
                                                List<FilialResumo> filiais) {
        return new EstabelecimentoResponse(estabelecimento.getId(), estabelecimento.getNome(),
                administradorPrincipal, filiais);
    }

    public record FilialResumo(Long id, String nome, boolean ativa) {}
}
