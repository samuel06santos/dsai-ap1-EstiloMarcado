package br.ufpa.dsai.estilomarcado.catalogo.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "estabelecimento")
public class Estabelecimento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm = Instant.now();

    protected Estabelecimento() {}

    public Estabelecimento(String nome) { this.nome = nome.trim(); }
    public Long getId() { return id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome.trim(); }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }

    @PreUpdate
    void atualizarInstante() { atualizadoEm = Instant.now(); }
}
