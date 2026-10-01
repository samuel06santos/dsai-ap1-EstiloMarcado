package br.ufpa.dsai.estilomarcado.agendamento;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.ufpa.dsai.estilomarcado.agendamento.model.Atendimento;
import br.ufpa.dsai.estilomarcado.agendamento.model.AtendimentoStatus;
import br.ufpa.dsai.estilomarcado.agendamento.model.Cliente;
import br.ufpa.dsai.estilomarcado.agendamento.repository.AtendimentoRepository;
import br.ufpa.dsai.estilomarcado.agendamento.repository.ClienteRepository;
import br.ufpa.dsai.estilomarcado.autenticacao.model.EstadoConta;
import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;
import br.ufpa.dsai.estilomarcado.autenticacao.model.Usuario;
import br.ufpa.dsai.estilomarcado.autenticacao.repository.UsuarioRepository;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioAtual;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioPrincipal;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.RecursoNaoEncontradoException;
import br.ufpa.dsai.estilomarcado.catalogo.model.Profissional;
import br.ufpa.dsai.estilomarcado.catalogo.model.Servico;
import br.ufpa.dsai.estilomarcado.catalogo.repository.ProfissionalRepository;
import br.ufpa.dsai.estilomarcado.catalogo.repository.ServicoRepository;
import br.ufpa.dsai.estilomarcado.disponibilidade.service.CalculadoraHorarios;
import br.ufpa.dsai.estilomarcado.disponibilidade.service.CalculadoraHorarios.Ocupacao;
import br.ufpa.dsai.estilomarcado.disponibilidade.service.CalculadoraJanelas;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.ConsultaHorariosResponse;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.HorarioDisponivelResponse;
import br.ufpa.dsai.estilomarcado.operacao.NotificacaoService;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.ObjectMapper;

@Service
public class AgendamentoService {
    private final AtendimentoRepository atendimentos;
    private final ClienteRepository clientes;
    private final UsuarioRepository usuarios;
    private final ProfissionalRepository profissionais;
    private final ServicoRepository servicos;
    private final UsuarioAtual atual;
    private final CalculadoraJanelas janelas;
    private final CalculadoraHorarios horarios;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final EntityManager entityManager;
    private final ObjectMapper mapper;
    private final NotificacaoService notificacoes;

    public AgendamentoService(AtendimentoRepository atendimentos, ClienteRepository clientes,
            UsuarioRepository usuarios, ProfissionalRepository profissionais, ServicoRepository servicos,
            UsuarioAtual atual, CalculadoraJanelas janelas, CalculadoraHorarios horarios,
            JdbcTemplate jdbc, Clock clock, EntityManager entityManager, ObjectMapper mapper,
            NotificacaoService notificacoes) {
        this.atendimentos = atendimentos;
        this.clientes = clientes;
        this.usuarios = usuarios;
        this.profissionais = profissionais;
        this.servicos = servicos;
        this.atual = atual;
        this.janelas = janelas;
        this.horarios = horarios;
        this.jdbc = jdbc;
        this.clock = clock;
        this.entityManager = entityManager;
        this.mapper = mapper;
        this.notificacoes = notificacoes;
    }

    public record Criacao(Long servicoId, Long profissionalId, LocalDateTime inicio,
                          Long clienteId, String clienteNome, String clienteTelefone) {}
    public record Resposta(Long id, Long unidadeId, String unidadeNome, boolean unidadeAtiva, Long clienteId,
                           Long servicoId, String servicoNome, boolean servicoAtivo,
                           java.math.BigDecimal precoAcordado, Long profissionalId, String profissionalNome,
                           LocalDateTime inicio, LocalDateTime fim,
                           String fusoHorario, AtendimentoStatus status, Instant criadoEm,
                           Instant atualizadoEm, Instant canceladoEm, String motivoCancelamento) {}

    public record ClienteResumo(String nome, String telefoneContato) {}
    public record ResumoPainel(long proximosAtivos, long realizados, long cancelados) {}
    public record PainelResposta(Instant agora, ClienteResumo cliente, Resposta proximo,
                                 List<Resposta> proximos, List<Resposta> historico,
                                 ResumoPainel resumo) {}
    public record ClienteOpcao(Long id, String nome) {}
    public record FilialOpcao(Long id, String nome, boolean ativa) {}

