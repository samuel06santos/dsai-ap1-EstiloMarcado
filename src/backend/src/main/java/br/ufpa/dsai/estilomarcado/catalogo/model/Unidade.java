package br.ufpa.dsai.estilomarcado.catalogo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Unidade (estabelecimento) a que um servico pertence.
 *
 * <p>Esta e uma representacao minima, suficiente para o catalogo de servicos.
 * A spec propria de "estabelecimentos, profissionais e servicos" devera
 * expandir esta entidade com os demais atributos e regras do dominio.</p>
 */
@Entity
@Table(name = "unidade")
public class Unidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    protected Unidade() {
        // Construtor protegido exigido pelo JPA.
    }

    public Unidade(String nome) {
        this.nome = nome;
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
}
