package br.ufpa.dsai.estilomarcado.catalogo.api.dto;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import br.ufpa.dsai.estilomarcado.catalogo.model.Servico;

/**
 * Representacao de um servico retornada pela API.
 */
public class ServicoResponse {

    private Long id;
    private Long unidadeId;
    private String unidadeNome;
    private String nome;
    private String descricao;
    private Integer duracaoMinutos;
    private BigDecimal preco;
    private Integer intervaloMinutos;
    private boolean ativo;
    private List<ProfissionalResumo> profissionais;

    public static ServicoResponse from(Servico servico) {
        ServicoResponse response = new ServicoResponse();
        response.id = servico.getId();
        response.unidadeId = servico.getUnidade().getId();
        response.unidadeNome = servico.getUnidade().getNome();
        response.nome = servico.getNome();
        response.descricao = servico.getDescricao();
        response.duracaoMinutos = servico.getDuracaoMinutos();
        response.preco = servico.getPreco();
        response.intervaloMinutos = servico.getIntervaloMinutos();
        response.ativo = servico.isAtivo();
        response.profissionais = servico.getProfissionais().stream()
                .map(p -> new ProfissionalResumo(p.getId(), p.getNome(), p.isAtivo()))
                .sorted(Comparator.comparing(ProfissionalResumo::nome))
                .toList();
        return response;
    }

    public Long getId() {
        return id;
    }

    public Long getUnidadeId() {
        return unidadeId;
    }

    public String getUnidadeNome() {
        return unidadeNome;
    }

    public String getNome() {
        return nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public Integer getDuracaoMinutos() {
        return duracaoMinutos;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public Integer getIntervaloMinutos() {
        return intervaloMinutos;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public List<ProfissionalResumo> getProfissionais() {
        return profissionais;
    }
}
