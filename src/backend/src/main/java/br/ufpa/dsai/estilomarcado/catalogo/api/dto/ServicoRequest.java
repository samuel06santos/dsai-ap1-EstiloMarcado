package br.ufpa.dsai.estilomarcado.catalogo.api.dto;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Corpo de criacao ou atualizacao de um servico.
 */
public class ServicoRequest {

    @NotBlank(message = "nome e obrigatorio")
    @Size(min = 2, max = 120, message = "nome deve ter entre 2 e 120 caracteres")
    private String nome;

    @Size(max = 1000, message = "descricao deve ter no maximo 1000 caracteres")
    private String descricao;

    @NotNull(message = "duracaoMinutos e obrigatoria")
    @Positive(message = "duracaoMinutos deve ser maior que zero")
    private Integer duracaoMinutos;

    @NotNull(message = "preco e obrigatorio")
    @DecimalMin(value = "0.00", message = "preco nao pode ser negativo")
    @Digits(integer = 10, fraction = 2, message = "preco deve ter no maximo 2 casas decimais")
    private BigDecimal preco;

    @Min(value = 0, message = "intervaloMinutos nao pode ser negativo")
    private Integer intervaloMinutos;

    private Set<@NotNull Long> profissionalIds = new LinkedHashSet<>();

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public Integer getDuracaoMinutos() {
        return duracaoMinutos;
    }

    public void setDuracaoMinutos(Integer duracaoMinutos) {
        this.duracaoMinutos = duracaoMinutos;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public void setPreco(BigDecimal preco) {
        this.preco = preco;
    }

    public Integer getIntervaloMinutos() {
        return intervaloMinutos;
    }

    public void setIntervaloMinutos(Integer intervaloMinutos) {
        this.intervaloMinutos = intervaloMinutos;
    }

    public Set<Long> getProfissionalIds() {
        return profissionalIds;
    }

    public void setProfissionalIds(Set<Long> profissionalIds) {
        this.profissionalIds = profissionalIds;
    }
}
