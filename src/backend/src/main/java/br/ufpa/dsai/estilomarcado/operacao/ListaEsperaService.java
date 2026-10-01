package br.ufpa.dsai.estilomarcado.operacao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.ufpa.dsai.estilomarcado.agendamento.AgendamentoConflitoException;
import br.ufpa.dsai.estilomarcado.agendamento.AgendamentoService;
import br.ufpa.dsai.estilomarcado.agendamento.AgendamentoService.Criacao;
import br.ufpa.dsai.estilomarcado.agendamento.AgendamentoService.Resposta;
import br.ufpa.dsai.estilomarcado.agendamento.model.Cliente;
import br.ufpa.dsai.estilomarcado.agendamento.repository.ClienteRepository;
import br.ufpa.dsai.estilomarcado.autenticacao.model.EstadoConta;
import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;
import br.ufpa.dsai.estilomarcado.autenticacao.repository.UsuarioRepository;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioAtual;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioPrincipal;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.RecursoNaoEncontradoException;
import br.ufpa.dsai.estilomarcado.catalogo.model.Profissional;
import br.ufpa.dsai.estilomarcado.catalogo.model.Servico;
import br.ufpa.dsai.estilomarcado.catalogo.model.Unidade;
import br.ufpa.dsai.estilomarcado.catalogo.repository.ProfissionalRepository;
import br.ufpa.dsai.estilomarcado.catalogo.repository.ServicoRepository;
import br.ufpa.dsai.estilomarcado.catalogo.repository.UnidadeRepository;
import br.ufpa.dsai.estilomarcado.disponibilidade.service.MotorDisponibilidadeService;

@Service
public class ListaEsperaService {
    public record Preferencias(Long servicoId, Long profissionalId, LocalDate dataInicio,
                               LocalDate dataFim, LocalTime horaInicio, LocalTime horaFim) {}
    public record Solicitacao(Long id, Long unidadeId, Long servicoId, Long profissionalId,
                              LocalDate dataInicio, LocalDate dataFim, LocalTime horaInicio,
                              LocalTime horaFim, String status, Instant criadoEm,
                              Long usuarioId, String clienteNome, String telefoneContato,
                              Long posicaoAproximada) {}
    public record Oferta(Long id, Long solicitacaoId, Long profissionalId, LocalDateTime inicio,
                         String fusoHorario, String status, Instant expiraEm) {}
    public record Entrada(Solicitacao solicitacao, boolean criada) {}
    public record Encaixe(Long listaEsperaId, Long profissionalId, LocalDateTime inicio,
                          boolean autorizacaoConfirmada) {}

    private final JdbcTemplate jdbc;
    private final UsuarioAtual atual;
    private final UsuarioRepository usuarios;
    private final ClienteRepository clientes;
    private final UnidadeRepository unidades;
    private final ServicoRepository servicos;
    private final ProfissionalRepository profissionais;
    private final MotorDisponibilidadeService motor;
    private final AgendamentoService agendamentos;
    private final NotificacaoService notificacoes;
    private final Clock clock;

    public ListaEsperaService(JdbcTemplate jdbc, UsuarioAtual atual, UsuarioRepository usuarios,
            ClienteRepository clientes, UnidadeRepository unidades, ServicoRepository servicos,
            ProfissionalRepository profissionais, MotorDisponibilidadeService motor,
            AgendamentoService agendamentos, NotificacaoService notificacoes, Clock clock) {
        this.jdbc = jdbc;
        this.atual = atual;
        this.usuarios = usuarios;
        this.clientes = clientes;
        this.unidades = unidades;
        this.servicos = servicos;
        this.profissionais = profissionais;
        this.motor = motor;
        this.agendamentos = agendamentos;
        this.notificacoes = notificacoes;
        this.clock = clock;
    }

