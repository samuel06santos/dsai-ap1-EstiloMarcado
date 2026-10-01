package br.ufpa.dsai.estilomarcado.disponibilidade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.ufpa.dsai.estilomarcado.disponibilidade.service.CalculadoraHorarios;
import br.ufpa.dsai.estilomarcado.disponibilidade.service.CalculadoraHorarios.Ocupacao;
import br.ufpa.dsai.estilomarcado.disponibilidade.service.JanelaTrabalho;

class CalculadoraHorariosTest {
    private final CalculadoraHorarios calculadora = new CalculadoraHorarios();
    private final ZoneId fuso = ZoneId.of("America/Sao_Paulo");
    private final LocalDate data = LocalDate.of(2026, 10, 5);
    private final Instant antes = Instant.parse("2026-10-04T00:00:00Z");

    @Test
    void alinhaGradeERespeitaFimDaJanela() {
        var horarios = calcular(data, fuso, antes,
                List.of(new JanelaTrabalho(LocalTime.of(9, 7), LocalTime.of(10, 2))),
                List.of(), 30, 0);
        assertEquals(List.of(data.atTime(9, 15), data.atTime(9, 30)), horarios);
    }

    @Test
    void naoUneJanelasAdjacentesNemAtravessaMeiaNoite() {
        assertEquals(List.of(), calcular(data, fuso, antes,
                List.of(new JanelaTrabalho(LocalTime.of(9, 0), LocalTime.of(9, 30)),
                        new JanelaTrabalho(LocalTime.of(9, 30), LocalTime.of(10, 0))),
                List.of(), 45, 0));
        assertEquals(List.of(), calcular(data, fuso, antes,
                List.of(new JanelaTrabalho(LocalTime.of(23, 30), LocalTime.of(23, 59))),
                List.of(), 45, 0));
    }

    @Test
    void aplicaPausaExistenteEPausaDoCandidato() {
        var ocupacoes = List.of(new Ocupacao(data.atTime(9, 0), 45, 15),
                new Ocupacao(data.atTime(11, 0), 30, 0));
        var horarios = calcular(data, fuso, antes,
                List.of(new JanelaTrabalho(LocalTime.of(9, 0), LocalTime.NOON)),
                ocupacoes, 45, 15);
        assertEquals(List.of(data.atTime(10, 0)), horarios);
    }

    @Test
    void permiteUltimoServicoMesmoComPausaForaDaJornada() {
        assertEquals(List.of(data.atTime(11, 15)), calcular(data, fuso, antes,
                List.of(new JanelaTrabalho(LocalTime.of(11, 15), LocalTime.NOON)),
                List.of(), 45, 30));
    }

    @Test
    void excluiInicioPassadoPeloRelogioDaFilial() {
        assertEquals(List.of(data.atTime(10, 15)), calcular(data, fuso,
                data.atTime(10, 1).atZone(fuso).toInstant(),
                List.of(new JanelaTrabalho(LocalTime.of(10, 0), LocalTime.of(10, 45))),
                List.of(), 30, 0));
    }

    @Test
    void excluiHorasInexistentesEAmbiguasEmTransicoesDeFuso() {
        ZoneId novaYork = ZoneId.of("America/New_York");
        LocalDate primavera = LocalDate.of(2026, 3, 8);
        assertEquals(List.of(), calcular(primavera, novaYork, antes,
                List.of(new JanelaTrabalho(LocalTime.of(1, 30), LocalTime.of(3, 30))),
                List.of(), 60, 0));
        LocalDate outono = LocalDate.of(2026, 11, 1);
        assertEquals(List.of(), calcular(outono, novaYork, antes,
                List.of(new JanelaTrabalho(LocalTime.of(1, 0), LocalTime.of(2, 0))),
                List.of(), 30, 0));
    }

    private List<LocalDateTime> calcular(LocalDate data, ZoneId zona, Instant agora,
            List<JanelaTrabalho> janelas, List<Ocupacao> ocupacoes, int duracao, int intervalo) {
        return calculadora.calcular(data, zona, agora, janelas, ocupacoes, duracao, intervalo);
    }
}
