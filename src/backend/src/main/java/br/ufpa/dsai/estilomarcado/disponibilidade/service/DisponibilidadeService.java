package br.ufpa.dsai.estilomarcado.disponibilidade.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.ufpa.dsai.estilomarcado.agendamento.model.Atendimento;
import br.ufpa.dsai.estilomarcado.agendamento.model.AtendimentoStatus;
import br.ufpa.dsai.estilomarcado.agendamento.repository.AtendimentoRepository;
import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioAtual;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioPrincipal;
import br.ufpa.dsai.estilomarcado.autenticacao.service.AuditoriaService;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.ConflitoException;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.RecursoNaoEncontradoException;
import br.ufpa.dsai.estilomarcado.catalogo.model.Profissional;
import br.ufpa.dsai.estilomarcado.catalogo.model.Unidade;
import br.ufpa.dsai.estilomarcado.catalogo.repository.ProfissionalRepository;
import br.ufpa.dsai.estilomarcado.catalogo.repository.UnidadeRepository;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.AfastamentoRequest;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.AfastamentoResponse;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.BloqueioRequest;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.BloqueioResponse;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.ExcecaoRequest;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.ExcecaoResponse;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.FeriadoRequest;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.FeriadoResponse;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.HorarioRequest;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.JanelaResponse;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.JornadaIntervaloRequest;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.JornadaIntervaloResponse;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.JornadaRequest;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.JornadaResponse;
import br.ufpa.dsai.estilomarcado.disponibilidade.exception.ConflitoAtendimentoException;
import br.ufpa.dsai.estilomarcado.disponibilidade.exception.ItemConflito;
import br.ufpa.dsai.estilomarcado.disponibilidade.model.Afastamento;
import br.ufpa.dsai.estilomarcado.disponibilidade.model.BloqueioAgenda;
import br.ufpa.dsai.estilomarcado.disponibilidade.model.ExcecaoJornada;
import br.ufpa.dsai.estilomarcado.disponibilidade.model.ExcecaoJornadaIntervalo;
import br.ufpa.dsai.estilomarcado.disponibilidade.model.Feriado;
import br.ufpa.dsai.estilomarcado.disponibilidade.model.JornadaIntervalo;
import br.ufpa.dsai.estilomarcado.disponibilidade.model.TipoExcecaoJornada;
import br.ufpa.dsai.estilomarcado.disponibilidade.repository.AfastamentoRepository;
import br.ufpa.dsai.estilomarcado.disponibilidade.repository.BloqueioAgendaRepository;
import br.ufpa.dsai.estilomarcado.disponibilidade.repository.ExcecaoJornadaRepository;
import br.ufpa.dsai.estilomarcado.disponibilidade.repository.FeriadoRepository;
import br.ufpa.dsai.estilomarcado.disponibilidade.repository.JornadaIntervaloRepository;
import jakarta.persistence.EntityManager;

/**
 * Regras de negocio de jornada semanal, folgas, feriados, afastamentos e
 * bloqueios de agenda.
 *
 * <p>Concentra a autorizacao por perfil, filial e proprietario, as validacoes
 * de sobreposicao e a recusa de alteracoes que invalidariam atendimentos
 * futuros ativos.</p>
 */
@Service
public class DisponibilidadeService {

    private static final int MAX_INTERVALOS_DIA = 4;

    private final JornadaIntervaloRepository jornadas;
    private final ExcecaoJornadaRepository excecoes;
    private final AfastamentoRepository afastamentos;
    private final FeriadoRepository feriados;
    private final BloqueioAgendaRepository bloqueios;
    private final AtendimentoRepository atendimentos;
    private final ProfissionalRepository profissionais;
    private final UnidadeRepository unidades;
    private final CalculadoraJanelas calculadora;
    private final UsuarioAtual usuarioAtual;
    private final AuditoriaService auditoria;
    private final EntityManager entityManager;

    private void travarProfissional(Profissional profissional) {
        profissionais.bloquear(profissional.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("profissional nao encontrado"));
        entityManager.refresh(profissional);
    }

    private void travarUnidade(Unidade unidade) {
        for (Profissional profissional : profissionais.findByUnidadeIdOrderByIdAsc(unidade.getId())) {
            travarProfissional(profissional);
        }
    }

    public DisponibilidadeService(JornadaIntervaloRepository jornadas,
                                  ExcecaoJornadaRepository excecoes,
                                  AfastamentoRepository afastamentos,
                                  FeriadoRepository feriados,
                                  BloqueioAgendaRepository bloqueios,
                                  AtendimentoRepository atendimentos,
                                  ProfissionalRepository profissionais,
                                  UnidadeRepository unidades,
                                  CalculadoraJanelas calculadora,
                                  UsuarioAtual usuarioAtual,
                                  AuditoriaService auditoria, EntityManager entityManager) {
        this.jornadas = jornadas;
        this.excecoes = excecoes;
        this.afastamentos = afastamentos;
        this.feriados = feriados;
        this.bloqueios = bloqueios;
        this.atendimentos = atendimentos;
        this.profissionais = profissionais;
        this.unidades = unidades;
        this.calculadora = calculadora;
        this.usuarioAtual = usuarioAtual;
        this.auditoria = auditoria;
        this.entityManager = entityManager;
    }