    @Transactional
    public Resposta criar(Long unidadeId, Criacao pedido, String chaveTexto) {
        UsuarioPrincipal sessao = atual.get();
        exigirAtorDaUnidade(sessao, unidadeId);
        if (pedido == null || pedido.servicoId() == null || pedido.profissionalId() == null || pedido.inicio() == null) {
            throw new IllegalArgumentException("servicoId, profissionalId e inicio sao obrigatorios");
        }
        UUID chave;
        try { chave = UUID.fromString(chaveTexto); }
        catch (Exception ex) { throw new IllegalArgumentException("Idempotency-Key deve ser UUID"); }
        String hash = hash(unidadeId + "|" + mapper.writeValueAsString(pedido));
        // O lock da conta serializa a criacao do vinculo cliente e o uso da chave.
        Usuario autor = usuarios.bloquearPorId(sessao.id()).orElseThrow();
        if (autor.getEstado() != EstadoConta.ATIVA) throw new AccessDeniedException("conta inativa");
        jdbc.update("delete from agendamento_idempotencia where usuario_id=? and chave=? and expira_em < ?",
                autor.getId(), chave, java.sql.Timestamp.from(clock.instant()));
        int inseridos = jdbc.update("""
                insert into agendamento_idempotencia(usuario_id,chave,unidade_id,pedido_hash,expira_em)
                values (?,?,?,?,?) on conflict (usuario_id,chave) do nothing
                """, autor.getId(), chave, unidadeId, hash,
                java.sql.Timestamp.from(clock.instant().plusSeconds(86400)));
        if (inseridos == 0) {
            var anterior = jdbc.queryForList("""
                    select pedido_hash,unidade_id,resposta_json from agendamento_idempotencia
                    where usuario_id=? and chave=?
                    """, autor.getId(), chave).getFirst();
            if (!hash.equals(anterior.get("pedido_hash")) || !unidadeId.equals(anterior.get("unidade_id"))) {
                throw new AgendamentoConflitoException("CHAVE_IDEMPOTENCIA_REUTILIZADA", "chave reutilizada com outro pedido");
            }
            return mapper.readValue((String) anterior.get("resposta_json"), Resposta.class);
        }
        Profissional profissional = profissionais.bloquear(pedido.profissionalId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("profissional nao encontrado"));
        Servico servico = servicos.findById(pedido.servicoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("servico nao encontrado"));
        validarCatalogo(unidadeId, profissional, servico);
        validarHorario(profissional, pedido.inicio(), servico.getDuracaoMinutos(),
                servico.getIntervaloMinutos() == null ? 0 : servico.getIntervaloMinutos(), null);
        Cliente cliente = resolverCliente(sessao, autor, unidadeId, pedido);
        Atendimento atendimento = atendimentos.saveAndFlush(new Atendimento(profissional, servico, cliente,
                pedido.inicio(), AtendimentoStatus.AGENDADO));
        evento(atendimento, autor, "CRIACAO", null, null);
        Resposta resultado = resposta(atendimento, sessao);
        jdbc.update("update agendamento_idempotencia set atendimento_id=?, resposta_json=? where usuario_id=? and chave=?",
                atendimento.getId(), mapper.writeValueAsString(resultado), autor.getId(), chave);
        return resultado;
    }

    @Transactional
    public Resposta confirmar(Long id) {
        UsuarioPrincipal sessao = atual.get();
        Atendimento a = travarAtendimento(id);
        exigirEquipe(sessao, a.getProfissional().getUnidade().getId());
        exigirAntesInicio(a);
        if (a.getStatus() == AtendimentoStatus.CONFIRMADO) return resposta(a, sessao);
        exigirAtivoFuturo(a);
        AtendimentoStatus anterior = a.getStatus();
        a.setStatus(AtendimentoStatus.CONFIRMADO);
        evento(a, usuarios.getReferenceById(sessao.id()), "CONFIRMACAO", anterior, a.getInicio());
        atendimentos.flush();
        return resposta(a, sessao);
    }

    @Transactional
    public Resposta cancelar(Long id, String motivo) {
        UsuarioPrincipal sessao = atual.get();
        Atendimento a = travarAtendimento(id);
        exigirAcesso(sessao, a);
        exigirAntesInicio(a);
        if (a.getStatus() == AtendimentoStatus.CANCELADO) return resposta(a, sessao);
        if (motivo != null && motivo.length() > 500) throw new IllegalArgumentException("motivo excede 500 caracteres");
        exigirAtivoFuturo(a);
        AtendimentoStatus anterior = a.getStatus();
        a.cancelar(usuarios.getReferenceById(sessao.id()), motivo);
        evento(a, usuarios.getReferenceById(sessao.id()), "CANCELAMENTO", anterior, a.getInicio());
        atendimentos.flush();
        return resposta(a, sessao);
    }

