package br.ufpa.dsai.estilomarcado.disponibilidade.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/** Calcula inicios de servico a partir de janelas e ocupacoes de um dia. */
@Component
public class CalculadoraHorarios {

    private static final int PASSO_MINUTOS = 15;

    public record Ocupacao(LocalDateTime inicio, int duracaoMinutos, int intervaloMinutos) {
        LocalDateTime fimOcupado() {
            return inicio.plusMinutes((long) duracaoMinutos + intervaloMinutos);
        }
    }

    public List<LocalDateTime> calcular(LocalDate data, ZoneId fuso, Instant agora,
                                       List<JanelaTrabalho> janelas, List<Ocupacao> ocupacoes,
                                       int duracaoMinutos, int intervaloMinutos) {
        List<LocalDateTime> resultado = new ArrayList<>();
        for (JanelaTrabalho janela : janelas) {
            for (int minuto = alinhar(janela.inicio()); minuto < 24 * 60;
                 minuto += PASSO_MINUTOS) {
                LocalTime hora = LocalTime.of(minuto / 60, minuto % 60);
                if (!hora.isBefore(janela.fim())) {
                    break;
                }
                LocalDateTime inicio = data.atTime(hora);
                LocalDateTime fim = inicio.plusMinutes(duracaoMinutos);
                if (!fim.toLocalDate().equals(data) || fim.toLocalTime().isAfter(janela.fim())) {
                    continue;
                }
                if (!temInstanteUnico(inicio, fim, fuso, duracaoMinutos)
                        || inicio.atZone(fuso).toInstant().isBefore(agora)) {
                    continue;
                }
                LocalDateTime fimComIntervalo = fim.plusMinutes(intervaloMinutos);
                boolean conflito = ocupacoes.stream().anyMatch(ocupacao ->
                        intersecta(inicio, fim, ocupacao.inicio(), ocupacao.fimOcupado())
                        || !ocupacao.inicio().isBefore(inicio)
                        && ocupacao.inicio().isBefore(fimComIntervalo));
                if (!conflito) {
                    resultado.add(inicio);
                }
            }
        }
        return resultado;
    }

    private int alinhar(LocalTime hora) {
        int minuto = hora.getHour() * 60 + hora.getMinute();
        if (hora.getSecond() > 0 || hora.getNano() > 0) {
            minuto++;
        }
        int alinhado = ((minuto + PASSO_MINUTOS - 1) / PASSO_MINUTOS) * PASSO_MINUTOS;
        return alinhado;
    }

    private boolean intersecta(LocalDateTime inicioA, LocalDateTime fimA,
                                LocalDateTime inicioB, LocalDateTime fimB) {
        return inicioA.isBefore(fimB) && inicioB.isBefore(fimA);
    }

    private boolean temInstanteUnico(LocalDateTime inicio, LocalDateTime fim,
                                     ZoneId fuso, int duracaoMinutos) {
        if (fuso.getRules().getValidOffsets(inicio).size() != 1
                || fuso.getRules().getValidOffsets(fim).size() != 1) {
            return false;
        }
        return Duration.between(inicio.atZone(fuso).toInstant(), fim.atZone(fuso).toInstant())
                .toMinutes() == duracaoMinutos;
    }
}
