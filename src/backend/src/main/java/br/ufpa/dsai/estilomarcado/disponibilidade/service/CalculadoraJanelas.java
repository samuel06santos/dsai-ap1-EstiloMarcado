package br.ufpa.dsai.estilomarcado.disponibilidade.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import br.ufpa.dsai.estilomarcado.catalogo.model.Profissional;
import br.ufpa.dsai.estilomarcado.catalogo.model.Unidade;
import br.ufpa.dsai.estilomarcado.disponibilidade.model.BloqueioAgenda;
import br.ufpa.dsai.estilomarcado.disponibilidade.model.ExcecaoJornada;
import br.ufpa.dsai.estilomarcado.disponibilidade.repository.AfastamentoRepository;
import br.ufpa.dsai.estilomarcado.disponibilidade.repository.BloqueioAgendaRepository;
import br.ufpa.dsai.estilomarcado.disponibilidade.repository.ExcecaoJornadaRepository;
import br.ufpa.dsai.estilomarcado.disponibilidade.repository.FeriadoRepository;
import br.ufpa.dsai.estilomarcado.disponibilidade.repository.JornadaIntervaloRepository;

/**
 * Compila as regras de disponibilidade em janelas de trabalho de um
 * profissional em uma data.
 *
 * <p>A ordem de composicao e a definida na SPEC de jornada, folgas, feriados e
 * bloqueios: filial e profissional ativos, feriado, afastamento, excecao de
 * data, jornada semanal e, por fim, bloqueios de agenda.</p>
 */
@Component
public class CalculadoraJanelas {

    private final JornadaIntervaloRepository jornadas;
    private final ExcecaoJornadaRepository excecoes;
    private final AfastamentoRepository afastamentos;
    private final FeriadoRepository feriados;
    private final BloqueioAgendaRepository bloqueios;

    public CalculadoraJanelas(JornadaIntervaloRepository jornadas,
                              ExcecaoJornadaRepository excecoes,
                              AfastamentoRepository afastamentos,
                              FeriadoRepository feriados,
                              BloqueioAgendaRepository bloqueios) {
        this.jornadas = jornadas;
        this.excecoes = excecoes;
        this.afastamentos = afastamentos;
        this.feriados = feriados;
        this.bloqueios = bloqueios;
    }

    public List<JanelaTrabalho> calcular(Profissional profissional, LocalDate data) {
        Unidade unidade = profissional.getUnidade();
        if (!unidade.isAtiva() || !profissional.isAtivo()) {
            return List.of();
        }
        if (feriados.existsByUnidadeIdAndData(unidade.getId(), data)) {
            return List.of();
        }
        if (afastamentos.existsByProfissionalIdAndDataInicioLessThanEqualAndDataFimGreaterThanEqual(
                profissional.getId(), data, data)) {
            return List.of();
        }

        List<JanelaTrabalho> base = base(profissional, data);
        List<BloqueioAgenda> doDia = bloquesDoDia(unidade.getId(), profissional.getId(), data);
        if (doDia.stream().anyMatch(BloqueioAgenda::isDiaInteiro)) {
            return List.of();
        }
        List<JanelaTrabalho> recortes = doDia.stream()
                .map(b -> new JanelaTrabalho(b.getHoraInicio(), b.getHoraFim()))
                .toList();
        return subtrair(base, recortes);
    }

