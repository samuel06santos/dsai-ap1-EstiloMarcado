package br.ufpa.dsai.estilomarcado.agendamento.model;

import java.time.LocalDateTime;
import java.time.Instant;
import java.math.BigDecimal;
import br.ufpa.dsai.estilomarcado.autenticacao.model.Usuario;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import br.ufpa.dsai.estilomarcado.catalogo.model.Profissional;
import br.ufpa.dsai.estilomarcado.catalogo.model.Servico;
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

/** Atendimento com snapshots comerciais e de ocupacao preservados no historico. */
@Entity
@Table(name = "atendimento")
public class Atendimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profissional_id", nullable = false)
    private Profissional profissional;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servico_id", nullable = false)
    private Servico servico;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(nullable = false)
    private LocalDateTime inicio;

    @Column(name = "duracao_minutos", nullable = false)
    private Integer duracaoMinutos;

    @Column(name = "intervalo_minutos", nullable = false)
    private Integer intervaloMinutos;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AtendimentoStatus status;

    @Column(name = "servico_nome", nullable = false, length = 120)
    private String servicoNome;

    @Column(name = "preco_acordado", nullable = false, precision = 12, scale = 2)
    private BigDecimal precoAcordado;

    @Column(name = "fuso_horario_agendamento", nullable = false, length = 80)
    private String fusoHorarioAgendamento;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Column(name = "cancelado_em")
    private Instant canceladoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelado_por")
    private Usuario canceladoPor;

    @Column(name = "motivo_cancelamento", length = 500)
    private String motivoCancelamento;

    protected Atendimento() {
        // Construtor protegido exigido pelo JPA.
    }

    public Atendimento(Profissional profissional, Servico servico, Cliente cliente,
                       LocalDateTime inicio, AtendimentoStatus status) {
        this.profissional = profissional;
        this.servico = servico;
        this.cliente = cliente;
        this.inicio = inicio;
        this.status = status;
        this.duracaoMinutos = servico.getDuracaoMinutos();
        this.intervaloMinutos = servico.getIntervaloMinutos() == null ? 0 : servico.getIntervaloMinutos();
        this.servicoNome = servico.getNome();
        this.precoAcordado = servico.getPreco();
        this.fusoHorarioAgendamento = servico.getUnidade().getFusoHorario();
    }

    @PrePersist
    void criarInstantes() { criadoEm = Instant.now(); atualizadoEm = criadoEm; }

    @PreUpdate
    void atualizarInstante() { atualizadoEm = Instant.now(); }

    public Long getId() {
        return id;
    }

    public Profissional getProfissional() {
        return profissional;
    }

    public Servico getServico() {
        return servico;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public LocalDateTime getInicio() {
        return inicio;
    }

    public void setInicio(LocalDateTime inicio) {
        this.inicio = inicio;
    }

    public AtendimentoStatus getStatus() {
        return status;
    }

    public void setStatus(AtendimentoStatus status) {
        this.status = status;
    }

    public Integer getDuracaoMinutos() {
        return duracaoMinutos;
    }

    public Integer getIntervaloMinutos() {
        return intervaloMinutos;
    }

    public String getServicoNome() { return servicoNome; }
    public BigDecimal getPrecoAcordado() { return precoAcordado; }
    public String getFusoHorarioAgendamento() { return fusoHorarioAgendamento; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
    public Instant getCanceladoEm() { return canceladoEm; }
    public String getMotivoCancelamento() { return motivoCancelamento; }
    public void cancelar(Usuario autor, String motivo) {
        status = AtendimentoStatus.CANCELADO;
        canceladoEm = Instant.now();
        canceladoPor = autor;
        motivoCancelamento = motivo;
    }
}
