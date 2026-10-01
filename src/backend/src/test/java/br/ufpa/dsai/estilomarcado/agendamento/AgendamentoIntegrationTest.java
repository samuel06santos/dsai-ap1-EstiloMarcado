package br.ufpa.dsai.estilomarcado.agendamento;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.GlobalExceptionHandler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.ufpa.dsai.estilomarcado.agendamento.AgendamentoService.Criacao;
import br.ufpa.dsai.estilomarcado.agendamento.model.AtendimentoStatus;
import br.ufpa.dsai.estilomarcado.agendamento.model.Atendimento;
import br.ufpa.dsai.estilomarcado.agendamento.model.Cliente;
import br.ufpa.dsai.estilomarcado.agendamento.repository.AtendimentoRepository;
import br.ufpa.dsai.estilomarcado.agendamento.repository.ClienteRepository;
import br.ufpa.dsai.estilomarcado.autenticacao.model.EstadoConta;
import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;
import br.ufpa.dsai.estilomarcado.autenticacao.model.Usuario;
import br.ufpa.dsai.estilomarcado.autenticacao.repository.UsuarioRepository;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioPrincipal;
import br.ufpa.dsai.estilomarcado.catalogo.model.Profissional;
import br.ufpa.dsai.estilomarcado.catalogo.model.Servico;
import br.ufpa.dsai.estilomarcado.catalogo.model.Unidade;
import br.ufpa.dsai.estilomarcado.catalogo.repository.ProfissionalRepository;
import br.ufpa.dsai.estilomarcado.catalogo.repository.ServicoRepository;
import br.ufpa.dsai.estilomarcado.catalogo.repository.UnidadeRepository;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.RecursoNaoEncontradoException;
import org.springframework.security.access.AccessDeniedException;
import br.ufpa.dsai.estilomarcado.disponibilidade.service.DisponibilidadeService;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.JornadaRequest;
import br.ufpa.dsai.estilomarcado.disponibilidade.exception.ConflitoAtendimentoException;
import java.util.List;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AgendamentoIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @TestConfiguration
    static class RelogioTeste {
        @Bean @Primary Clock relogioFixo() {
            return Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired AgendamentoService service;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UsuarioRepository usuarios;
    @Autowired UnidadeRepository unidades;
    @Autowired ProfissionalRepository profissionais;
    @Autowired ServicoRepository servicos;
    @Autowired AtendimentoRepository atendimentos;
    @Autowired ClienteRepository clientes;
    @Autowired DisponibilidadeService disponibilidade;
    @Autowired GlobalExceptionHandler erros;
    private Unidade unidade;
    private Profissional profissional;
    private Servico servico;
    private Usuario cliente;
    private Usuario admin;
    private final LocalDateTime nove = LocalDateTime.parse("2026-10-05T09:00:00");

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE TABLE agendamento_evento, agendamento_idempotencia, atendimento, "
                + "servico_profissional, servico, cliente, jornada_intervalo, usuario, profissional, "
                + "unidade, estabelecimento RESTART IDENTITY CASCADE");
        unidade = unidades.save(new Unidade("Centro"));
        profissional = profissionais.save(new Profissional("Ana", unidade));
        servico = new Servico(unidade, "Corte", 30, new BigDecimal("50.00"));
        servico.setIntervaloMinutos(15);
        servico.substituirProfissionais(Set.of(profissional));
        servico = servicos.saveAndFlush(servico);
        jdbc.update("insert into jornada_intervalo(profissional_id,dia_semana,hora_inicio,hora_fim) "
                + "values (?,1,'09:00','12:00')", profissional.getId());
        cliente = usuarios.save(new Usuario("Cliente", "cliente@test.local", "cliente@test.local", "x",
                PerfilUsuario.CLIENTE, EstadoConta.ATIVA, null, null));
        admin = usuarios.save(new Usuario("Admin", "admin@test.local", "admin@test.local", "x",
                PerfilUsuario.ADMINISTRADOR, EstadoConta.ATIVA, unidade, null));
    }

    @AfterEach void limparSessao() { SecurityContextHolder.clearContext(); }

    private void autenticar(Usuario usuario) {
        UsuarioPrincipal principal = UsuarioPrincipal.from(usuario);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Criacao pedido(LocalDateTime inicio) {
        return new Criacao(servico.getId(), profissional.getId(), inicio, null, null, null);
    }

    @Test
    void criaParaContaEReutilizaChaveSemDuplicar() {
        autenticar(cliente);
        String chave = UUID.randomUUID().toString();
        var primeiro = service.criar(unidade.getId(), pedido(nove), chave);
        var repetido = service.criar(unidade.getId(), pedido(nove), chave);
        assertEquals(primeiro.id(), repetido.id());
        assertEquals(1, atendimentos.count());
        assertEquals(cliente.getId(), clientes.findById(atendimentos.findById(primeiro.id()).orElseThrow()
                .getCliente().getId()).orElseThrow().getUsuario().getId());
        assertEquals(1, jdbc.queryForObject("select count(*) from agendamento_evento", Integer.class));
        assertEquals("CHAVE_IDEMPOTENCIA_REUTILIZADA", assertThrows(AgendamentoConflitoException.class,
                () -> service.criar(unidade.getId(), pedido(nove.plusMinutes(45)), chave)).getCodigo());
        autenticar(admin);
        service.cancelar(primeiro.id(), null);
        autenticar(cliente);
        assertEquals(AtendimentoStatus.AGENDADO,
                service.criar(unidade.getId(), pedido(nove), chave).status());
    }

    @Test
    void conflitoCancelamentoEReagendamentoPreservamSnapshot() {
        autenticar(cliente);
        var reserva = service.criar(unidade.getId(), pedido(nove), UUID.randomUUID().toString());
        assertEquals("HORARIO_INDISPONIVEL", assertThrows(AgendamentoConflitoException.class,
                () -> service.criar(unidade.getId(), pedido(nove), UUID.randomUUID().toString())).getCodigo());
        autenticar(admin);
        assertEquals(AtendimentoStatus.CONFIRMADO, service.confirmar(reserva.id()).status());
        service.confirmar(reserva.id());
        servico.setPreco(new BigDecimal("99.00"));
        servico.setDuracaoMinutos(60);
        servicos.saveAndFlush(servico);
        assertTrue(service.horariosParaReagendar(reserva.id(), nove.toLocalDate()).horarios().stream()
                .anyMatch(h -> h.inicio().equals(nove.plusHours(1))));
        var reagendado = service.reagendar(reserva.id(), nove.plusHours(1));
        assertEquals(AtendimentoStatus.AGENDADO, reagendado.status());
        assertEquals(new BigDecimal("50.00"), reagendado.precoAcordado());
        assertEquals(nove.plusMinutes(90), reagendado.fim());
        assertEquals(AtendimentoStatus.CANCELADO, service.cancelar(reserva.id(), "pedido do cliente").status());
        assertEquals(4, jdbc.queryForObject("select count(*) from agendamento_evento", Integer.class));
        assertEquals("AGENDAMENTO_CANCELADO", assertThrows(AgendamentoConflitoException.class,
                () -> service.reagendar(reserva.id(), nove.plusHours(2))).getCodigo());
    }

    @Test
    void acessoECsrfProtegemRotas() throws Exception {
        String url = "/api/unidades/" + unidade.getId() + "/agendamentos";
        String corpo = "{\"servicoId\":" + servico.getId() + ",\"profissionalId\":" + profissional.getId()
                + ",\"inicio\":\"2026-10-05T09:00:00\"}";
        mvc.perform(get("/api/me/agendamentos").param("de", "2026-10-05")
                .param("ate", "2026-10-05")).andExpect(status().isUnauthorized());
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mvc.perform(post(url).with(user(UsuarioPrincipal.from(cliente)))
                .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mvc.perform(post(url).with(user(UsuarioPrincipal.from(cliente))).with(csrf())
                .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("AGENDADO"));
        Usuario profissionalUsuario = usuarios.save(new Usuario("Profissional", "pro@test.local", "pro@test.local", "x",
                PerfilUsuario.PROFISSIONAL, EstadoConta.ATIVA, unidade, profissional));
        mvc.perform(post(url).with(user(UsuarioPrincipal.from(profissionalUsuario))).with(csrf())
                .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
    }

    @Test
    void listagensDeSelecaoRespeitamEscopo() throws Exception {
        Cliente registro = new Cliente("Cliente");
        registro.setUsuario(cliente);
        registro = clientes.save(registro);
        atendimentos.save(new Atendimento(profissional, servico, registro, nove, AtendimentoStatus.AGENDADO));

        mvc.perform(get("/api/unidades/" + unidade.getId() + "/clientes")
                        .with(user(UsuarioPrincipal.from(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Cliente"));
        mvc.perform(get("/api/unidades/" + unidade.getId() + "/clientes")
                        .with(user(UsuarioPrincipal.from(cliente))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/unidades/" + unidade.getId() + "/clientes"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/me/filiais").with(user(UsuarioPrincipal.from(cliente))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Centro"));
        mvc.perform(get("/api/me/filiais").with(user(UsuarioPrincipal.from(admin))))
                .andExpect(status().isForbidden());
    }

    @Test
    void duasTransacoesConcorrentesReservamApenasUmaVez() throws Exception {
        Usuario outro = usuarios.save(new Usuario("Outro", "outro@test.local", "outro@test.local", "x",
                PerfilUsuario.CLIENTE, EstadoConta.ATIVA, null, null));
        try (var exec = Executors.newFixedThreadPool(2)) {
            for (int semana = 0; semana < 4; semana++) {
                LocalDateTime inicio = nove.plusWeeks(semana);
                var largada = new CountDownLatch(1);
                Future<Boolean> a = exec.submit(() -> disputar(cliente, largada, inicio));
                Future<Boolean> b = exec.submit(() -> disputar(outro, largada, inicio));
                largada.countDown();
                assertEquals(1, (a.get() ? 1 : 0) + (b.get() ? 1 : 0));
                assertEquals(semana + 1L, atendimentos.count());
                assertEquals(semana + 1, jdbc.queryForObject(
                        "select count(*) from agendamento_evento", Integer.class));
                assertEquals(semana + 1, jdbc.queryForObject(
                        "select count(*) from agendamento_idempotencia", Integer.class));
                assertEquals(clientes.count(), jdbc.queryForObject(
                        "select count(distinct cliente_id) from atendimento", Long.class));
            }
        }
    }

    @Test
    void mudarJornadaEReservarNaoDeixamAgendaInconsistente() throws Exception {
        var largada = new CountDownLatch(1);
        try (var exec = Executors.newFixedThreadPool(2)) {
            Future<Boolean> reserva = exec.submit(() -> disputar(cliente, largada));
            Future<Boolean> mudanca = exec.submit(() -> {
                autenticar(admin);
                largada.await();
                try {
                    disponibilidade.atualizarJornada(unidade.getId(), profissional.getId(),
                            new JornadaRequest(List.of()), "teste");
                    return true;
                } catch (ConflitoAtendimentoException ex) {
                    return false;
                } finally { SecurityContextHolder.clearContext(); }
            });
            largada.countDown();
            assertEquals(1, (reserva.get() ? 1 : 0) + (mudanca.get() ? 1 : 0));
            assertEquals(reserva.get() ? 1L : 0L, atendimentos.count());
        }
    }

    private boolean disputar(Usuario usuario, CountDownLatch largada) throws Exception {
        return disputar(usuario, largada, nove);
    }

    private boolean disputar(Usuario usuario, CountDownLatch largada, LocalDateTime inicio) throws Exception {
        autenticar(usuario);
        largada.await();
        try {
            service.criar(unidade.getId(), pedido(inicio), UUID.randomUUID().toString());
            return true;
        } catch (AgendamentoConflitoException ex) {
            assertEquals("HORARIO_INDISPONIVEL", ex.getCodigo());
            return false;
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void chavesDiferentesNaoPermitemReservaDuplicadaDoMesmoCliente() {
        autenticar(cliente);
        service.criar(unidade.getId(), pedido(nove), UUID.randomUUID().toString());
        assertEquals("HORARIO_INDISPONIVEL", assertThrows(AgendamentoConflitoException.class,
                () -> service.criar(unidade.getId(), pedido(nove), UUID.randomUUID().toString())).getCodigo());
        assertEquals(1, atendimentos.count());
        assertEquals(1, clientes.count());
        assertEquals(1, jdbc.queryForObject("select count(*) from agendamento_evento", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from agendamento_idempotencia", Integer.class));
    }

    @Test
    void periodosParciaisAdjacentesEProfissionaisIndependentes() {
        autenticar(cliente);
        service.criar(unidade.getId(), pedido(nove), UUID.randomUUID().toString());
        assertEquals("HORARIO_INDISPONIVEL", assertThrows(AgendamentoConflitoException.class,
                () -> service.criar(unidade.getId(), pedido(nove.plusMinutes(30)),
                        UUID.randomUUID().toString())).getCodigo());
        service.criar(unidade.getId(), pedido(nove.plusMinutes(45)), UUID.randomUUID().toString());
        Profissional outro = profissionais.save(new Profissional("Bia", unidade));
        servico.substituirProfissionais(Set.of(profissional, outro));
        servicos.saveAndFlush(servico);
        jdbc.update("insert into jornada_intervalo(profissional_id,dia_semana,hora_inicio,hora_fim) "
                + "values (?,1,'09:00','12:00')", outro.getId());
        service.criar(unidade.getId(), new Criacao(servico.getId(), outro.getId(), nove,
                null, null, null), UUID.randomUUID().toString());
        assertEquals(3, atendimentos.count());
    }

    @Test
    void intervaloAposMeiaNoiteBloqueiaDiaSeguinteAteFimExato() {
        jdbc.update("delete from jornada_intervalo where profissional_id=?", profissional.getId());
        jdbc.update("insert into jornada_intervalo(profissional_id,dia_semana,hora_inicio,hora_fim) "
                + "values (?,1,'23:00','23:59'), (?,2,'00:00','02:00')",
                profissional.getId(), profissional.getId());
        servico.setIntervaloMinutos(90);
        servicos.saveAndFlush(servico);
        autenticar(cliente);
        LocalDateTime segunda = nove.toLocalDate().atTime(23, 15);
        service.criar(unidade.getId(), pedido(segunda), UUID.randomUUID().toString());
        assertEquals("HORARIO_INDISPONIVEL", assertThrows(AgendamentoConflitoException.class,
                () -> service.criar(unidade.getId(), pedido(segunda.plusHours(1).plusMinutes(15)),
                        UUID.randomUUID().toString())).getCodigo());
        service.criar(unidade.getId(), pedido(segunda.plusHours(2)), UUID.randomUUID().toString());
        assertEquals(2, atendimentos.count());
    }

    @Test
    void restricaoDoBancoRejeitaEscritaForaDoServicoETraduzConflito() {
        autenticar(cliente);
        var primeiro = service.criar(unidade.getId(), pedido(nove), UUID.randomUUID().toString());
        DataIntegrityViolationException falha = assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("""
                        insert into atendimento(profissional_id,servico_id,cliente_id,inicio,status,
                          duracao_minutos,intervalo_minutos,servico_nome,preco_acordado,
                          fuso_horario_agendamento)
                        select profissional_id,servico_id,cliente_id,?,status,duracao_minutos,
                          intervalo_minutos,servico_nome,preco_acordado,fuso_horario_agendamento
                        from atendimento where id=?
                        """, nove.plusMinutes(30), primeiro.id()));
        assertEquals("23P01", ((SQLException) falha.getMostSpecificCause()).getSQLState());
        var resposta = erros.handleIntegridade(falha);
        assertEquals(409, resposta.getStatusCode().value());
        assertEquals("HORARIO_INDISPONIVEL", resposta.getBody().codigo());
        assertEquals(1, atendimentos.count());
    }

    @Test
    void reagendamentoPerdedorPreservaOrigemECancelamentoLiberaAposCommit() {
        autenticar(cliente);
        var origem = service.criar(unidade.getId(), pedido(nove), UUID.randomUUID().toString());
        var destino = service.criar(unidade.getId(), pedido(nove.plusMinutes(45)),
                UUID.randomUUID().toString());
        assertEquals("HORARIO_INDISPONIVEL", assertThrows(AgendamentoConflitoException.class,
                () -> service.reagendar(origem.id(), destino.inicio())).getCodigo());
        assertEquals(nove, service.detalhar(origem.id()).inicio());
        assertEquals(2, jdbc.queryForObject("select count(*) from agendamento_evento", Integer.class));
        service.cancelar(destino.id(), null);
        assertEquals(destino.inicio(), service.reagendar(origem.id(), destino.inicio()).inicio());
        assertEquals(1, jdbc.queryForObject("select count(*) from atendimento "
                + "where status in ('AGENDADO','CONFIRMADO')", Integer.class));
    }

    @Test
    void cancelamentoConcorrenteSoLiberaHorarioAposConfirmacao() throws Exception {
        autenticar(cliente);
        var reserva = service.criar(unidade.getId(), pedido(nove), UUID.randomUUID().toString());
        Usuario outro = usuarios.save(new Usuario("Outro", "outro@test.local", "outro@test.local", "x",
                PerfilUsuario.CLIENTE, EstadoConta.ATIVA, null, null));
        var largada = new CountDownLatch(1);
        try (var exec = Executors.newFixedThreadPool(2)) {
            Future<Boolean> nova = exec.submit(() -> disputar(outro, largada));
            Future<?> cancelar = exec.submit(() -> {
                autenticar(cliente);
                try {
                    largada.await();
                    service.cancelar(reserva.id(), null);
                } finally { SecurityContextHolder.clearContext(); }
                return null;
            });
            largada.countDown();
            boolean criou = nova.get();
            cancelar.get();
            assertEquals(AtendimentoStatus.CANCELADO, atendimentos.findById(reserva.id()).orElseThrow().getStatus());
            assertEquals(criou ? 1 : 0, jdbc.queryForObject("select count(*) from atendimento "
                    + "where status in ('AGENDADO','CONFIRMADO')", Integer.class));
        }
    }

    @Test
    void reagendamentoECriacaoConcorrentesDisputamMesmoDestino() throws Exception {
        autenticar(cliente);
        var origem = service.criar(unidade.getId(), pedido(nove), UUID.randomUUID().toString());
        Usuario outro = usuarios.save(new Usuario("Outro", "outro@test.local", "outro@test.local", "x",
                PerfilUsuario.CLIENTE, EstadoConta.ATIVA, null, null));
        LocalDateTime destino = nove.plusHours(1);
        var largada = new CountDownLatch(1);
        try (var exec = Executors.newFixedThreadPool(2)) {
            Future<Boolean> criar = exec.submit(() -> disputar(outro, largada, destino));
            Future<Boolean> mover = exec.submit(() -> {
                autenticar(cliente);
                try {
                    largada.await();
                    service.reagendar(origem.id(), destino);
                    return true;
                } catch (AgendamentoConflitoException ex) {
                    assertEquals("HORARIO_INDISPONIVEL", ex.getCodigo());
                    return false;
                } finally { SecurityContextHolder.clearContext(); }
            });
            largada.countDown();
            boolean criou = criar.get();
            boolean moveu = mover.get();
            assertEquals(1, (criou ? 1 : 0) + (moveu ? 1 : 0));
            assertEquals(moveu ? destino : nove, atendimentos.findById(origem.id()).orElseThrow().getInicio());
            assertEquals(criou ? 2 : 1, atendimentos.count());
            assertEquals(criou ? 2 : 1, jdbc.queryForObject(
                    "select count(*) from agendamento_idempotencia", Integer.class));
        }
    }

    @Test
    void apenasProprietarioOuEquipeDaFilialConsultaDetalhe() {
        autenticar(cliente);
        Long id = service.criar(unidade.getId(), pedido(nove), UUID.randomUUID().toString()).id();
        Usuario outro = usuarios.save(new Usuario("Outro", "outro@test.local", "outro@test.local", "x",
                PerfilUsuario.CLIENTE, EstadoConta.ATIVA, null, null));
        autenticar(outro);
        assertThrows(RecursoNaoEncontradoException.class, () -> service.detalhar(id));
        Unidade outraUnidade = unidades.save(new Unidade("Outra"));
        Usuario outroAdmin = usuarios.save(new Usuario("Outro admin", "outra@test.local", "outra@test.local", "x",
                PerfilUsuario.ADMINISTRADOR, EstadoConta.ATIVA, outraUnidade, null));
        autenticar(outroAdmin);
        assertThrows(RecursoNaoEncontradoException.class, () -> service.detalhar(id));
        Usuario profissionalUsuario = usuarios.save(new Usuario("Profissional", "pro@test.local", "pro@test.local", "x",
                PerfilUsuario.PROFISSIONAL, EstadoConta.ATIVA, unidade, profissional));
        autenticar(profissionalUsuario);
        assertThrows(AccessDeniedException.class, () -> service.confirmar(id));
    }

    @Test
    void naoPermiteMutacoesAposInicio() {
        autenticar(admin);
        Cliente avulso = clientes.save(new Cliente("Avulso"));
        Atendimento passado = atendimentos.saveAndFlush(new Atendimento(profissional, servico, avulso,
                LocalDateTime.parse("2026-10-01T08:00:00"), AtendimentoStatus.AGENDADO));
        assertEquals("AGENDAMENTO_INICIADO", assertThrows(AgendamentoConflitoException.class,
                () -> service.confirmar(passado.getId())).getCodigo());
        assertEquals("AGENDAMENTO_INICIADO", assertThrows(AgendamentoConflitoException.class,
                () -> service.cancelar(passado.getId(), null)).getCodigo());
        assertEquals("AGENDAMENTO_INICIADO", assertThrows(AgendamentoConflitoException.class,
                () -> service.reagendar(passado.getId(), nove)).getCodigo());
    }

    @Test
    void equipeCriaAvulsoMasNaoUsaClienteDeOutraFilial() {
        autenticar(admin);
        var avulso = service.criar(unidade.getId(),
                new Criacao(servico.getId(), profissional.getId(), nove, null, "Maria", "5591999999999"),
                UUID.randomUUID().toString());
        assertNotNull(avulso.clienteId());
        assertNull(clientes.findById(avulso.clienteId()).orElseThrow().getUsuario());
        assertEquals("5591999999999", clientes.findById(avulso.clienteId()).orElseThrow().getTelefoneContato());
        var conhecido = service.criar(unidade.getId(),
                new Criacao(servico.getId(), profissional.getId(), nove.plusHours(1),
                        avulso.clienteId(), null, null), UUID.randomUUID().toString());
        assertEquals(avulso.clienteId(), conhecido.clienteId());
        Cliente deFora = clientes.save(new Cliente("Fora"));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.criar(unidade.getId(),
                new Criacao(servico.getId(), profissional.getId(), nove.plusHours(2),
                        deFora.getId(), null, null), UUID.randomUUID().toString()));
    }

    @Test
    void escritaRevalidaFeriadoEEstadoDoCatalogo() {
        autenticar(cliente);
        jdbc.update("insert into feriado(unidade_id,data,nome) values (?,?,?)",
                unidade.getId(), nove.toLocalDate(), "Feriado");
        assertEquals("HORARIO_INDISPONIVEL", assertThrows(AgendamentoConflitoException.class,
                () -> service.criar(unidade.getId(), pedido(nove), UUID.randomUUID().toString())).getCodigo());
        jdbc.update("delete from feriado");
        servico.setAtivo(false);
        servicos.saveAndFlush(servico);
        assertThrows(RecursoNaoEncontradoException.class, () ->
                service.criar(unidade.getId(), pedido(nove), UUID.randomUUID().toString()));
    }
}
