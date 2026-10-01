package br.ufpa.dsai.estilomarcado.disponibilidade.model;

import java.time.Instant;
import java.time.LocalDate;

import br.ufpa.dsai.estilomarcado.catalogo.model.Unidade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Feriado de uma filial. Fecha a filial inteira na data informada.
 */
@Entity
@Table(name = "feriado")
public class Feriado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidade_id", nullable = false)
    private Unidade unidade;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    protected Feriado() {
        // Construtor protegido exigido pelo JPA.
    }

    public Feriado(Unidade unidade, LocalDate data, String nome) {
        this.unidade = unidade;
        this.data = data;
        this.nome = nome;
    }

    public Long getId() { return id; }
    public Unidade getUnidade() { return unidade; }
    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public Instant getCriadoEm() { return criadoEm; }
}
