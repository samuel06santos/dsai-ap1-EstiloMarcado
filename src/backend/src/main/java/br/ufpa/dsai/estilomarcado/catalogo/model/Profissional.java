package br.ufpa.dsai.estilomarcado.catalogo.model;

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
 * Profissional habilitado a executar servicos de uma unidade.
 *
 * <p>Representacao minima exigida pelo catalogo de servicos: um profissional
 * pertence a uma unidade e pode estar ativo ou inativo. A spec propria de
 * profissionais expandira esta entidade.</p>
 */
@Entity
@Table(name = "profissional")
public class Profissional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false)
    private boolean ativo = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidade_id", nullable = false)
    private Unidade unidade;

    protected Profissional() {
        // Construtor protegido exigido pelo JPA.
    }

    public Profissional(String nome, Unidade unidade) {
        this.nome = nome;
        this.unidade = unidade;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public Unidade getUnidade() {
        return unidade;
    }
}
