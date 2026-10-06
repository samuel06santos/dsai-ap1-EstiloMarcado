package br.ufpa.dsai.estilomarcado.operacao;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;
import java.util.UUID;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.ufpa.dsai.estilomarcado.autenticacao.model.EstadoConta;
import br.ufpa.dsai.estilomarcado.agendamento.AgendamentoService;
import br.ufpa.dsai.estilomarcado.agendamento.AgendamentoService.Criacao;
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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OperacaoIntegrationTest {
    private static final LocalDate DIA_ATENDIMENTO = LocalDate.now(ZoneId.of("America/Sao_Paulo"))
            .with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    private static final LocalDate DIA_ATIVIDADE = DIA_ATENDIMENTO.minusDays(3);

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @TestConfiguration
    static class RelogioTeste {
        @Bean @Primary Clock relogioFixo() {
            return Clock.fixed(DIA_ATIVIDADE.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        }
    }

    @Autowired ListaEsperaService fila;
    @Autowired AgendamentoService agendamentos;
    @Autowired NotificacaoService notificacoes;
    @Autowired HistoricoRelatorioService historico;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired UnidadeRepository unidades;
    @Autowired ProfissionalRepository profissionais;
    @Autowired ServicoRepository servicos;

    Unidade unidade;
    Profissional profissional;
    Servico servico;
    Usuario cliente;
    Usuario admin;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE TABLE atendimento, servico_profissional, servico, cliente, jornada_intervalo, "
                + "usuario, profissional, unidade, estabelecimento RESTART IDENTITY CASCADE");
        unidade = unidades.save(new Unidade("Centro"));
        profissional = profissionais.save(new Profissional("Ana", unidade));
        servico = new Servico(unidade, "Corte", 30, new BigDecimal("50.00"));
        servico.setIntervaloMinutos(15);
        servico.substituirProfissionais(Set.of(profissional));
        servico = servicos.saveAndFlush(servico);
        jdbc.update("insert into jornada_intervalo(profissional_id,dia_semana,hora_inicio,hora_fim) "
                + "values (?,1,'09:00','12:00')", profissional.getId());
        cliente = usuarios.save(new Usuario("Cliente", "cliente@teste.local", "cliente@teste.local", "x",
                PerfilUsuario.CLIENTE, EstadoConta.ATIVA, null, null));
        admin = usuarios.save(new Usuario("Admin", "admin@teste.local", "admin@teste.local", "x",
                PerfilUsuario.ADMINISTRADOR, EstadoConta.ATIVA, unidade, null));
    }

    private void autenticar(Usuario usuario) {
        UsuarioPrincipal principal = UsuarioPrincipal.from(usuario);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private ListaEsperaService.Preferencias preferencias() {
        return new ListaEsperaService.Preferencias(servico.getId(), profissional.getId(),
                DIA_ATENDIMENTO, DIA_ATENDIMENTO,
                LocalTime.parse("09:00"), LocalTime.parse("12:00"));
    }

    @Test
    void atualizacaoMantemPosicaoEEncaixeCriaReservaUmaVez() {
        autenticar(cliente);
        var primeira = fila.entrar(unidade.getId(), preferencias());
        assertTrue(primeira.criada());
        var segunda = fila.entrar(unidade.getId(), preferencias());
        assertFalse(segunda.criada());
        assertEquals(primeira.solicitacao().id(), segunda.solicitacao().id());
        fila.verificarOportunidades();
        var ofertas = fila.minhasOfertas(primeira.solicitacao().id());
        assertFalse(ofertas.isEmpty());
        String chave = UUID.randomUUID().toString();
        var reserva = fila.aceitar(primeira.solicitacao().id(), ofertas.getFirst().id(), chave);
        assertEquals(reserva.id(), fila.aceitar(primeira.solicitacao().id(), ofertas.getFirst().id(), chave).id());
        assertEquals("ATENDIDA", jdbc.queryForObject("select status from lista_espera where id=?", String.class,
                primeira.solicitacao().id()));
        assertEquals(1, jdbc.queryForObject("select count(*) from atendimento", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from notificacao_interna where tipo='CRIACAO'", Integer.class));
        assertFalse(notificacoes.minhas(0, 10).isEmpty());
        autenticar(admin);
        assertEquals(1, historico.eventos(reserva.id(), 0, 10).size());
        var relatorio = historico.relatorio(unidade.getId(), DIA_ATENDIMENTO,
                DIA_ATENDIMENTO, null, null);
        assertEquals(1, relatorio.total().agendados());
        var atividade = historico.relatorio(unidade.getId(), DIA_ATIVIDADE,
                DIA_ATIVIDADE, null, null);
        assertEquals(1, atividade.total().encaixes());
        assertEquals(1, jdbc.queryForObject("select count(*) from notificacao_outbox "
                + "where tipo='LEMBRETE' and status='PENDENTE'", Integer.class));
        agendamentos.cancelar(reserva.id(), null);
        assertEquals(1, jdbc.queryForObject("select count(*) from notificacao_outbox "
                + "where tipo='LEMBRETE' and status='CANCELADO'", Integer.class));
    }

    @Test
    void caixaPessoalExigeCsrfEIsolaOutroUsuario() throws Exception {
        autenticar(cliente);
        var entrada = fila.entrar(unidade.getId(), preferencias());
        mvc.perform(post("/api/unidades/{id}/lista-espera", unidade.getId())
                .with(user(UsuarioPrincipal.from(cliente)))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        autenticar(cliente);
        fila.cancelar(entrada.solicitacao().id(), null);
        assertEquals("CANCELADA", jdbc.queryForObject("select status from lista_espera where id=?", String.class,
                entrada.solicitacao().id()));
        autenticar(admin);
        assertThrows(IllegalArgumentException.class,
                () -> historico.relatorio(unidade.getId(), LocalDate.now(), LocalDate.now().plusDays(90), null, null));
    }

    @Test
    void horarioOcupadoInvalidaOfertaSemCriarSegundaReserva() throws Exception {
        autenticar(cliente);
        var entrada = fila.entrar(unidade.getId(), preferencias());
        fila.verificarOportunidades();
        var oferta = fila.minhasOfertas(entrada.solicitacao().id()).getFirst();
        Usuario outro = usuarios.save(new Usuario("Outro", "outro@teste.local", "outro@teste.local", "x",
                PerfilUsuario.CLIENTE, EstadoConta.ATIVA, null, null));
        autenticar(outro);
        agendamentos.criar(unidade.getId(), new Criacao(servico.getId(), profissional.getId(),
                oferta.inicio(), null, null, null), UUID.randomUUID().toString());
        mvc.perform(post("/api/me/lista-espera/{id}/ofertas/{ofertaId}/aceitacoes",
                entrada.solicitacao().id(), oferta.id()).with(user(UsuarioPrincipal.from(cliente)))
                .with(csrf()).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
        assertEquals("INDISPONIVEL", jdbc.queryForObject("select status from lista_espera_oferta where id=?",
                String.class, oferta.id()));
        assertEquals("ATIVA", jdbc.queryForObject("select status from lista_espera where id=?",
                String.class, entrada.solicitacao().id()));
        assertEquals(1, jdbc.queryForObject("select count(*) from atendimento", Integer.class));
    }

    @Test
    void preferenciaDesativaOfertasEMensagensNaoSaoCompartilhadas() {
        autenticar(cliente);
        var entrada = fila.entrar(unidade.getId(), preferencias());
        notificacoes.alterarPreferencias(new NotificacaoService.Preferencias(true, false));
        fila.verificarOportunidades();
        assertTrue(fila.minhasOfertas(entrada.solicitacao().id()).isEmpty());
        autenticar(admin);
        assertTrue(notificacoes.minhas(0, 10).isEmpty());
        autenticar(cliente);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> historico.relatorio(unidade.getId(), DIA_ATIVIDADE,
                        DIA_ATIVIDADE, null, null));
    }

    @Test
    void notificacoesIsolamListaLeituraEContagemPorUsuario() throws Exception {
        String chaveCliente = "teste-cliente-" + UUID.randomUUID();
        String chaveAdmin = "teste-admin-" + UUID.randomUUID();
        jdbc.update("insert into notificacao_interna(usuario_id,tipo,referencia_tipo,referencia_id,dedupe_key) "
                + "values (?,'CRIACAO','AGENDAMENTO',?,?)", cliente.getId(), 101L, chaveCliente);
        jdbc.update("insert into notificacao_interna(usuario_id,tipo,referencia_tipo,referencia_id,dedupe_key) "
                + "values (?,'CRIACAO','AGENDAMENTO',?,?)", admin.getId(), 202L, chaveAdmin);
        Long avisoCliente = jdbc.queryForObject("select id from notificacao_interna where dedupe_key=?",
                Long.class, chaveCliente);
        Long avisoAdmin = jdbc.queryForObject("select id from notificacao_interna where dedupe_key=?",
                Long.class, chaveAdmin);

        mvc.perform(get("/api/me/notificacoes").with(user(UsuarioPrincipal.from(cliente))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(avisoCliente));
        mvc.perform(get("/api/me/notificacoes/nao-lidas/contagem")
                .with(user(UsuarioPrincipal.from(cliente))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.naoLidas").value(1));
        mvc.perform(patch("/api/me/notificacoes/{id}/leitura", avisoAdmin)
                .with(user(UsuarioPrincipal.from(cliente))).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/me/notificacoes/{id}/leitura", avisoCliente)
                .with(user(UsuarioPrincipal.from(cliente))).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(patch("/api/me/notificacoes/{id}/leitura", avisoCliente)
                .with(user(UsuarioPrincipal.from(cliente))).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me/notificacoes/nao-lidas/contagem")
                .with(user(UsuarioPrincipal.from(cliente))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.naoLidas").value(0));
        mvc.perform(get("/api/me/notificacoes").with(user(UsuarioPrincipal.from(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(avisoAdmin));
    }
}