    @Transactional
    public Resposta reagendar(Long id, LocalDateTime inicio) {
        UsuarioPrincipal sessao = atual.get();
        Atendimento a = travarAtendimento(id);
        exigirAcesso(sessao, a);
        exigirAtivoFuturo(a);
        if (inicio == null) throw new IllegalArgumentException("inicio obrigatorio");
        if (inicio.equals(a.getInicio())) return resposta(a, sessao);
        Profissional profissional = a.getProfissional();
        validarCatalogo(profissional.getUnidade().getId(), profissional, a.getServico());
        validarHorario(profissional, inicio, a.getDuracaoMinutos(), a.getIntervaloMinutos(), a.getId());
        AtendimentoStatus anterior = a.getStatus();
        LocalDateTime inicioAnterior = a.getInicio();
        a.setInicio(inicio);
        a.setStatus(AtendimentoStatus.AGENDADO);
        evento(a, usuarios.getReferenceById(sessao.id()), "REAGENDAMENTO", anterior, inicioAnterior);
        atendimentos.flush();
        return resposta(a, sessao);
    }

    @Transactional(readOnly = true)
    public Resposta detalhar(Long id) {
        UsuarioPrincipal sessao = atual.get();
        Atendimento a = atendimentos.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("agendamento nao encontrado"));
        exigirAcesso(sessao, a);
        return resposta(a, sessao);
    }

    @Transactional(readOnly = true)
    public ConsultaHorariosResponse horariosParaReagendar(Long id, LocalDate data) {
        UsuarioPrincipal sessao = atual.get();
        Atendimento a = atendimentos.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("agendamento nao encontrado"));
        exigirAcesso(sessao, a);
        exigirAtivoFuturo(a);
        Profissional p = a.getProfissional();
        validarCatalogo(p.getUnidade().getId(), p, a.getServico());
        ZoneId fuso = ZoneId.of(p.getUnidade().getFusoHorario());
        LocalDate hoje = clock.instant().atZone(fuso).toLocalDate();
        if (data == null || data.isBefore(hoje) || data.isAfter(hoje.plusDays(60))) {
            throw new IllegalArgumentException("data fora do horizonte de 60 dias");
        }
        List<Ocupacao> ocupacoes = atendimentos.findAllById(atendimentos.buscarIdsOcupacao(
                        List.of(p.getId()), data.atStartOfDay(), data.plusDays(1).atStartOfDay()))
                .stream().filter(item -> !item.getId().equals(id))
                .map(item -> new Ocupacao(item.getInicio(), item.getDuracaoMinutos(), item.getIntervaloMinutos()))
                .toList();
        List<HorarioDisponivelResponse> disponiveis = horarios.calcular(data, fuso, clock.instant(),
                        janelas.calcular(p, data), ocupacoes, a.getDuracaoMinutos(), a.getIntervaloMinutos())
                .stream().map(inicio -> new HorarioDisponivelResponse(p.getId(), inicio,
                        inicio.plusMinutes(a.getDuracaoMinutos()))).toList();
        return new ConsultaHorariosResponse(p.getUnidade().getId(), a.getServico().getId(), data,
                fuso.getId(), disponiveis);
    }

    @Transactional(readOnly = true)
    public List<Resposta> listar(Long unidadeId, LocalDate de, LocalDate ate, AtendimentoStatus status,
                                 Long profissionalId, Long filtroUnidadeId, Long filtroServicoId,
                                 int pagina, int tamanho) {
        UsuarioPrincipal sessao = atual.get();
        validarPeriodo(de, ate, pagina, tamanho);
        List<Atendimento> itens;
        PageRequest paginacao = PageRequest.of(pagina, tamanho);
        if (unidadeId == null) {
            if (sessao.perfil() != PerfilUsuario.CLIENTE) throw new AccessDeniedException("acesso negado");
            var cliente = clientes.findByUsuarioId(sessao.id());
            if (cliente.isEmpty()) return List.of();
            itens = atendimentos.listarCliente(cliente.get().getId(), de.atStartOfDay(),
                    ate.plusDays(1).atStartOfDay(), status, filtroUnidadeId, filtroServicoId, paginacao);
        } else {
            exigirEquipe(sessao, unidadeId);
            itens = atendimentos.listarUnidade(unidadeId, de.atStartOfDay(),
                    ate.plusDays(1).atStartOfDay(), status, profissionalId, paginacao);
        }
        return itens.stream().map(a -> resposta(a, sessao)).toList();
    }

    @Transactional(readOnly = true)
    public PainelResposta painel(int limiteProximos, int limiteHistorico) {
        UsuarioPrincipal sessao = atual.get();
        if (sessao.perfil() != PerfilUsuario.CLIENTE) {
            throw new AccessDeniedException("acesso negado");
        }
        if (limiteProximos < 1 || limiteProximos > 20 || limiteHistorico < 1 || limiteHistorico > 20) {
            throw new IllegalArgumentException("limites devem estar entre 1 e 20");
        }
        Instant agora = clock.instant();
        Usuario autor = usuarios.findById(sessao.id()).orElseThrow();
        ClienteResumo dados = new ClienteResumo(autor.getNome(), autor.getTelefoneContato());
        var cliente = clientes.findByUsuarioId(sessao.id());
        if (cliente.isEmpty()) {
            return new PainelResposta(agora, dados, null, List.of(), List.of(),
                    new ResumoPainel(0, 0, 0));
        }
        List<Atendimento> todos = atendimentos.buscarDoCliente(cliente.get().getId());
        List<ItemPainel> itens = todos.stream().map(a -> {
            Instant instante = a.getInicio()
                    .atZone(ZoneId.of(a.getFusoHorarioAgendamento())).toInstant();
            boolean cancelado = a.getStatus() == AtendimentoStatus.CANCELADO;
            boolean futuro = !cancelado && instante.isAfter(agora);
            return new ItemPainel(a, instante, futuro, cancelado);
        }).toList();
        List<ItemPainel> ativosFuturos = itens.stream().filter(ItemPainel::futuro).toList();
        List<Resposta> proximos = ativosFuturos.stream()
                .sorted(Comparator.comparing(ItemPainel::instante)
                        .thenComparing(item -> item.atendimento().getId()))
                .limit(limiteProximos)
                .map(item -> resposta(item.atendimento(), sessao))
                .toList();
        List<Resposta> historico = itens.stream()
                .filter(item -> item.cancelado() || !item.futuro())
                .sorted(Comparator.comparing(ItemPainel::instante).reversed()
                        .thenComparing(item -> item.atendimento().getId(), Comparator.reverseOrder()))
                .limit(limiteHistorico)
                .map(item -> resposta(item.atendimento(), sessao))
                .toList();
        long realizados = itens.stream().filter(item -> !item.cancelado() && !item.futuro()).count();
        long cancelados = itens.stream().filter(ItemPainel::cancelado).count();
        Resposta proximo = proximos.isEmpty() ? null : proximos.get(0);
        return new PainelResposta(agora, dados, proximo, proximos, historico,
                new ResumoPainel(ativosFuturos.size(), realizados, cancelados));
    }

    /**
     * Clientes ja atendidos pela filial, para o seletor da recepcao. A equipe
     * so enxerga clientes da propria filial.
     */
    @Transactional(readOnly = true)
    public List<ClienteOpcao> listarClientesDaUnidade(Long unidadeId) {
        exigirEquipe(atual.get(), unidadeId);
        return clientes.buscarDaUnidade(unidadeId).stream()
                .map(cliente -> new ClienteOpcao(cliente.getId(), cliente.getNome()))
                .toList();
    }

    /**
     * Filiais em que o cliente autenticado ja teve atendimento, para o seletor
     * da lista de espera.
     */
    @Transactional(readOnly = true)
    public List<FilialOpcao> minhasFiliais() {
        UsuarioPrincipal sessao = atual.get();
        if (sessao.perfil() != PerfilUsuario.CLIENTE) {
            throw new AccessDeniedException("acesso negado");
        }
        return atendimentos.filiaisDoCliente(sessao.id()).stream()
                .map(unidade -> new FilialOpcao(unidade.getId(), unidade.getNome(), unidade.isAtiva()))
                .sorted(Comparator.comparing(FilialOpcao::nome))
                .toList();
    }

    private record ItemPainel(Atendimento atendimento, Instant instante, boolean futuro,
                              boolean cancelado) {}

    private void validarPeriodo(LocalDate de, LocalDate ate, int pagina, int tamanho) {
        if (de == null || ate == null || de.isAfter(ate) || ate.isAfter(de.plusDays(30))
                || pagina < 0 || tamanho < 1 || tamanho > 100) {
            throw new IllegalArgumentException("periodo de ate 31 dias e pagina de ate 100 itens obrigatorios");
        }
    }

    private Atendimento travarAtendimento(Long id) {
        Atendimento preliminar = atendimentos.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("agendamento nao encontrado"));
        profissionais.bloquear(preliminar.getProfissional().getId()).orElseThrow();
        Atendimento bloqueado = atendimentos.bloquear(id).orElseThrow();
        entityManager.refresh(bloqueado);
        return bloqueado;
    }

    private void validarCatalogo(Long unidadeId, Profissional p, Servico s) {
        if (!p.getUnidade().getId().equals(unidadeId) || !s.getUnidade().getId().equals(unidadeId)
                || !p.getUnidade().isAtiva() || !p.isAtivo() || !s.isAtivo()
                || s.getProfissionais().stream().noneMatch(item -> item.getId().equals(p.getId()))) {
            throw new RecursoNaoEncontradoException("servico ou profissional nao disponivel");
        }
    }

    private void validarHorario(Profissional p, LocalDateTime inicio, int duracao, int intervalo, Long ignorarId) {
        ZoneId fuso = ZoneId.of(p.getUnidade().getFusoHorario());
        LocalDate hoje = clock.instant().atZone(fuso).toLocalDate();
        if (inicio.toLocalTime().getMinute() % 15 != 0 || inicio.toLocalTime().getSecond() != 0
                || inicio.toLocalTime().getNano() != 0 || inicio.toLocalDate().isBefore(hoje)
                || inicio.toLocalDate().isAfter(hoje.plusDays(60))) {
            throw new IllegalArgumentException("inicio fora da grade ou do horizonte de 60 dias");
        }
        var ids = atendimentos.buscarIdsOcupacao(List.of(p.getId()), inicio.toLocalDate().atStartOfDay(),
                inicio.toLocalDate().plusDays(1).atStartOfDay());
        List<Ocupacao> ocupacoes = atendimentos.findAllById(ids).stream()
                .filter(a -> !a.getId().equals(ignorarId))
                .map(a -> new Ocupacao(a.getInicio(), a.getDuracaoMinutos(), a.getIntervaloMinutos())).toList();
        if (!horarios.calcular(inicio.toLocalDate(), fuso, clock.instant(),
                janelas.calcular(p, inicio.toLocalDate()), ocupacoes, duracao, intervalo).contains(inicio)) {
            throw new AgendamentoConflitoException("HORARIO_INDISPONIVEL", "horario indisponivel");
        }
    }

    private Cliente resolverCliente(UsuarioPrincipal sessao, Usuario autor, Long unidadeId, Criacao pedido) {
        if (sessao.perfil() == PerfilUsuario.CLIENTE) {
            if (pedido.clienteId() != null || pedido.clienteNome() != null || pedido.clienteTelefone() != null) {
                throw new IllegalArgumentException("cliente e definido pela sessao");
            }
            return clientes.findByUsuarioId(sessao.id()).orElseGet(() -> {
                Cliente novo = new Cliente(autor.getNome());
                novo.setUsuario(autor);
                novo.setTelefoneContato(autor.getTelefoneContato());
                return clientes.saveAndFlush(novo);
            });
        }
        boolean id = pedido.clienteId() != null;
        boolean nome = pedido.clienteNome() != null;
        if (id == nome) throw new IllegalArgumentException("informe clienteId ou clienteNome");
        if (id) {
            if (pedido.clienteTelefone() != null) {
                throw new IllegalArgumentException("telefone so pode ser informado para cliente avulso");
            }
            Cliente cliente = clientes.findById(pedido.clienteId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("cliente nao encontrado"));
            boolean naLista = Boolean.TRUE.equals(jdbc.queryForObject("""
                    select exists(select 1 from lista_espera where usuario_id=? and unidade_id=? and status='ATIVA')
                    """, Boolean.class, cliente.getUsuario() == null ? -1L : cliente.getUsuario().getId(), unidadeId));
            if (!atendimentos.existsByClienteIdAndProfissionalUnidadeId(cliente.getId(), unidadeId) && !naLista) {
                throw new RecursoNaoEncontradoException("cliente nao encontrado");
            }
            return cliente;
        }
        String nomeLimpo = pedido.clienteNome().trim();
        if (nomeLimpo.length() < 2 || nomeLimpo.length() > 120
                || pedido.clienteTelefone() != null && pedido.clienteTelefone().length() > 20) {
            throw new IllegalArgumentException("dados do cliente invalidos");
        }
        Cliente novo = new Cliente(nomeLimpo);
        novo.setTelefoneContato(pedido.clienteTelefone());
        return clientes.save(novo);
    }

    private void exigirAtorDaUnidade(UsuarioPrincipal sessao, Long unidadeId) {
        if (sessao.perfil() == PerfilUsuario.CLIENTE) return;
        exigirEquipe(sessao, unidadeId);
    }

    private void exigirEquipe(UsuarioPrincipal sessao, Long unidadeId) {
        if (!(sessao.perfil() == PerfilUsuario.RECEPCAO || sessao.perfil() == PerfilUsuario.ADMINISTRADOR)) {
            throw new AccessDeniedException("acesso negado");
        }
        if (!unidadeId.equals(sessao.unidadeId())) {
            throw new RecursoNaoEncontradoException("filial nao encontrada");
        }
    }

    private void exigirAcesso(UsuarioPrincipal sessao, Atendimento a) {
        if (sessao.perfil() == PerfilUsuario.CLIENTE) {
            if (a.getCliente().getUsuario() == null || !a.getCliente().getUsuario().getId().equals(sessao.id())) {
                throw new RecursoNaoEncontradoException("agendamento nao encontrado");
            }
        } else {
            if (sessao.perfil() == PerfilUsuario.PROFISSIONAL) throw new AccessDeniedException("acesso negado");
            if (!a.getProfissional().getUnidade().getId().equals(sessao.unidadeId())) {
                throw new RecursoNaoEncontradoException("agendamento nao encontrado");
            }
        }
    }

    private void exigirAtivoFuturo(Atendimento a) {
        if (a.getStatus() == AtendimentoStatus.CANCELADO) {
            throw new AgendamentoConflitoException("AGENDAMENTO_CANCELADO", "agendamento cancelado");
        }
        exigirAntesInicio(a);
    }

    private void exigirAntesInicio(Atendimento a) {
        Instant inicio = a.getInicio().atZone(ZoneId.of(a.getFusoHorarioAgendamento())).toInstant();
        if (!inicio.isAfter(clock.instant())) {
            throw new AgendamentoConflitoException("AGENDAMENTO_INICIADO", "agendamento ja iniciado");
        }
    }

    private void evento(Atendimento a, Usuario autor, String tipo, AtendimentoStatus estadoAnterior,
                        LocalDateTime inicioAnterior) {
        Long eventoId = jdbc.queryForObject("""
                insert into agendamento_evento(atendimento_id,autor_id,tipo,ocorrido_em,
                    estado_anterior,estado_novo,inicio_anterior,inicio_novo)
                values (?,?,?,?,?,?,?,?)
                returning id
                """, Long.class, a.getId(), autor.getId(), tipo, java.sql.Timestamp.from(clock.instant()),
                estadoAnterior == null ? null : estadoAnterior.name(), a.getStatus().name(),
                inicioAnterior, a.getInicio());
        notificacoes.registrarAgendamento(eventoId, a, tipo);
    }

    private Resposta resposta(Atendimento a, UsuarioPrincipal sessao) {
        var unidade = a.getProfissional().getUnidade();
        Long clienteId = sessao.perfil() == PerfilUsuario.CLIENTE ? null : a.getCliente().getId();
        return new Resposta(a.getId(), unidade.getId(), unidade.getNome(), unidade.isAtiva(), clienteId,
                a.getServico().getId(), a.getServicoNome(), a.getServico().isAtivo(),
                a.getPrecoAcordado(), a.getProfissional().getId(), a.getProfissional().getNome(),
                a.getInicio(), a.getInicio().plusMinutes(a.getDuracaoMinutos()),
                a.getFusoHorarioAgendamento(), a.getStatus(), a.getCriadoEm(), a.getAtualizadoEm(),
                a.getCanceladoEm(), a.getMotivoCancelamento());
    }

    private String hash(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
