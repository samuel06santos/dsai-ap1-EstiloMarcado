package br.ufpa.dsai.estilomarcado.disponibilidade.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import br.ufpa.dsai.estilomarcado.catalogo.model.Profissional;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Excecao de data da jornada de um profissional: folga ou jornada especial.
 *
 * <p>Existe no maximo uma excecao por profissional e data.</p>
 */
@Entity
@Table(name = "excecao_jornada")
public class ExcecaoJornada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profissional_id", nullable = false)
    private Profissional profissional;

    @Column(nullable = false)
    private LocalDate data;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoExcecaoJornada tipo;

    @Column(length = 500)
    private String motivo;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm = Instant.now();

    @OneToMany(mappedBy = "excecao", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("horaInicio ASC")
    private List<ExcecaoJornadaIntervalo> intervalos = new ArrayList<>();

    protected ExcecaoJornada() {
        // Construtor protegido exigido pelo JPA.
    }

    public ExcecaoJornada(Profissional profissional, LocalDate data, TipoExcecaoJornada tipo) {
        this.profissional = profissional;
        this.data = data;
        this.tipo = tipo;
    }

    public Long getId() { return id; }
    public Profissional getProfissional() { return profissional; }
    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }
    public TipoExcecaoJornada getTipo() { return tipo; }
    public void setTipo(TipoExcecaoJornada tipo) { this.tipo = tipo; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
    public List<ExcecaoJornadaIntervalo> getIntervalos() { return intervalos; }

    public void substituirIntervalos(List<ExcecaoJornadaIntervalo> novos) {
        intervalos.clear();
        novos.forEach(intervalo -> {
            intervalo.setExcecao(this);
            intervalos.add(intervalo);
        });
    }

    @PreUpdate
    void atualizarInstante() { atualizadoEm = Instant.now(); }
}