    @Transactional
    public Entrada entrar(Long unidadeId, Preferencias preferencias) {
        UsuarioPrincipal sessao = exigirCliente();
        var usuario = usuarios.bloquearPorId(sessao.id()).orElseThrow();
        if (usuario.getEstado() != EstadoConta.ATIVA) throw new AccessDeniedException("conta inativa");
        validar(unidadeId, preferencias);
        clientes.findByUsuarioId(sessao.id()).orElseGet(() -> {
            Cliente cliente = new Cliente(usuario.getNome());
            cliente.setUsuario(usuario);
            cliente.setTelefoneContato(usuario.getTelefoneContato());
            return clientes.saveAndFlush(cliente);
        });
        List<Long> ativos = jdbc.queryForList("""
                select id from lista_espera where usuario_id=? and unidade_id=? and servico_id=?
                and status='ATIVA' for update
                """, Long.class, sessao.id(), unidadeId, preferencias.servicoId());
        boolean criada = ativos.isEmpty();
        Long id;
        if (criada) {
            id = jdbc.queryForObject("""
                    insert into lista_espera(usuario_id,unidade_id,servico_id,profissional_id,
                      data_inicio,data_fim,hora_inicio,hora_fim)
                    values (?,?,?,?,?,?,?,?) returning id
                    """, Long.class, sessao.id(), unidadeId, preferencias.servicoId(),
                    preferencias.profissionalId(), preferencias.dataInicio(), preferencias.dataFim(),
                    preferencias.horaInicio(), preferencias.horaFim());
            evento(id, sessao.id(), null, "ATIVA");
        } else {
            id = ativos.getFirst();
            jdbc.update("update lista_espera_oferta set status='INDISPONIVEL' "
                    + "where lista_espera_id=? and status='ENVIADA'", id);
            jdbc.update("""
                    update lista_espera set profissional_id=?,data_inicio=?,data_fim=?,
                      hora_inicio=?,hora_fim=?,atualizado_em=? where id=?
                    """, preferencias.profissionalId(), preferencias.dataInicio(), preferencias.dataFim(),
                    preferencias.horaInicio(), preferencias.horaFim(), agora(), id);
        }
        return new Entrada(buscar(id), criada);
    }

    @Transactional(readOnly = true)
    public List<Solicitacao> minhas(int pagina, int tamanho) {
        UsuarioPrincipal sessao = exigirCliente();
        paginacao(pagina, tamanho);
        return jdbc.query("""
                select l.*,u.nome,u.telefone_contato,
                  (select count(*) from lista_espera x where x.status='ATIVA'
                   and x.unidade_id=l.unidade_id and x.servico_id=l.servico_id
                   and (x.criado_em,x.id)<=(l.criado_em,l.id)) as posicao
                from lista_espera l join usuario u on u.id=l.usuario_id
                where l.usuario_id=? order by l.criado_em desc,l.id desc limit ? offset ?
                """, this::mapear, sessao.id(), tamanho, pagina * tamanho);
    }

    @Transactional(readOnly = true)
    public List<Solicitacao> daUnidade(Long unidadeId, String status, int pagina, int tamanho) {
        exigirEquipe(unidadeId);
        paginacao(pagina, tamanho);
        if (status != null && !List.of("ATIVA", "ATENDIDA", "CANCELADA", "EXPIRADA").contains(status)) {
            throw new IllegalArgumentException("status invalido");
        }
        return jdbc.query("""
                select l.*,u.nome,u.telefone_contato,
                  (select count(*) from lista_espera x where x.status='ATIVA'
                   and x.unidade_id=l.unidade_id and x.servico_id=l.servico_id
                   and (x.criado_em,x.id)<=(l.criado_em,l.id)) as posicao
                from lista_espera l join usuario u on u.id=l.usuario_id
                where l.unidade_id=? and (? is null or l.status=?)
                order by l.criado_em,l.id limit ? offset ?
                """, this::mapear, unidadeId, status, status, tamanho, pagina * tamanho);
    }

    @Transactional(readOnly = true)
    public List<Oferta> minhasOfertas(Long solicitacaoId) {
        UsuarioPrincipal sessao = exigirCliente();
        Solicitacao s = buscar(solicitacaoId);
        if (!s.usuarioId().equals(sessao.id())) throw naoEncontrado();
        return jdbc.query("""
                select * from lista_espera_oferta where lista_espera_id=? order by emitida_em desc,id desc
                limit 100
                """, this::mapearOferta, solicitacaoId);
    }

