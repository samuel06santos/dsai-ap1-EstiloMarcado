package br.ufpa.dsai.estilomarcado.catalogo.model;

import java.time.Instant;
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "unidade")
public class Unidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(name = "nome_normalizado", nullable = false, length = 120)
    private String nomeNormalizado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "estabelecimento_id", nullable = false)
    private Estabelecimento estabelecimento;

    @Column(nullable = false)
    private boolean principal;

    @Column(length = 250)
    private String endereco;

    @Column(length = 30)
    private String telefone;

    @Column(name = "fuso_horario", nullable = false, length = 80)
    private String fusoHorario = "America/Sao_Paulo";

    @Column(nullable = false)
    private boolean ativa = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm = Instant.now();

    protected Unidade() {
        // Construtor protegido exigido pelo JPA.
    }

    public Unidade(String nome) {
        this(nome, new Estabelecimento(nome), true);
    }

    public Unidade(String nome, Estabelecimento estabelecimento, boolean principal) {
        setNome(nome);
        this.estabelecimento = estabelecimento;
        this.principal = principal;
        this.ativa = principal;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome.trim();
        this.nomeNormalizado = nome.trim().toLowerCase(Locale.ROOT);
    }

    public Estabelecimento getEstabelecimento() { return estabelecimento; }
    public boolean isPrincipal() { return principal; }
    public String getEndereco() { return endereco; }
    public void setEndereco(String endereco) { this.endereco = endereco; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public String getFusoHorario() { return fusoHorario; }
    public void setFusoHorario(String fusoHorario) { this.fusoHorario = fusoHorario; }
    public boolean isAtiva() { return ativa; }
    public void setAtiva(boolean ativa) { this.ativa = ativa; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }

    @PreUpdate
    void atualizarInstante() { atualizadoEm = Instant.now(); }
}
