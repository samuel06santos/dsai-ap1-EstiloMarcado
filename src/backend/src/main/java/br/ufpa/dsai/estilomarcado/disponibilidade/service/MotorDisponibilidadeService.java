package br.ufpa.dsai.estilomarcado.disponibilidade.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import br.ufpa.dsai.estilomarcado.agendamento.model.AtendimentoStatus;
import br.ufpa.dsai.estilomarcado.agendamento.repository.AtendimentoRepository;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.RecursoNaoEncontradoException;
import br.ufpa.dsai.estilomarcado.catalogo.model.Profissional;
import br.ufpa.dsai.estilomarcado.catalogo.model.Servico;
import br.ufpa.dsai.estilomarcado.catalogo.model.Unidade;
import br.ufpa.dsai.estilomarcado.catalogo.repository.ServicoRepository;
import br.ufpa.dsai.estilomarcado.catalogo.repository.UnidadeRepository;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.ConsultaHorariosResponse;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.HorarioDisponivelResponse;
import br.ufpa.dsai.estilomarcado.disponibilidade.service.CalculadoraHorarios.Ocupacao;

@Service
public class MotorDisponibilidadeService {

    private final UnidadeRepository unidades;
    private final ServicoRepository servicos;
    private final AtendimentoRepository atendimentos;
    private final CalculadoraJanelas janelas;
    private final CalculadoraHorarios calculadora;
    private final Clock clock;

    public MotorDisponibilidadeService(UnidadeRepository unidades, ServicoRepository servicos,
            AtendimentoRepository atendimentos, CalculadoraJanelas janelas,
            CalculadoraHorarios calculadora, Clock clock) {
        this.unidades = unidades;
        this.servicos = servicos;
        this.atendimentos = atendimentos;
        this.janelas = janelas;
        this.calculadora = calculadora;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ConsultaHorariosResponse consultar(Long unidadeId, Long servicoId, LocalDate data,
                                              Long profissionalId) {
        Unidade unidade = unidades.findById(unidadeId)
                .filter(Unidade::isAtiva)
                .orElseThrow(() -> new RecursoNaoEncontradoException("filial nao encontrada"));
        Servico servico = servicos.findById(servicoId)
                .filter(Servico::isAtivo)
                .filter(item -> item.getUnidade().getId().equals(unidadeId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("servico nao encontrado"));
        ZoneId fuso = ZoneId.of(unidade.getFusoHorario());
        Instant agora = clock.instant();
        LocalDate hoje = agora.atZone(fuso).toLocalDate();
        if (data == null || data.isBefore(hoje) || data.isAfter(hoje.plusDays(60))) {
            throw new IllegalArgumentException("data deve estar entre hoje e os proximos 60 dias");
        }

        List<Profissional> elegiveis = servico.getProfissionais().stream()
                .filter(Profissional::isAtivo)
                .filter(item -> item.getUnidade().getId().equals(unidadeId))
                .filter(item -> profissionalId == null || profissionalId.equals(item.getId()))
                .toList();
        if (profissionalId != null && elegiveis.isEmpty()) {
            throw new RecursoNaoEncontradoException("profissional nao encontrado");
        }

        Map<Long, List<Ocupacao>> ocupacoes = elegiveis.isEmpty() ? Map.of()
                : atendimentos.buscarOcupacaoDoDia(elegiveis.stream().map(Profissional::getId).toList(),
                                data.atStartOfDay(), data.plusDays(1).atStartOfDay(),
                                List.of(AtendimentoStatus.AGENDADO, AtendimentoStatus.CONFIRMADO))
                        .stream().collect(Collectors.groupingBy(a -> a.getProfissional().getId(),
                                Collectors.mapping(a -> new Ocupacao(a.getInicio(),
                                        a.getDuracaoMinutos(), a.getIntervaloMinutos()), Collectors.toList())));
        Map<Long, List<JanelaTrabalho>> janelasDoDia = janelas.calcularEmLote(elegiveis, data);

        List<HorarioDisponivelResponse> horarios = elegiveis.stream()
                .flatMap(profissional -> calculadora.calcular(data, fuso, agora,
                                janelasDoDia.getOrDefault(profissional.getId(), List.of()),
                                ocupacoes.getOrDefault(profissional.getId(), List.of()),
                                servico.getDuracaoMinutos(), intervalo(servico))
                        .stream().map(inicio -> new HorarioDisponivelResponse(profissional.getId(),
                                inicio, inicio.plusMinutes(servico.getDuracaoMinutos()))))
                .sorted(Comparator.comparing(HorarioDisponivelResponse::inicio)
                        .thenComparing(HorarioDisponivelResponse::profissionalId))
                .distinct().toList();
        return new ConsultaHorariosResponse(unidadeId, servicoId, data, fuso.getId(), horarios);
    }

    private int intervalo(Servico servico) {
        return servico.getIntervaloMinutos() == null ? 0 : servico.getIntervaloMinutos();
    }
}