    @Transactional(readOnly = true)
    public List<Oferta> ofertasDaUnidade(Long unidadeId, Long solicitacaoId) {
        exigirEquipe(unidadeId);
        Solicitacao s = buscar(solicitacaoId);
        if (!s.unidadeId().equals(unidadeId)) throw naoEncontrado();
        return jdbc.query("select * from lista_espera_oferta where lista_espera_id=? "
                + "order by emitida_em desc,id desc limit 100", this::mapearOferta, solicitacaoId);
    }

    @Transactional
    public void cancelar(Long id, Long unidadeId) {
        UsuarioPrincipal sessao = atual.get();
        Solicitacao s = buscar(id);
        if (unidadeId == null) {
            if (sessao.perfil() != PerfilUsuario.CLIENTE || !s.usuarioId().equals(sessao.id())) throw naoEncontrado();
        } else {
            exigirEquipe(unidadeId);
            if (!s.unidadeId().equals(unidadeId)) throw naoEncontrado();
        }
        jdbc.queryForList("select id from lista_espera where id=? for update", Long.class, id);
        int mudou = jdbc.update("update lista_espera set status='CANCELADA',atualizado_em=? "
                + "where id=? and status='ATIVA'", agora(), id);
        if (mudou != 0) {
            jdbc.update("update lista_espera_oferta set status='INDISPONIVEL' "
                    + "where lista_espera_id=? and status='ENVIADA'", id);
            evento(id, sessao.id(), "ATIVA", "CANCELADA");
        }
    }

    @Transactional
    public Resposta aceitar(Long id, Long ofertaId, String chave) {
        UsuarioPrincipal sessao = exigirCliente();
        Solicitacao s = buscar(id);
        if (!s.usuarioId().equals(sessao.id())) throw naoEncontrado();
        Oferta oferta = buscarOferta(ofertaId);
        if (!oferta.solicitacaoId().equals(id)) throw naoEncontrado();
        Resposta repetida = respostaAnterior(sessao.id(), chave, id, oferta.profissionalId(), oferta.inicio());
        if (repetida != null) return repetida;
        if (!"ATIVA".equals(s.status())) throw conflito("SOLICITACAO_ENCERRADA");
        if (!"ENVIADA".equals(oferta.status()) || !oferta.expiraEm().isAfter(clock.instant())) {
            throw conflito("OFERTA_EXPIRADA");
        }
        validarCompatibilidade(s, oferta.profissionalId(), oferta.inicio());
        Resposta resposta = agendamentos.criar(s.unidadeId(),
                new Criacao(s.servicoId(), oferta.profissionalId(), oferta.inicio(), null, null, null), chave);
        concluir(s, ofertaId, resposta.id(), sessao.id());
        return resposta;
    }

    @Transactional
    public void marcarOfertaIndisponivel(Long id, Long ofertaId) {
        UsuarioPrincipal sessao = exigirCliente();
        Solicitacao s = buscar(id);
        Oferta oferta = buscarOferta(ofertaId);
        if (!s.usuarioId().equals(sessao.id()) || !oferta.solicitacaoId().equals(id)) throw naoEncontrado();
        jdbc.update("update lista_espera_oferta set status='INDISPONIVEL' "
                + "where id=? and status='ENVIADA'", ofertaId);
    }

    @Scheduled(fixedDelayString = "${app.lista-espera.intervalo-ms:300000}")
    @Transactional
    public void verificarOportunidades() {
        jdbc.update("update lista_espera_oferta set status='EXPIRADA' "
                + "where status='ENVIADA' and expira_em<=?", agora());
        List<Long> expiradas = jdbc.query("""
                update lista_espera l set status='EXPIRADA',atualizado_em=?
                from unidade u where l.unidade_id=u.id and l.status='ATIVA'
                and l.data_fim < (now() at time zone u.fuso_horario)::date
                returning l.id
                """, (r, n) -> r.getLong(1), agora());
        for (Long id : expiradas) evento(id, null, "ATIVA", "EXPIRADA");
        List<Solicitacao> fila = jdbc.query("""
                select l.*,u.nome,u.telefone_contato,
                  (select count(*) from lista_espera x where x.status='ATIVA'
                   and x.unidade_id=l.unidade_id and x.servico_id=l.servico_id
                   and (x.criado_em,x.id)<=(l.criado_em,l.id)) as posicao
                from lista_espera l join usuario u on u.id=l.usuario_id
                left join notificacao_preferencia np on np.usuario_id=u.id
                where l.status='ATIVA' and u.estado='ATIVA' and coalesce(np.avisos_lista,true) and not exists (
                  select 1 from lista_espera_oferta o where o.lista_espera_id=l.id and o.status='ENVIADA')
                order by l.criado_em,l.id limit 100 for update of l skip locked
                """, this::mapear);
        for (Solicitacao s : fila) {
            String fuso = unidades.findById(s.unidadeId()).orElseThrow(this::naoEncontrado).getFusoHorario();
            LocalDate hoje = clock.instant().atZone(ZoneId.of(fuso)).toLocalDate();
            LocalDate dia = s.dataInicio().isBefore(hoje) ? hoje : s.dataInicio();
            while (!dia.isAfter(s.dataFim())) {
                try {
                    if (tentarOferecer(s, dia)) break;
                } catch (RecursoNaoEncontradoException ex) {
                    break;
                }
                dia = dia.plusDays(1);
            }
        }
    }

