package br.ufpa.dsai.estilomarcado.agendamento.model;

import java.time.LocalDateTime;

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

/**
 * Atendimento agendado para um profissional, em um horario, para um servico e um
 * cliente.
 *
 * <p>Representacao minima exigida pelo painel profissional. A spec de
 * agendamentos expandira esta entidade com as regras de conflito e demais
 * transicoes de status.</p>
 */
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
    }

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
}
