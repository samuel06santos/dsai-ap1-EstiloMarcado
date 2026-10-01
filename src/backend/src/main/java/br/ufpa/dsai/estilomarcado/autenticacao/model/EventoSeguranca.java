package br.ufpa.dsai.estilomarcado.autenticacao.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "evento_seguranca")
public class EventoSeguranca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false, length = 50)
    private String tipo;

    @Column(nullable = false, length = 20)
    private String resultado;

    @Column(length = 100)
    private String origem;

    @Column(length = 500)
    private String detalhes;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected EventoSeguranca() {
    }

    public EventoSeguranca(Long usuarioId, String tipo, String resultado, String origem, String detalhes) {
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.resultado = resultado;
        this.origem = origem;
        this.detalhes = detalhes;
        this.criadoEm = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getTipo() { return tipo; }
    public String getResultado() { return resultado; }
    public String getOrigem() { return origem; }
    public String getDetalhes() { return detalhes; }
    public Instant getCriadoEm() { return criadoEm; }
}