    private boolean tentarOferecer(Solicitacao s, LocalDate dia) {
        var horarios = motor.consultar(s.unidadeId(), s.servicoId(), dia, s.profissionalId()).horarios();
        int duracao = servicos.findById(s.servicoId()).orElseThrow(this::naoEncontrado).getDuracaoMinutos();
        for (var h : horarios) {
            if (s.horaInicio() != null && (h.inicio().toLocalTime().isBefore(s.horaInicio())
                    || h.inicio().plusMinutes(duracao).toLocalTime().isAfter(s.horaFim()))) continue;
            Boolean jaOfertado = jdbc.queryForObject("""
                    select exists(select 1 from lista_espera_oferta
                      where lista_espera_id=? and profissional_id=? and inicio=?)
                    """, Boolean.class, s.id(), h.profissionalId(), h.inicio());
            if (Boolean.TRUE.equals(jaOfertado)) continue;
            Boolean avisoAtivo = jdbc.queryForObject("""
                    select exists(select 1 from lista_espera_oferta o join lista_espera l on l.id=o.lista_espera_id
                      where l.unidade_id=? and l.servico_id=? and o.profissional_id=? and o.inicio=?
                      and o.status='ENVIADA' and o.expira_em>?)
                    """, Boolean.class, s.unidadeId(), s.servicoId(), h.profissionalId(), h.inicio(), agora());
            if (Boolean.TRUE.equals(avisoAtivo)) continue;
            String fuso = unidades.findById(s.unidadeId()).orElseThrow(this::naoEncontrado).getFusoHorario();
            Long ofertaId = jdbc.queryForObject("""
                    insert into lista_espera_oferta(lista_espera_id,profissional_id,inicio,fuso_horario,expira_em)
                    values (?,?,?,?,?) returning id
                    """, Long.class, s.id(), h.profissionalId(), h.inicio(), fuso,
                    java.sql.Timestamp.from(clock.instant().plusSeconds(900)));
            notificacoes.registrarOferta(ofertaId, s.usuarioId());
            return true;
        }
        return false;
    }

    @Transactional
    public Resposta encaixar(Long unidadeId, Encaixe pedido, String chave) {
        UsuarioPrincipal sessao = exigirEquipe(unidadeId);
        if (pedido == null || pedido.listaEsperaId() == null || pedido.profissionalId() == null
                || pedido.inicio() == null || !pedido.autorizacaoConfirmada()) {
            throw new IllegalArgumentException("solicitacao, horario e autorizacao do cliente sao obrigatorios");
        }
        Solicitacao s = buscar(pedido.listaEsperaId());
        if (!s.unidadeId().equals(unidadeId)) throw naoEncontrado();
        Resposta repetida = respostaAnterior(sessao.id(), chave, s.id(), pedido.profissionalId(), pedido.inicio());
        if (repetida != null) return repetida;
        if (!"ATIVA".equals(s.status())) throw conflito("SOLICITACAO_ENCERRADA");
        validarCompatibilidade(s, pedido.profissionalId(), pedido.inicio());
        Long clienteId = jdbc.queryForObject("select id from cliente where usuario_id=?", Long.class, s.usuarioId());
        Resposta resposta = agendamentos.criar(unidadeId,
                new Criacao(s.servicoId(), pedido.profissionalId(), pedido.inicio(), clienteId, null, null), chave);
        concluir(s, null, resposta.id(), sessao.id());
        return resposta;
    }

