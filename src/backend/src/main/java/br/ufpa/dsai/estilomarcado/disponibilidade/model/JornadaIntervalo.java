package br.ufpa.dsai.estilomarcado.disponibilidade.model;

import java.time.LocalTime;
import java.util.Objects;

import br.ufpa.dsai.estilomarcado.catalogo.model.Profissional;
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
 * Intervalo da jornada semanal de um profissional em um dia da semana.
 *
 * <p>O dia da semana segue a norma ISO: 1 = segunda-feira e 7 = domingo. Um
 * intervalo nunca atravessa a meia-noite.</p>
 */
@Entity
@Table(name = "jornada_intervalo")
public class JornadaIntervalo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profissional_id", nullable = false)
    private Profissional profissional;

    @Column(name = "dia_semana", nullable = false)
    private short diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fim", nullable = false)
    private LocalTime horaFim;

    protected JornadaIntervalo() {
        // Construtor protegido exigido pelo JPA.
    }

    public JornadaIntervalo(Profissional profissional, int diaSemana,
                            LocalTime horaInicio, LocalTime horaFim) {
        this.profissional = profissional;
        this.diaSemana = (short) diaSemana;
        this.horaInicio = horaInicio;
        this.horaFim = horaFim;
    }

    public Long getId() { return id; }
    public Profissional getProfissional() { return profissional; }
    public int getDiaSemana() { return diaSemana; }
    public LocalTime getHoraInicio() { return horaInicio; }
    public LocalTime getHoraFim() { return horaFim; }

    public void setHoraInicio(LocalTime horaInicio) { this.horaInicio = horaInicio; }
    public void setHoraFim(LocalTime horaFim) { this.horaFim = horaFim; }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) { return true; }
        if (!(outro instanceof JornadaIntervalo intervalo) || id == null) { return false; }
        return Objects.equals(id, intervalo.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
