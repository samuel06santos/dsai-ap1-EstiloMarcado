package br.ufpa.dsai.estilomarcado.estabelecimento.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record FiliaisPublicasResponse(List<FilialResumo> itens, long total,
                                     int pagina, int tamanho, int totalPaginas) {
    public record FilialResumo(Long id, String nome, String estabelecimento, String endereco,
                               BigDecimal precoMinimo, BigDecimal precoMaximo,
                               List<ServicoResumo> servicos) {}

    public record ServicoResumo(String codigo, String nome, BigDecimal precoMinimo) {}
    public record ServicoOpcao(String codigo, String nome) {}
}
