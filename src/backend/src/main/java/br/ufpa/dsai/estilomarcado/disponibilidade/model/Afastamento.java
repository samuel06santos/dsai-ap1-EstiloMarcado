package br.ufpa.dsai.estilomarcado.disponibilidade.model;

import java.time.Instant;
import java.time.LocalDate;

import br.ufpa.dsai.estilomarcado.catalogo.model.Profissional;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Afastamento de um profissional por um periodo (ferias, licenca etc.).
 *
 * <p>As datas sao inclusivas: o afastamento cobre {@code dataInicio} e
 * {@code dataFim}.</p>
 */
@Entity
@Table(name = "afastamento")
public class Afastamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profissional_id", nullable = false)
    private Profissional profissional;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim", nullable = false)
    private LocalDate dataFim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoAfastamento tipo;

    @Column(length = 500)
    private String descricao;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm = Instant.now();

    protected Afastamento() {
        // Construtor protegido exigido pelo JPA.
    }

    public Afastamento(Profissional profissional, LocalDate dataInicio, LocalDate dataFim,
                       TipoAfastamento tipo) {
        this.profissional = profissional;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
        this.tipo = tipo;
    }

    public Long getId() { return id; }
    public Profissional getProfissional() { return profissional; }
    public LocalDate getDataInicio() { return dataInicio; }
    public void setDataInicio(LocalDate dataInicio) { this.dataInicio = dataInicio; }
    public LocalDate getDataFim() { return dataFim; }
    public void setDataFim(LocalDate dataFim) { this.dataFim = dataFim; }
    public TipoAfastamento getTipo() { return tipo; }
    public void setTipo(TipoAfastamento tipo) { this.tipo = tipo; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }

    @PreUpdate
    void atualizarInstante() { atualizadoEm = Instant.now(); }
}