    // ------------------------------------------------------------------
    // Jornada semanal
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public JornadaResponse consultarJornada(Long unidadeId, Long profissionalId) {
        return montarJornada(exigirLeitura(unidadeId, profissionalId));
    }

    @Transactional
    public JornadaResponse atualizarJornada(Long unidadeId, Long profissionalId,
                                            JornadaRequest request, String origem) {
        return salvarJornada(exigirEscrita(unidadeId, profissionalId), request, origem);
    }

    @Transactional(readOnly = true)
    public JornadaResponse consultarMinhaJornada() {
        return montarJornada(profissionalDaSessao());
    }

    @Transactional
    public JornadaResponse atualizarMinhaJornada(JornadaRequest request, String origem) {
        return salvarJornada(profissionalDaSessao(), request, origem);
    }

    @Transactional(readOnly = true)
    public List<JanelaResponse> consultarJanelas(Long unidadeId, Long profissionalId, LocalDate data) {
        Profissional profissional = exigirLeitura(unidadeId, profissionalId);
        return janelas(profissional, data);
    }

    @Transactional(readOnly = true)
    public List<JanelaResponse> consultarMinhasJanelas(LocalDate data) {
        return janelas(profissionalDaSessao(), data);
    }

    private List<JanelaResponse> janelas(Profissional profissional, LocalDate data) {
        return calculadora.calcular(profissional, data).stream().map(JanelaResponse::from).toList();
    }

    private JornadaResponse montarJornada(Profissional profissional) {
        List<JornadaIntervaloResponse> intervalos = jornadas
                .findByProfissionalIdOrderByDiaSemanaAscHoraInicioAsc(profissional.getId()).stream()
                .map(JornadaIntervaloResponse::from)
                .toList();
        return new JornadaResponse(profissional.getId(), profissional.getUnidade().getId(), intervalos);
    }

    private JornadaResponse salvarJornada(Profissional profissional, JornadaRequest request,
                                         String origem) {
        travarProfissional(profissional);
        List<JornadaIntervaloRequest> pedidos = request.intervalosSeguros();
        validarIntervalos(pedidos);

        Map<Integer, List<JanelaTrabalho>> porDia = agruparPorDia(pedidos);
        LocalDateTime agora = agora(profissional.getUnidade());
        List<ItemConflito> conflitos = new ArrayList<>();
        for (Atendimento atendimento : atendimentos
                .findByProfissionalIdAndInicioGreaterThanEqualOrderByInicioAsc(profissional.getId(), agora)) {
            if (atendimento.getStatus() == AtendimentoStatus.CANCELADO) {
                continue;
            }
            LocalDate data = atendimento.getInicio().toLocalDate();
            if (excecoes.findByProfissionalIdAndData(profissional.getId(), data).isPresent()) {
                continue;
            }
            List<JanelaTrabalho> janelasDoDia =
                    porDia.getOrDefault(atendimento.getInicio().getDayOfWeek().getValue(), List.of());
            if (!cabe(atendimento, janelasDoDia)) {
                conflitos.add(ItemConflito.from(atendimento));
            }
        }
        recusarSeHouverConflito(conflitos, "a nova jornada deixaria atendimentos futuros fora das janelas");

        jornadas.deleteByProfissionalId(profissional.getId());
        jornadas.flush();
        pedidos.forEach(pedido -> jornadas.save(new JornadaIntervalo(
                profissional, pedido.diaSemana(), pedido.horaInicio(), pedido.horaFim())));
        auditar("JORNADA_ALTERADA", origem, "profissional=" + profissional.getId());
        return montarJornada(profissional);
    }

    private Map<Integer, List<JanelaTrabalho>> agruparPorDia(List<JornadaIntervaloRequest> pedidos) {
        Map<Integer, List<JanelaTrabalho>> porDia = new LinkedHashMap<>();
        pedidos.forEach(pedido -> porDia
                .computeIfAbsent(pedido.diaSemana(), chave -> new ArrayList<>())
                .add(new JanelaTrabalho(pedido.horaInicio(), pedido.horaFim())));
        return porDia;
    }

