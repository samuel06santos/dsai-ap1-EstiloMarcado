package br.ufpa.dsai.estilomarcado.disponibilidade.model;

import java.time.LocalTime;
import java.util.Objects;

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
 * Intervalo de uma jornada especial ({@code JORNADA_ESPECIAL}).
 */
@Entity
@Table(name = "excecao_jornada_intervalo")
public class ExcecaoJornadaIntervalo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "excecao_id", nullable = false)
    private ExcecaoJornada excecao;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fim", nullable = false)
    private LocalTime horaFim;

    protected ExcecaoJornadaIntervalo() {
        // Construtor protegido exigido pelo JPA.
    }

    public ExcecaoJornadaIntervalo(LocalTime horaInicio, LocalTime horaFim) {
        this.horaInicio = horaInicio;
        this.horaFim = horaFim;
    }

    public Long getId() { return id; }
    public ExcecaoJornada getExcecao() { return excecao; }
    public void setExcecao(ExcecaoJornada excecao) { this.excecao = excecao; }
    public LocalTime getHoraInicio() { return horaInicio; }
    public LocalTime getHoraFim() { return horaFim; }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) { return true; }
        if (!(outro instanceof ExcecaoJornadaIntervalo intervalo) || id == null) { return false; }
        return Objects.equals(id, intervalo.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