    /** Carrega as regras de uma filial/data em lotes para a consulta publica. */
    public Map<Long, List<JanelaTrabalho>> calcularEmLote(List<Profissional> profissionais, LocalDate data) {
        if (profissionais.isEmpty()) {
            return Map.of();
        }
        Unidade unidade = profissionais.getFirst().getUnidade();
        if (!unidade.isAtiva() || feriados.existsByUnidadeIdAndData(unidade.getId(), data)) {
            return Map.of();
        }
        List<Long> ids = profissionais.stream().filter(Profissional::isAtivo)
                .map(Profissional::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Set<Long> ausentes = afastamentos
                .findByProfissionalIdInAndDataInicioLessThanEqualAndDataFimGreaterThanEqual(ids, data, data)
                .stream().map(item -> item.getProfissional().getId()).collect(Collectors.toSet());
        Map<Long, ExcecaoJornada> especiais = excecoes.findDoDiaComIntervalos(ids, data).stream()
                .collect(Collectors.toMap(item -> item.getProfissional().getId(), Function.identity()));
        Map<Long, List<JanelaTrabalho>> semana = jornadas
                .findByProfissionalIdInAndDiaSemanaOrderByHoraInicioAsc(ids, data.getDayOfWeek().getValue())
                .stream().collect(Collectors.groupingBy(item -> item.getProfissional().getId(),
                        Collectors.mapping(item -> new JanelaTrabalho(item.getHoraInicio(), item.getHoraFim()),
                                Collectors.toList())));
        List<BloqueioAgenda> todosBloqueios = bloqueios.findByUnidadeIdAndData(unidade.getId(), data);

        return profissionais.stream().collect(Collectors.toMap(Profissional::getId, profissional -> {
            Long id = profissional.getId();
            if (!profissional.isAtivo() || ausentes.contains(id)) {
                return List.<JanelaTrabalho>of();
            }
            List<JanelaTrabalho> base = especiais.containsKey(id)
                    ? deExcecao(especiais.get(id)) : semana.getOrDefault(id, List.of());
            List<BloqueioAgenda> doDia = todosBloqueios.stream()
                    .filter(b -> b.getProfissional() == null || b.getProfissional().getId().equals(id))
                    .toList();
            if (doDia.stream().anyMatch(BloqueioAgenda::isDiaInteiro)) {
                return List.<JanelaTrabalho>of();
            }
            List<JanelaTrabalho> recortes = doDia.stream()
                    .map(b -> new JanelaTrabalho(b.getHoraInicio(), b.getHoraFim())).toList();
            return subtrair(base, recortes);
        }));
    }

    private List<JanelaTrabalho> base(Profissional profissional, LocalDate data) {
        var excecao = excecoes.findByProfissionalIdAndData(profissional.getId(), data);
        if (excecao.isPresent()) {
            return deExcecao(excecao.get());
        }
        int diaSemana = data.getDayOfWeek().getValue();
        return jornadas.findByProfissionalIdOrderByDiaSemanaAscHoraInicioAsc(profissional.getId()).stream()
                .filter(intervalo -> intervalo.getDiaSemana() == diaSemana)
                .map(intervalo -> new JanelaTrabalho(intervalo.getHoraInicio(), intervalo.getHoraFim()))
                .sorted(Comparator.comparing(JanelaTrabalho::inicio))
                .toList();
    }

    private List<JanelaTrabalho> deExcecao(ExcecaoJornada excecao) {
        return excecao.getIntervalos().stream()
                .map(intervalo -> new JanelaTrabalho(intervalo.getHoraInicio(), intervalo.getHoraFim()))
                .sorted(Comparator.comparing(JanelaTrabalho::inicio))
                .toList();
    }

    private List<BloqueioAgenda> bloquesDoDia(Long unidadeId, Long profissionalId, LocalDate data) {
        List<BloqueioAgenda> lista = new ArrayList<>();
        lista.addAll(bloqueios.findByUnidadeIdAndDataAndProfissionalIsNull(unidadeId, data));
        lista.addAll(bloqueios.findByProfissionalIdAndData(profissionalId, data));
        return lista;
    }

    private List<JanelaTrabalho> subtrair(List<JanelaTrabalho> base, List<JanelaTrabalho> recortes) {
        List<JanelaTrabalho> resultado = new ArrayList<>(base);
        for (JanelaTrabalho recorte : recortes) {
            List<JanelaTrabalho> proximo = new ArrayList<>();
            for (JanelaTrabalho janela : resultado) {
                if (!janela.intersecta(recorte.inicio(), recorte.fim())) {
                    proximo.add(janela);
                    continue;
                }
                if (janela.inicio().isBefore(recorte.inicio())) {
                    proximo.add(new JanelaTrabalho(janela.inicio(), recorte.inicio()));
                }
                if (recorte.fim().isBefore(janela.fim())) {
                    proximo.add(new JanelaTrabalho(recorte.fim(), janela.fim()));
                }
            }
            resultado = proximo;
        }
        return resultado.stream().sorted(Comparator.comparing(JanelaTrabalho::inicio)).toList();
    }
}