    private void validarIntervalos(List<JornadaIntervaloRequest> intervaloRequests) {
        Map<Integer, List<JornadaIntervaloRequest>> porDia = new LinkedHashMap<>();
        intervaloRequests.forEach(intervalo -> porDia
                .computeIfAbsent(intervalo.diaSemana(), chave -> new ArrayList<>())
                .add(intervalo));

        porDia.forEach((dia, doDia) -> {
            if (doDia.size() > MAX_INTERVALOS_DIA) {
                throw new IllegalArgumentException("um dia pode ter no maximo " + MAX_INTERVALOS_DIA + " intervalos");
            }
            List<JornadaIntervaloRequest> ordenados = new ArrayList<>(doDia);
            ordenados.sort((a, b) -> a.horaInicio().compareTo(b.horaInicio()));
            for (int i = 0; i < ordenados.size(); i++) {
                JornadaIntervaloRequest atual = ordenados.get(i);
                if (!atual.horaInicio().isBefore(atual.horaFim())) {
                    throw new IllegalArgumentException("horaInicio deve ser anterior a horaFim");
                }
                if (i > 0 && ordenados.get(i - 1).horaFim().isAfter(atual.horaInicio())) {
                    throw new ConflitoException("intervalos da jornada nao podem se sobrepor");
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // Excecoes de data (folgas e jornadas especiais)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ExcecaoResponse> listarExcecoes(Long unidadeId, Long profissionalId,
                                                LocalDate de, LocalDate ate) {
        return listarExcecoesDe(exigirLeitura(unidadeId, profissionalId), de, ate);
    }

    @Transactional(readOnly = true)
    public List<ExcecaoResponse> listarMinhasExcecoes(LocalDate de, LocalDate ate) {
        return listarExcecoesDe(profissionalDaSessao(), de, ate);
    }

    @Transactional
    public ExcecaoResponse criarExcecao(Long unidadeId, Long profissionalId,
                                        ExcecaoRequest request, String origem) {
        return salvarExcecao(exigirEscrita(unidadeId, profissionalId), request, null, origem);
    }

    @Transactional
    public ExcecaoResponse atualizarExcecao(Long unidadeId, Long profissionalId, Long id,
                                            ExcecaoRequest request, String origem) {
        return salvarExcecao(exigirEscrita(unidadeId, profissionalId), request, id, origem);
    }

    @Transactional
    public ExcecaoResponse criarMinhaExcecao(ExcecaoRequest request, String origem) {
        return salvarExcecao(profissionalDaSessao(), request, null, origem);
    }

    @Transactional
    public ExcecaoResponse atualizarMinhaExcecao(Long id, ExcecaoRequest request, String origem) {
        return salvarExcecao(profissionalDaSessao(), request, id, origem);
    }

    @Transactional
    public void removerExcecao(Long unidadeId, Long profissionalId, Long id, String origem) {
        Profissional profissional = exigirEscrita(unidadeId, profissionalId);
        travarProfissional(profissional);
        ExcecaoJornada excecao = buscarExcecao(profissional, id);
        excecoes.delete(excecao);
        auditar("EXCECAO_JORNADA_REMOVIDA", origem, "profissional=" + profissional.getId());
    }

    @Transactional
    public void removerMinhaExcecao(Long id, String origem) {
        Profissional profissional = profissionalDaSessao();
        travarProfissional(profissional);
        excecoes.delete(buscarExcecao(profissional, id));
        auditar("EXCECAO_JORNADA_REMOVIDA", origem, "profissional=" + profissional.getId());
    }

    private List<ExcecaoResponse> listarExcecoesDe(Profissional profissional, LocalDate de, LocalDate ate) {
        return excecoes.findByProfissionalIdAndDataBetweenOrderByDataAsc(profissional.getId(), de, ate)
                .stream().map(ExcecaoResponse::from).toList();
    }

    private ExcecaoResponse salvarExcecao(Profissional profissional, ExcecaoRequest request,
                                          Long id, String origem) {
        travarProfissional(profissional);
        validarHorarios(request.intervalosSeguros(), request.tipo());
        boolean duplicada = id == null
                ? excecoes.existsByProfissionalIdAndData(profissional.getId(), request.data())
                : excecoes.existsByProfissionalIdAndDataAndIdNot(profissional.getId(), request.data(), id);
        if (duplicada) {
            throw new ConflitoException("ja existe uma excecao de jornada para esta data");
        }

        ExcecaoJornada excecao = id == null
                ? new ExcecaoJornada(profissional, request.data(), request.tipo())
                : buscarExcecao(profissional, id);
        excecao.setData(request.data());
        excecao.setTipo(request.tipo());
        excecao.setMotivo(opcional(request.motivo()));
        if (request.tipo() == TipoExcecaoJornada.FOLGA) {
            excecao.substituirIntervalos(List.of());
        } else {
            excecao.substituirIntervalos(request.intervalosSeguros().stream()
                    .map(h -> new ExcecaoJornadaIntervalo(h.horaInicio(), h.horaFim()))
                    .toList());
        }

        List<JanelaTrabalho> janelas = request.tipo() == TipoExcecaoJornada.JORNADA_ESPECIAL
                ? request.intervalosSeguros().stream()
                    .map(h -> new JanelaTrabalho(h.horaInicio(), h.horaFim())).toList()
                : List.of();
        List<ItemConflito> conflitos = conflitosDoDia(profissional, request.data(), janelas).stream()
                .map(ItemConflito::from).toList();
        recusarSeHouverConflito(conflitos,
                "a excecao deixaria atendimentos futuros fora das janelas");

        ExcecaoJornada salva = excecoes.save(excecao);
        auditar("EXCECAO_JORNADA_SALVA", origem, "profissional=" + profissional.getId());
        return ExcecaoResponse.from(salva);
    }

    private ExcecaoJornada buscarExcecao(Profissional profissional, Long id) {
        return excecoes.findById(id)
                .filter(excecao -> excecao.getProfissional().getId().equals(profissional.getId()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("excecao de jornada nao encontrada"));
    }

    // ------------------------------------------------------------------
    // Afastamentos
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AfastamentoResponse> listarAfastamentos(Long unidadeId, Long profissionalId,
                                                        LocalDate de, LocalDate ate) {
        return listarAfastamentosDe(exigirLeitura(unidadeId, profissionalId), de, ate);
    }

    @Transactional(readOnly = true)
    public List<AfastamentoResponse> listarMeusAfastamentos(LocalDate de, LocalDate ate) {
        return listarAfastamentosDe(profissionalDaSessao(), de, ate);
    }

    @Transactional
    public AfastamentoResponse criarAfastamento(Long unidadeId, Long profissionalId,
                                                AfastamentoRequest request, String origem) {
        return salvarAfastamento(exigirEscrita(unidadeId, profissionalId), request, null, origem);
    }

    @Transactional
    public AfastamentoResponse atualizarAfastamento(Long unidadeId, Long profissionalId, Long id,
                                                    AfastamentoRequest request, String origem) {
        return salvarAfastamento(exigirEscrita(unidadeId, profissionalId), request, id, origem);
    }

    @Transactional
    public AfastamentoResponse criarMeuAfastamento(AfastamentoRequest request, String origem) {
        return salvarAfastamento(profissionalDaSessao(), request, null, origem);
    }

    @Transactional
    public AfastamentoResponse atualizarMeuAfastamento(Long id, AfastamentoRequest request,
                                                       String origem) {
        return salvarAfastamento(profissionalDaSessao(), request, id, origem);
    }

    @Transactional
    public void removerAfastamento(Long unidadeId, Long profissionalId, Long id, String origem) {
        Profissional profissional = exigirEscrita(unidadeId, profissionalId);
        travarProfissional(profissional);
        afastamentos.delete(buscarAfastamento(profissional, id));
        auditar("AFASTAMENTO_REMOVIDO", origem, "profissional=" + profissional.getId());
    }

    @Transactional
    public void removerMeuAfastamento(Long id, String origem) {
        Profissional profissional = profissionalDaSessao();
        travarProfissional(profissional);
        afastamentos.delete(buscarAfastamento(profissional, id));
        auditar("AFASTAMENTO_REMOVIDO", origem, "profissional=" + profissional.getId());
    }

    private List<AfastamentoResponse> listarAfastamentosDe(Profissional profissional,
                                                           LocalDate de, LocalDate ate) {
        return afastamentos
                .findByProfissionalIdAndDataFimGreaterThanEqualAndDataInicioLessThanEqualOrderByDataInicioAsc(
                        profissional.getId(), de, ate)
                .stream().map(AfastamentoResponse::from).toList();
    }

    private AfastamentoResponse salvarAfastamento(Profissional profissional, AfastamentoRequest request,
                                                  Long id, String origem) {
        travarProfissional(profissional);
        if (request.dataInicio().isAfter(request.dataFim())) {
            throw new IllegalArgumentException("dataInicio deve ser anterior ou igual a dataFim");
        }
        boolean sobreposto = afastamentos
                .findByProfissionalIdAndDataFimGreaterThanEqualAndDataInicioLessThanEqualOrderByDataInicioAsc(
                        profissional.getId(), request.dataInicio(), request.dataFim()).stream()
                .anyMatch(afastamento -> id == null || !afastamento.getId().equals(id));
        if (sobreposto) {
            throw new ConflitoException("ja existe um afastamento neste periodo");
        }

        List<ItemConflito> conflitos = conflitosNoPeriodo(
                profissional, request.dataInicio(), request.dataFim()).stream()
                .map(ItemConflito::from).toList();
        recusarSeHouverConflito(conflitos,
                "o afastamento deixaria atendimentos futuros fora das janelas");

        Afastamento afastamento = id == null
                ? new Afastamento(profissional, request.dataInicio(), request.dataFim(), request.tipo())
                : buscarAfastamento(profissional, id);
        afastamento.setDataInicio(request.dataInicio());
        afastamento.setDataFim(request.dataFim());
        afastamento.setTipo(request.tipo());
        afastamento.setDescricao(opcional(request.descricao()));

        Afastamento salvo = afastamentos.save(afastamento);
        auditar("AFASTAMENTO_SALVO", origem, "profissional=" + profissional.getId());
        return AfastamentoResponse.from(salvo);
    }

    private Afastamento buscarAfastamento(Profissional profissional, Long id) {
        return afastamentos.findById(id)
                .filter(afastamento -> afastamento.getProfissional().getId().equals(profissional.getId()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("afastamento nao encontrado"));
    }

    // ------------------------------------------------------------------
    // Feriados
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<FeriadoResponse> listarFeriados(Long unidadeId, LocalDate de, LocalDate ate) {
        exigirInterno(unidadeId);
        return feriados.findByUnidadeIdAndDataBetweenOrderByDataAsc(unidadeId, de, ate).stream()
                .map(FeriadoResponse::from).toList();
    }

    @Transactional
    public FeriadoResponse criarFeriado(Long unidadeId, FeriadoRequest request, String origem) {
        return salvarFeriado(exigirAdministradorUnidadeAtiva(unidadeId), request, null, origem);
    }

    @Transactional
    public FeriadoResponse atualizarFeriado(Long unidadeId, Long id, FeriadoRequest request,
                                            String origem) {
        return salvarFeriado(exigirAdministradorUnidadeAtiva(unidadeId), request, id, origem);
    }

    @Transactional
    public void removerFeriado(Long unidadeId, Long id, String origem) {
        Unidade unidade = exigirAdministradorUnidadeAtiva(unidadeId);
        travarUnidade(unidade);
        Feriado feriado = buscarFeriado(unidade, id);
        feriados.delete(feriado);
        auditar("FERIADO_REMOVIDO", origem, "unidade=" + unidadeId);
    }

    private FeriadoResponse salvarFeriado(Unidade unidade, FeriadoRequest request, Long id,
                                          String origem) {
        travarUnidade(unidade);
        boolean duplicado = id == null
                ? feriados.existsByUnidadeIdAndData(unidade.getId(), request.data())
                : feriados.existsByUnidadeIdAndDataAndIdNot(unidade.getId(), request.data(), id);
        if (duplicado) {
            throw new ConflitoException("ja existe um feriado nesta data");
        }

        List<ItemConflito> conflitos = atendimentosFuturosDaUnidade(unidade, request.data()).stream()
                .map(ItemConflito::from).toList();
        recusarSeHouverConflito(conflitos,
                "o feriado deixaria atendimentos futuros fora das janelas");

        Feriado feriado = id == null
                ? new Feriado(unidade, request.data(), request.nome().trim())
                : buscarFeriado(unidade, id);
        feriado.setData(request.data());
        feriado.setNome(request.nome().trim());

        Feriado salvo = feriados.save(feriado);
        auditar("FERIADO_SALVO", origem, "unidade=" + unidade.getId());
        return FeriadoResponse.from(salvo);
    }

    private Feriado buscarFeriado(Unidade unidade, Long id) {
        return feriados.findById(id)
                .filter(feriado -> feriado.getUnidade().getId().equals(unidade.getId()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("feriado nao encontrado"));
    }

    // ------------------------------------------------------------------
    // Bloqueios de agenda
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<BloqueioResponse> listarBloqueios(Long unidadeId, LocalDate de, LocalDate ate,
                                                  Long profissionalId) {
        exigirInterno(unidadeId);
        return bloqueios.findByUnidadeIdAndDataBetweenOrderByDataAscHoraInicioAsc(unidadeId, de, ate)
                .stream()
                .filter(bloqueio -> profissionalId == null
                        || bloqueio.getProfissional() == null
                        || bloqueio.getProfissional().getId().equals(profissionalId))
                .map(BloqueioResponse::from).toList();
    }

    @Transactional
    public BloqueioResponse criarBloqueio(Long unidadeId, BloqueioRequest request, String origem) {
        Unidade unidade = exigirAdministradorUnidadeAtiva(unidadeId);
        Profissional profissional = request.profissionalId() == null
                ? null
                : buscarProfissional(unidadeId, request.profissionalId());
        return salvarBloqueio(unidade, profissional, request, null, origem);
    }

    @Transactional
    public BloqueioResponse atualizarBloqueio(Long unidadeId, Long id, BloqueioRequest request,
                                              String origem) {
        Unidade unidade = exigirAdministradorUnidadeAtiva(unidadeId);
        BloqueioAgenda existente = buscarBloqueio(unidade, id);
        Profissional profissional = request.profissionalId() == null
                ? null
                : buscarProfissional(unidadeId, request.profissionalId());
        return salvarBloqueio(unidade, profissional, request, existente, origem);
    }

    @Transactional
    public BloqueioResponse criarMeuBloqueio(BloqueioRequest request, String origem) {
        Profissional profissional = profissionalDaSessao();
        exigirMesmoProfissional(request.profissionalId(), profissional);
        return salvarBloqueio(profissional.getUnidade(), profissional, request, null, origem);
    }

    @Transactional
    public BloqueioResponse atualizarMeuBloqueio(Long id, BloqueioRequest request, String origem) {
        Profissional profissional = profissionalDaSessao();
        exigirMesmoProfissional(request.profissionalId(), profissional);
        BloqueioAgenda existente = buscarBloqueio(profissional.getUnidade(), id);
        exigirBloqueioDoProfissional(existente, profissional);
        return salvarBloqueio(profissional.getUnidade(), profissional, request, existente, origem);
    }

    @Transactional
    public void removerBloqueio(Long unidadeId, Long id, String origem) {
        Unidade unidade = exigirAdministradorUnidadeAtiva(unidadeId);
        travarUnidade(unidade);
        bloqueios.delete(buscarBloqueio(unidade, id));
        auditar("BLOQUEIO_REMOVIDO", origem, "unidade=" + unidadeId);
    }

    @Transactional
    public void removerMeuBloqueio(Long id, String origem) {
        Profissional profissional = profissionalDaSessao();
        travarProfissional(profissional);
        BloqueioAgenda bloqueio = buscarBloqueio(profissional.getUnidade(), id);
        exigirBloqueioDoProfissional(bloqueio, profissional);
        bloqueios.delete(bloqueio);
        auditar("BLOQUEIO_REMOVIDO", origem, "profissional=" + profissional.getId());
    }

    private BloqueioResponse salvarBloqueio(Unidade unidade, Profissional profissional,
                                            BloqueioRequest request, BloqueioAgenda existente,
                                            String origem) {
        if (profissional == null) travarUnidade(unidade);
        else travarProfissional(profissional);
        validarBloqueio(request);
        Long idIgnorado = existente == null ? null : existente.getId();
        List<BloqueioAgenda> mesmaAbrangencia = profissional == null
                ? bloqueios.findByUnidadeIdAndDataAndProfissionalIsNull(unidade.getId(), request.data())
                : bloqueios.findByProfissionalIdAndData(profissional.getId(), request.data());
        boolean sobreposto = mesmaAbrangencia.stream()
                .filter(bloqueio -> idIgnorado == null || !bloqueio.getId().equals(idIgnorado))
                .anyMatch(bloqueio -> sobrepoe(bloqueio, request));
        if (sobreposto) {
            throw new ConflitoException("ja existe um bloqueio nesta janela");
        }

        List<Atendimento> candidatos = profissional == null
                ? atendimentosFuturosDaUnidade(unidade, request.data())
                : atendimentosFuturosDoDia(profissional, request.data());
        List<ItemConflito> conflitos = candidatos.stream()
                .filter(atendimento -> bloqueioConflita(request, atendimento))
                .map(ItemConflito::from).toList();
        recusarSeHouverConflito(conflitos,
                "o bloqueio deixaria atendimentos futuros fora das janelas");

        BloqueioAgenda bloqueio = existente == null
                ? new BloqueioAgenda(unidade, profissional, request.data(), request.diaInteiro(),
                        request.diaInteiro() ? null : request.horaInicio(),
                        request.diaInteiro() ? null : request.horaFim(),
                        opcional(request.motivo()), usuarioAtual.get().id())
                : existente;
        bloqueio.setData(request.data());
        bloqueio.setDiaInteiro(request.diaInteiro());
        bloqueio.setHoraInicio(request.diaInteiro() ? null : request.horaInicio());
        bloqueio.setHoraFim(request.diaInteiro() ? null : request.horaFim());
        bloqueio.setMotivo(opcional(request.motivo()));
        if (existente != null) {
            reposicionarProfissional(bloqueio, unidade, profissional);
        }

        BloqueioAgenda salvo = bloqueios.save(bloqueio);
        auditar("BLOQUEIO_SALVO", origem, "unidade=" + unidade.getId());
        return BloqueioResponse.from(salvo);
    }

    private void reposicionarProfissional(BloqueioAgenda bloqueio, Unidade unidade,
                                          Profissional profissional) {
        // O vinculo e definido na criacao; a atualizacao nao troca a abrangencia
        // entre filial e profissional para preservar o escopo validado.
        if (bloqueio.getProfissional() == null && profissional != null
                || bloqueio.getProfissional() != null && profissional == null) {
            throw new IllegalArgumentException("a abrangencia do bloqueio nao pode ser alterada");
        }
    }

    private BloqueioAgenda buscarBloqueio(Unidade unidade, Long id) {
        return bloqueios.findById(id)
                .filter(bloqueio -> bloqueio.getUnidade().getId().equals(unidade.getId()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("bloqueio nao encontrado"));
    }

    private void exigirBloqueioDoProfissional(BloqueioAgenda bloqueio, Profissional profissional) {
        if (bloqueio.getProfissional() == null
                || !bloqueio.getProfissional().getId().equals(profissional.getId())) {
            throw new AccessDeniedException("acesso negado");
        }
    }

    private void exigirMesmoProfissional(Long profissionalId, Profissional profissional) {
        if (profissionalId != null && !profissionalId.equals(profissional.getId())) {
            throw new AccessDeniedException("acesso negado");
        }
    }

    private void validarBloqueio(BloqueioRequest request) {
        if (request.diaInteiro()) {
            if (request.horaInicio() != null || request.horaFim() != null) {
                throw new IllegalArgumentException("bloqueio de dia inteiro nao aceita horarios");
            }
            return;
        }
        if (request.horaInicio() == null || request.horaFim() == null) {
            throw new IllegalArgumentException("informe horaInicio e horaFim ou marque diaInteiro");
        }
        if (!request.horaInicio().isBefore(request.horaFim())) {
            throw new IllegalArgumentException("horaInicio deve ser anterior a horaFim");
        }
    }

    private boolean sobrepoe(BloqueioAgenda existente, BloqueioRequest novo) {
        if (existente.isDiaInteiro() || novo.diaInteiro()) {
            return true;
        }
        return novo.horaInicio().isBefore(existente.getHoraFim())
                && existente.getHoraInicio().isBefore(novo.horaFim());
    }

    private boolean bloqueioConflita(BloqueioRequest bloqueio, Atendimento atendimento) {
        if (bloqueio.diaInteiro()) {
            return true;
        }
        LocalTime inicio = atendimento.getInicio().toLocalTime();
        LocalTime fim = inicio.plusMinutes(atendimento.getDuracaoMinutos());
        return inicio.isBefore(bloqueio.horaFim()) && bloqueio.horaInicio().isBefore(fim);
    }

    // ------------------------------------------------------------------
    // Composicao de janelas e conflitos
    // ------------------------------------------------------------------

    private List<Atendimento> conflitosDoDia(Profissional profissional, LocalDate data,
                                             List<JanelaTrabalho> janelas) {
        return atendimentosFuturosDoDia(profissional, data).stream()
                .filter(atendimento -> !cabe(atendimento, janelas))
                .toList();
    }

    private List<Atendimento> conflitosNoPeriodo(Profissional profissional, LocalDate inicio,
                                                 LocalDate fim) {
        return atendimentos.findByProfissionalIdAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioAsc(
                        profissional.getId(), inicio.atStartOfDay(), fim.plusDays(1).atStartOfDay())
                .stream()
                .filter(this::ativo)
                .filter(atendimento -> !atendimento.getInicio().isBefore(agora(profissional.getUnidade())))
                .toList();
    }

    private List<Atendimento> atendimentosFuturosDoDia(Profissional profissional, LocalDate data) {
        return atendimentos.findByProfissionalIdAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioAsc(
                        profissional.getId(), data.atStartOfDay(), data.plusDays(1).atStartOfDay())
                .stream()
                .filter(this::ativo)
                .filter(atendimento -> !atendimento.getInicio().isBefore(agora(profissional.getUnidade())))
                .toList();
    }

    private List<Atendimento> atendimentosFuturosDaUnidade(Unidade unidade, LocalDate data) {
        return atendimentos
                .findByProfissionalUnidadeIdAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioAsc(
                        unidade.getId(), data.atStartOfDay(), data.plusDays(1).atStartOfDay())
                .stream()
                .filter(this::ativo)
                .filter(atendimento -> !atendimento.getInicio().isBefore(agora(unidade)))
                .toList();
    }

    private boolean ativo(Atendimento atendimento) {
        return atendimento.getStatus() != AtendimentoStatus.CANCELADO;
    }

    private boolean cabe(Atendimento atendimento, List<JanelaTrabalho> janelas) {
        LocalTime inicio = atendimento.getInicio().toLocalTime();
        LocalTime fim = inicio.plusMinutes(atendimento.getDuracaoMinutos());
        return janelas.stream().anyMatch(janela -> janela.contem(inicio, fim));
    }

    private void validarHorarios(List<HorarioRequest> horarios, TipoExcecaoJornada tipo) {
        if (tipo == TipoExcecaoJornada.FOLGA) {
            if (!horarios.isEmpty()) {
                throw new IllegalArgumentException("folga nao aceita intervalos");
            }
            return;
        }
        if (horarios.isEmpty()) {
            throw new IllegalArgumentException("jornada especial exige ao menos um intervalo");
        }
        List<HorarioRequest> ordenados = new ArrayList<>(horarios);
        ordenados.sort((a, b) -> a.horaInicio().compareTo(b.horaInicio()));
        for (int i = 0; i < ordenados.size(); i++) {
            HorarioRequest atual = ordenados.get(i);
            if (!atual.horaInicio().isBefore(atual.horaFim())) {
                throw new IllegalArgumentException("horaInicio deve ser anterior a horaFim");
            }
            if (i > 0 && ordenados.get(i - 1).horaFim().isAfter(atual.horaInicio())) {
                throw new ConflitoException("intervalos da jornada especial nao podem se sobrepor");
            }
        }
    }

    private void recusarSeHouverConflito(List<ItemConflito> conflitos, String motivo) {
        if (!conflitos.isEmpty()) {
            throw new ConflitoAtendimentoException(
                    motivo + " (" + conflitos.size() + " atendimento(s))", conflitos);
        }
    }

    private LocalDateTime agora(Unidade unidade) {
        return LocalDateTime.now(ZoneId.of(unidade.getFusoHorario()));
    }

    private String opcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    // ------------------------------------------------------------------
    // Autorizacao
    // ------------------------------------------------------------------

    private Profissional exigirLeitura(Long unidadeId, Long profissionalId) {
        Profissional profissional = buscarProfissional(unidadeId, profissionalId);
        UsuarioPrincipal autor = usuarioAtual.get();
        boolean interno = (autor.perfil() == PerfilUsuario.ADMINISTRADOR
                || autor.perfil() == PerfilUsuario.RECEPCAO)
                && unidadeId.equals(autor.unidadeId());
        boolean proprio = autor.perfil() == PerfilUsuario.PROFISSIONAL
                && profissional.getId().equals(autor.profissionalId());
        if (!interno && !proprio) {
            throw new AccessDeniedException("acesso negado");
        }
        return profissional;
    }

    private Profissional exigirEscrita(Long unidadeId, Long profissionalId) {
        Profissional profissional = buscarProfissional(unidadeId, profissionalId);
        UsuarioPrincipal autor = usuarioAtual.get();
        if (autor.perfil() == PerfilUsuario.ADMINISTRADOR && unidadeId.equals(autor.unidadeId())) {
            exigirUnidadeAtiva(profissional.getUnidade());
            return profissional;
        }
        if (autor.perfil() == PerfilUsuario.PROFISSIONAL
                && profissional.getId().equals(autor.profissionalId())) {
            exigirProfissionalAtivo(profissional);
            return profissional;
        }
        throw new AccessDeniedException("acesso negado");
    }

    private Profissional profissionalDaSessao() {
        UsuarioPrincipal autor = usuarioAtual.get();
        if (autor.perfil() != PerfilUsuario.PROFISSIONAL || autor.profissionalId() == null) {
            throw new AccessDeniedException("acesso negado");
        }
        Profissional profissional = profissionais.findById(autor.profissionalId())
                .orElseThrow(() -> new AccessDeniedException("acesso negado"));
        exigirProfissionalAtivo(profissional);
        return profissional;
    }

    private Unidade exigirAdministradorUnidadeAtiva(Long unidadeId) {
        usuarioAtual.exigirAdministradorDaUnidade(unidadeId);
        Unidade unidade = buscarUnidade(unidadeId);
        exigirUnidadeAtiva(unidade);
        return unidade;
    }

    private Unidade exigirInterno(Long unidadeId) {
        UsuarioPrincipal autor = usuarioAtual.get();
        if (autor.unidadeId() == null || !autor.unidadeId().equals(unidadeId)) {
            throw new AccessDeniedException("acesso negado");
        }
        return buscarUnidade(unidadeId);
    }

    private void exigirUnidadeAtiva(Unidade unidade) {
        if (!unidade.isAtiva()) {
            throw new AccessDeniedException("filial inativa");
        }
    }

    private void exigirProfissionalAtivo(Profissional profissional) {
        if (!profissional.isAtivo() || !profissional.getUnidade().isAtiva()) {
            throw new AccessDeniedException("acesso negado");
        }
    }

    private Profissional buscarProfissional(Long unidadeId, Long profissionalId) {
        Profissional profissional = profissionais.findById(profissionalId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("profissional nao encontrado"));
        if (!profissional.getUnidade().getId().equals(unidadeId)) {
            throw new RecursoNaoEncontradoException("profissional nao encontrado");
        }
        return profissional;
    }

    private Unidade buscarUnidade(Long unidadeId) {
        return unidades.findById(unidadeId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("filial nao encontrada"));
    }

    private void auditar(String tipo, String origem, String detalhes) {
        auditoria.registrar(usuarioAtual.get().id(), tipo, true, origem, detalhes);
    }
}