    private void concluir(Solicitacao s, Long ofertaId, Long atendimentoId, Long autorId) {
        jdbc.queryForList("select id from lista_espera where id=? for update", Long.class, s.id());
        if (jdbc.update("update lista_espera set status='ATENDIDA',atualizado_em=? "
                + "where id=? and status='ATIVA'", agora(), s.id()) != 1) {
            throw conflito("SOLICITACAO_ENCERRADA");
        }
        jdbc.update("update atendimento set lista_espera_id=? where id=?", s.id(), atendimentoId);
        jdbc.update("update lista_espera_oferta set status='INDISPONIVEL' "
                + "where lista_espera_id=? and status='ENVIADA' and id<>coalesce(cast(? as bigint),-1)",
                s.id(), ofertaId);
        if (ofertaId != null) jdbc.update("update lista_espera_oferta set status='ACEITA' where id=?", ofertaId);
        evento(s.id(), autorId, "ATIVA", "ATENDIDA");
    }

    private Resposta respostaAnterior(Long autorId, String chave, Long listaId,
            Long profissionalId, LocalDateTime inicio) {
        UUID uuid;
        try { uuid = UUID.fromString(chave); }
        catch (Exception ex) { throw new IllegalArgumentException("Idempotency-Key deve ser UUID"); }
        List<Resposta> respostas = jdbc.query("""
                select a.id,a.lista_espera_id,a.profissional_id,a.inicio
                from agendamento_idempotencia i join atendimento a on a.id=i.atendimento_id
                where i.usuario_id=? and i.chave=?
                """, (r, n) -> {
                    if (!listaId.equals(r.getLong("lista_espera_id"))
                            || !profissionalId.equals(r.getLong("profissional_id"))
                            || !inicio.equals(r.getObject("inicio", LocalDateTime.class))) {
                        throw conflito("CHAVE_IDEMPOTENCIA_REUTILIZADA");
                    }
                    return agendamentos.detalhar(r.getLong("id"));
                }, autorId, uuid);
        return respostas.isEmpty() ? null : respostas.getFirst();
    }

    private void validar(Long unidadeId, Preferencias p) {
        if (p == null || p.servicoId() == null || p.dataInicio() == null || p.dataFim() == null) {
            throw new IllegalArgumentException("servicoId, dataInicio e dataFim sao obrigatorios");
        }
        Unidade unidade = unidades.findById(unidadeId).filter(Unidade::isAtiva).orElseThrow(this::naoEncontrado);
        Servico servico = servicos.findById(p.servicoId()).filter(Servico::isAtivo)
                .filter(item -> item.getUnidade().getId().equals(unidadeId)).orElseThrow(this::naoEncontrado);
        LocalDate hoje = clock.instant().atZone(ZoneId.of(unidade.getFusoHorario())).toLocalDate();
        if (p.dataInicio().isBefore(hoje) || p.dataFim().isBefore(p.dataInicio())
                || p.dataFim().isAfter(hoje.plusDays(60))
                || p.dataFim().isAfter(p.dataInicio().plusDays(30))
                || (p.horaInicio() == null) != (p.horaFim() == null)
                || p.horaInicio() != null && !p.horaInicio().isBefore(p.horaFim())) {
            throw new IllegalArgumentException("preferencias de data ou horario invalidas");
        }
        if (p.profissionalId() != null) {
            Profissional pro = profissionais.findById(p.profissionalId()).orElseThrow(this::naoEncontrado);
            if (!pro.isAtivo() || !pro.getUnidade().getId().equals(unidadeId)
                    || servico.getProfissionais().stream().noneMatch(item -> item.getId().equals(pro.getId()))) {
                throw naoEncontrado();
            }
        }
    }

