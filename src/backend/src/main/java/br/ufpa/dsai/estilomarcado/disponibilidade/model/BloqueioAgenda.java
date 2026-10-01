package br.ufpa.dsai.estilomarcado.disponibilidade.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import br.ufpa.dsai.estilomarcado.catalogo.model.Profissional;
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
 * Bloqueio de agenda pontual de uma filial ou de um profissional.
 *
 * <p>Quando {@code profissional} e nulo, o bloqueio vale para todos os
 * profissionais da filial. Quando {@code diaInteiro} e verdadeiro, os horarios
 * sao nulos.</p>
 */
@Entity
@Table(name = "bloqueio_agenda")
public class BloqueioAgenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidade_id", nullable = false)
    private Unidade unidade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profissional_id")
    private Profissional profissional;

    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "dia_inteiro", nullable = false)
    private boolean diaInteiro;

    @Column(name = "hora_inicio")
    private LocalTime horaInicio;

    @Column(name = "hora_fim")
    private LocalTime horaFim;

    @Column(length = 500)
    private String motivo;

    @Column(name = "criado_por", nullable = false, updatable = false)
    private Long criadoPor;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    protected BloqueioAgenda() {
        // Construtor protegido exigido pelo JPA.
    }

    public BloqueioAgenda(Unidade unidade, Profissional profissional, LocalDate data,
                          boolean diaInteiro, LocalTime horaInicio, LocalTime horaFim,
                          String motivo, Long criadoPor) {
        this.unidade = unidade;
        this.profissional = profissional;
        this.data = data;
        this.diaInteiro = diaInteiro;
        this.horaInicio = horaInicio;
        this.horaFim = horaFim;
        this.motivo = motivo;
        this.criadoPor = criadoPor;
    }

    public Long getId() { return id; }
    public Unidade getUnidade() { return unidade; }
    public Profissional getProfissional() { return profissional; }
    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }
    public boolean isDiaInteiro() { return diaInteiro; }
    public void setDiaInteiro(boolean diaInteiro) { this.diaInteiro = diaInteiro; }
    public LocalTime getHoraInicio() { return horaInicio; }
    public void setHoraInicio(LocalTime horaInicio) { this.horaInicio = horaInicio; }
    public LocalTime getHoraFim() { return horaFim; }
    public void setHoraFim(LocalTime horaFim) { this.horaFim = horaFim; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public Long getCriadoPor() { return criadoPor; }
    public Instant getCriadoEm() { return criadoEm; }
}
