package br.ufpa.dsai.estilomarcado.autenticacao.model;

import java.time.Instant;

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
import jakarta.persistence.Table;

@Entity
@Table(name = "token_usuario")
public class TokenUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FinalidadeToken finalidade;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "consumido_em")
    private Instant consumidoEm;

    protected TokenUsuario() {
    }

    public TokenUsuario(Usuario usuario, String tokenHash, FinalidadeToken finalidade, Instant expiraEm) {
        this.usuario = usuario;
        this.tokenHash = tokenHash;
        this.finalidade = finalidade;
        this.criadoEm = Instant.now();
        this.expiraEm = expiraEm;
    }

    public boolean estaValido(Instant agora) {
        return consumidoEm == null && expiraEm.isAfter(agora);
    }

    public void consumir(Instant agora) {
        consumidoEm = agora;
    }

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public String getTokenHash() { return tokenHash; }
    public FinalidadeToken getFinalidade() { return finalidade; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getExpiraEm() { return expiraEm; }
    public Instant getConsumidoEm() { return consumidoEm; }
}