    private void validarCompatibilidade(Solicitacao s, Long profissionalId, LocalDateTime inicio) {
        if (!"ATIVA".equals(s.status()) || inicio.toLocalDate().isBefore(s.dataInicio())
                || inicio.toLocalDate().isAfter(s.dataFim())
                || s.profissionalId() != null && !s.profissionalId().equals(profissionalId)) {
            throw conflito("PREFERENCIA_INCOMPATIVEL");
        }
        int duracao = servicos.findById(s.servicoId()).orElseThrow(this::naoEncontrado).getDuracaoMinutos();
        if (s.horaInicio() != null && (inicio.toLocalTime().isBefore(s.horaInicio())
                || inicio.plusMinutes(duracao).toLocalTime().isAfter(s.horaFim()))) {
            throw conflito("PREFERENCIA_INCOMPATIVEL");
        }
        if (motor.consultar(s.unidadeId(), s.servicoId(), inicio.toLocalDate(), profissionalId)
                .horarios().stream().noneMatch(h -> h.inicio().equals(inicio)
                        && h.profissionalId().equals(profissionalId))) {
            throw conflito("HORARIO_INDISPONIVEL");
        }
    }

    private UsuarioPrincipal exigirCliente() {
        UsuarioPrincipal sessao = atual.get();
        if (sessao.perfil() != PerfilUsuario.CLIENTE) throw new AccessDeniedException("acesso negado");
        return sessao;
    }

    private UsuarioPrincipal exigirEquipe(Long unidadeId) {
        UsuarioPrincipal sessao = atual.get();
        if (sessao.perfil() != PerfilUsuario.RECEPCAO && sessao.perfil() != PerfilUsuario.ADMINISTRADOR) {
            throw new AccessDeniedException("acesso negado");
        }
        if (!unidadeId.equals(sessao.unidadeId())) throw naoEncontrado();
        return sessao;
    }

    private void paginacao(int pagina, int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > 100) throw new IllegalArgumentException("paginacao invalida");
    }

    private Solicitacao buscar(Long id) {
        return jdbc.query("""
                select l.*,u.nome,u.telefone_contato,
                  (select count(*) from lista_espera x where x.status='ATIVA'
                   and x.unidade_id=l.unidade_id and x.servico_id=l.servico_id
                   and (x.criado_em,x.id)<=(l.criado_em,l.id)) as posicao
                from lista_espera l join usuario u on u.id=l.usuario_id
                where l.id=?
                """, this::mapear, id).stream().findFirst().orElseThrow(this::naoEncontrado);
    }

    private Oferta buscarOferta(Long id) {
        return jdbc.query("select * from lista_espera_oferta where id=?", this::mapearOferta, id)
                .stream().findFirst().orElseThrow(this::naoEncontrado);
    }

    private Solicitacao mapear(ResultSet r, int n) throws SQLException {
        return new Solicitacao(r.getLong("id"), r.getLong("unidade_id"), r.getLong("servico_id"),
                (Long) r.getObject("profissional_id"), r.getObject("data_inicio", LocalDate.class),
                r.getObject("data_fim", LocalDate.class), r.getObject("hora_inicio", LocalTime.class),
                r.getObject("hora_fim", LocalTime.class), r.getString("status"),
                r.getTimestamp("criado_em").toInstant(), r.getLong("usuario_id"),
                r.getString("nome"), r.getString("telefone_contato"),
                "ATIVA".equals(r.getString("status")) ? r.getLong("posicao") : null);
    }

    private Oferta mapearOferta(ResultSet r, int n) throws SQLException {
        return new Oferta(r.getLong("id"), r.getLong("lista_espera_id"), r.getLong("profissional_id"),
                r.getObject("inicio", LocalDateTime.class), r.getString("fuso_horario"),
                r.getString("status"), r.getTimestamp("expira_em").toInstant());
    }

    private void evento(Long id, Long autorId, String anterior, String novo) {
        jdbc.update("insert into lista_espera_evento(lista_espera_id,autor_id,estado_anterior,estado_novo,ocorrido_em) "
                + "values (?,?,?,?,?)", id, autorId, anterior, novo, agora());
    }

    private java.sql.Timestamp agora() { return java.sql.Timestamp.from(clock.instant()); }

    private AgendamentoConflitoException conflito(String codigo) {
        return new AgendamentoConflitoException(codigo, codigo.toLowerCase().replace('_', ' '));
    }

    private RecursoNaoEncontradoException naoEncontrado() {
        return new RecursoNaoEncontradoException("solicitacao ou recurso nao encontrado");
    }
}
