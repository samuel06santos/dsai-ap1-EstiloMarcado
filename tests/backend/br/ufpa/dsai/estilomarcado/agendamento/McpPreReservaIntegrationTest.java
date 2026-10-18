package br.ufpa.dsai.estilomarcado.agendamento;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.net.URI;
import java.util.regex.Pattern;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.ufpa.dsai.estilomarcado.agendamento.AgendamentoService.Criacao;
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
import br.ufpa.dsai.estilomarcado.disponibilidade.service.MotorDisponibilidadeService;
import br.ufpa.dsai.estilomarcado.mcp.McpTokenService;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "app.mcp.service-key=test-service-key")
@AutoConfigureMockMvc
@Testcontainers
class McpPreReservaIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired UnidadeRepository unidades;
    @Autowired ProfissionalRepository profissionais;
    @Autowired ServicoRepository servicos;
    @Autowired UsuarioRepository usuarios;
    @Autowired PreReservaService preReservas;
    @Autowired AgendamentoService agendamentos;
    @Autowired MotorDisponibilidadeService motor;
    @Autowired McpTokenService tokens;
    @Autowired ObjectMapper mapper;
    Unidade unidade;
    Profissional profissional;
    Servico servico;
    Usuario cliente;
    LocalDateTime inicio;

    @BeforeEach
    void preparar() {
        SecurityContextHolder.clearContext();
        TestSecurityContextHolder.clearContext();
        jdbc.execute("TRUNCATE TABLE estabelecimento, usuario, mcp_cliente_oauth, "
                + "mcp_confirmacao_consumida RESTART IDENTITY CASCADE");
        unidade = unidades.save(new Unidade("Centro"));
        profissional = profissionais.save(new Profissional("Ana", unidade));
        servico = new Servico(unidade, "Corte", 30, new BigDecimal("50.00"));
        servico.setIntervaloMinutos(15);
        servico.substituirProfissionais(Set.of(profissional));
        servico = servicos.saveAndFlush(servico);
        jdbc.update("insert into jornada_intervalo(profissional_id,dia_semana,hora_inicio,hora_fim) "
                + "values (?,1,'09:00','12:00')", profissional.getId());
        cliente = usuarios.saveAndFlush(new Usuario("Cliente", "cliente@mcp.test", "cliente@mcp.test", "x",
                PerfilUsuario.CLIENTE, EstadoConta.ATIVA, null, null));
        LocalDate segunda = LocalDate.now(ZoneId.of("America/Sao_Paulo"))
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        inicio = segunda.atTime(9, 0);
    }

    private void autenticar(Usuario usuario) {
        UsuarioPrincipal principal = UsuarioPrincipal.from(usuario);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void registroOauthAceitaMetadadosAdicionaisDoCodex() throws Exception {
        mvc.perform(post("/oauth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"client_name":"Codex","redirect_uris":["http://127.0.0.1:61649/callback/teste"],
                 "grant_types":["authorization_code"],"token_endpoint_auth_method":"none",
                 "response_types":["code"],"scope":"mcp:read mcp:write",
                 "application_type":"native"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.client_id").exists());
    }

    @Test
    void preReservaBloqueiaWebECheckoutConsomeUmaVez() {
        var hold = preReservas.criar(new PreReservaService.Pedido(unidade.getId(), servico.getId(), inicio),
                UUID.randomUUID().toString());
        assertEquals(1, jdbc.queryForObject("select count(*) from agenda_ocupacao where ativa", Integer.class));
        assertFalse(motor.consultar(unidade.getId(), servico.getId(), inicio.toLocalDate(),
                profissional.getId()).horarios().stream().anyMatch(h -> h.inicio().equals(inicio)));
        autenticar(cliente);
        assertThrows(AgendamentoConflitoException.class, () -> agendamentos.criar(unidade.getId(),
                new Criacao(servico.getId(), profissional.getId(), inicio, null, null, null),
                UUID.randomUUID().toString()));
        String token = hold.checkoutUrl().split("#t=")[1];
        var primeira = preReservas.confirmar(token);
        AgendamentoConflitoException repetida = assertThrows(AgendamentoConflitoException.class,
                () -> preReservas.confirmar(token));
        assertEquals("PRE_RESERVA_CONSUMIDA", repetida.getCodigo());
        assertEquals(1, jdbc.queryForObject("select count(*) from atendimento", Integer.class));
        assertEquals("AGENDADO", primeira.status().name());
    }

    @Test
    void expiracaoLiberaHorarioSemLimpezaAgendada() {
        var hold = preReservas.criar(new PreReservaService.Pedido(unidade.getId(), servico.getId(), inicio),
                UUID.randomUUID().toString());
        jdbc.update("update pre_reserva set criado_em=clock_timestamp()-interval '11 minutes', "
                + "expira_em=clock_timestamp()-interval '1 minute' where id=?", hold.id());
        assertTrue(motor.consultar(unidade.getId(), servico.getId(), inicio.toLocalDate(),
                profissional.getId()).horarios().stream().anyMatch(h -> h.inicio().equals(inicio)));
        autenticar(cliente);
        var criada = agendamentos.criar(unidade.getId(),
                new Criacao(servico.getId(), profissional.getId(), inicio, null, null, null),
                UUID.randomUUID().toString());
        assertNotNull(criada.id());
        assertEquals("EXPIRADA", jdbc.queryForObject("select estado from pre_reserva where id=?", String.class, hold.id()));
    }

    @Test
    void checkoutExpiradoNaoCriaAtendimento() {
        var hold = preReservas.criar(new PreReservaService.Pedido(unidade.getId(), servico.getId(), inicio),
                UUID.randomUUID().toString());
        jdbc.update("update pre_reserva set criado_em=clock_timestamp()-interval '11 minutes', "
                + "expira_em=clock_timestamp()-interval '1 second' where id=?", hold.id());
        autenticar(cliente);
        AgendamentoConflitoException vencida = assertThrows(AgendamentoConflitoException.class,
                () -> preReservas.confirmar(hold.checkoutUrl().split("#t=")[1]));
        assertEquals("PRE_RESERVA_EXPIRADA", vencida.getCodigo());
        assertEquals(0, jdbc.queryForObject("select count(*) from atendimento", Integer.class));
    }

    @Test
    void bearerValidaContaEscopoERevogacao() throws Exception {
        jdbc.update("insert into mcp_cliente_oauth(id,nome,redirect_uris) values ('teste','Teste','http://127.0.0.1:1234/callback')");
        jdbc.update("insert into mcp_autorizacao(usuario_id,cliente_id,versao) values (?, 'teste',1)", cliente.getId());
        String jwt = tokens.emitir(cliente.getId(), "teste", "mcp:read mcp:write");
        assertEquals(cliente.getId(), tokens.validar("Bearer " + jwt).principal().id());
        mvc.perform(get("/api/mcp/internal/me/lista-espera").header("X-MCP-Service-Key", "test-service-key"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.codigo").value("AUTENTICACAO_MCP_NECESSARIA"));
        mvc.perform(get("/api/mcp/internal/me/lista-espera").header("X-MCP-Service-Key", "test-service-key")
                .header("Authorization", "Bearer invalido")) .andExpect(status().isUnauthorized());
        jdbc.update("update mcp_autorizacao set revogado_em=clock_timestamp(),versao=2 where usuario_id=?", cliente.getId());
        assertThrows(org.springframework.security.authentication.BadCredentialsException.class,
                () -> tokens.validar("Bearer " + jwt));
    }

    @Test
    void bearerNoHeaderIsolaReservasDeDoisClientes() throws Exception {
        Usuario outra = usuarios.saveAndFlush(new Usuario("Outra", "outra@mcp.test", "outra@mcp.test", "x",
                PerfilUsuario.CLIENTE, EstadoConta.ATIVA, null, null));
        autenticar(cliente);
        long proprio = agendamentos.criar(unidade.getId(),
                new Criacao(servico.getId(), profissional.getId(), inicio, null, null, null),
                UUID.randomUUID().toString()).id();
        autenticar(outra);
        long alheio = agendamentos.criar(unidade.getId(),
                new Criacao(servico.getId(), profissional.getId(), inicio.plusMinutes(45), null, null, null),
                UUID.randomUUID().toString()).id();
        SecurityContextHolder.clearContext();
        jdbc.update("insert into mcp_cliente_oauth(id,nome,redirect_uris) values ('teste','Teste','http://127.0.0.1:1234/callback')");
        jdbc.update("insert into mcp_autorizacao(usuario_id,cliente_id,versao) values (?, 'teste',1)", cliente.getId());
        String bearer = "Bearer " + tokens.emitir(cliente.getId(), "teste", "mcp:read");
        mvc.perform(get("/api/mcp/internal/me/agendamentos")
                .header("X-MCP-Service-Key", "test-service-key").header("Authorization", bearer)
                .param("de", inicio.toLocalDate().toString()).param("ate", inicio.toLocalDate().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(proprio))
                .andExpect(jsonPath("$[1]").doesNotExist());
        mvc.perform(get("/api/mcp/internal/agendamentos/{id}", alheio)
                .header("X-MCP-Service-Key", "test-service-key").header("Authorization", bearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void preReservaEAgendamentoWebDisputamMesmaAgenda() throws Exception {
        var largada = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var pre = executor.submit(() -> {
                largada.await();
                try {
                    preReservas.criar(new PreReservaService.Pedido(unidade.getId(), servico.getId(), inicio),
                            UUID.randomUUID().toString());
                    return true;
                } catch (AgendamentoConflitoException ex) { return false; }
            });
            var web = executor.submit(() -> {
                autenticar(cliente);
                largada.await();
                try {
                    agendamentos.criar(unidade.getId(),
                            new Criacao(servico.getId(), profissional.getId(), inicio, null, null, null),
                            UUID.randomUUID().toString());
                    return true;
                } catch (AgendamentoConflitoException ex) { return false; }
                finally { SecurityContextHolder.clearContext(); }
            });
            largada.countDown();
            assertEquals(1, (pre.get() ? 1 : 0) + (web.get() ? 1 : 0));
            assertEquals(1, jdbc.queryForObject("select count(*) from agenda_ocupacao where ativa", Integer.class));
        }
    }

    @Test
    void duasPreReservasConcorrentesNaoOcupamMesmoProfissional() throws Exception {
        var largada = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var primeira = executor.submit(() -> {
                largada.await();
                try {
                    preReservas.criar(new PreReservaService.Pedido(unidade.getId(), servico.getId(), inicio),
                            UUID.randomUUID().toString());
                    return true;
                } catch (AgendamentoConflitoException ex) { return false; }
            });
            var segunda = executor.submit(() -> {
                largada.await();
                try {
                    preReservas.criar(new PreReservaService.Pedido(unidade.getId(), servico.getId(), inicio),
                            UUID.randomUUID().toString());
                    return true;
                } catch (AgendamentoConflitoException ex) { return false; }
            });
            largada.countDown();
            assertEquals(1, (primeira.get() ? 1 : 0) + (segunda.get() ? 1 : 0));
            assertEquals(1, jdbc.queryForObject("select count(*) from agenda_ocupacao where ativa", Integer.class));
        }
    }

    @Test
    void checkoutExigeSessaoWebECsrf() throws Exception {
        var hold = preReservas.criar(new PreReservaService.Pedido(unidade.getId(), servico.getId(), inicio),
                UUID.randomUUID().toString());
        String token = hold.checkoutUrl().split("#t=")[1];
        String body = "{\"token\":\"" + token + "\"}";
        mvc.perform(post("/api/checkout/pre-reservas/confirmacoes").contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/checkout/pre-reservas/confirmacoes").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/checkout/pre-reservas/confirmacoes")
                .with(user(UsuarioPrincipal.from(cliente))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AGENDADO"));
    }

    @Test
    void codigoPkceSoViraBearerAposTrocaPeloClienteMcp() throws Exception {
        String redirect = "http://127.0.0.1:1234/callback";
        String verifier = "v".repeat(43);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        jdbc.update("insert into mcp_cliente_oauth(id,nome,redirect_uris) values ('teste','Teste',?)", redirect);
        var consentimento = mvc.perform(get("/oauth/authorize").with(user(UsuarioPrincipal.from(cliente)))
                .param("response_type", "code").param("client_id", "teste").param("redirect_uri", redirect)
                .param("code_challenge", challenge).param("code_challenge_method", "S256")
                .param("scope", "mcp:read mcp:write").param("state", "estado123")
                .param("resource", tokens.audience()))
                .andExpect(status().isOk()).andReturn();
        assertTrue(consentimento.getResponse().getContentAsString().contains("Autorizar aplicativo"));
        String html = consentimento.getResponse().getContentAsString();
        assertTrue(html.contains("/oauth/consent.css"));
        assertTrue(consentimento.getResponse().getHeader("Content-Security-Policy")
                .contains("form-action 'self' http://127.0.0.1:1234"));
        var matcher = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(html);
        assertTrue(matcher.find(), "formulario deve conter token CSRF");
        String formCsrf = matcher.group(1);
        var sessionCookie = consentimento.getResponse().getCookie("SESSION");
        assertNotNull(sessionCookie);
        mvc.perform(get("/oauth/consent.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));
        mvc.perform(post("/oauth/authorize").with(user(UsuarioPrincipal.from(cliente)))
                .cookie(sessionCookie).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("response_type", "code").param("client_id", "teste").param("redirect_uri", redirect)
                .param("code_challenge", challenge).param("code_challenge_method", "S256")
                .param("scope", "mcp:read mcp:write").param("state", "estado123")
                .param("resource", tokens.audience()).param("decisao", "permitir"))
                .andExpect(status().isForbidden());
        var resposta = mvc.perform(post("/oauth/authorize").with(user(UsuarioPrincipal.from(cliente)))
                .cookie(sessionCookie).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("response_type", "code").param("client_id", "teste").param("redirect_uri", redirect)
                .param("code_challenge", challenge).param("code_challenge_method", "S256")
                .param("scope", "mcp:read mcp:write").param("state", "estado123")
                .param("resource", tokens.audience()).param("decisao", "permitir")
                .param("_csrf", formCsrf))
                .andExpect(status().isFound()).andReturn();
        String location = resposta.getResponse().getHeader("Location");
        assertNotNull(location);
        String code = new URI(location).getRawQuery().split("code=")[1].split("&")[0];
        mvc.perform(post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code").param("code", code)
                .param("client_id", "teste").param("redirect_uri", redirect)
                .param("code_verifier", "errado".repeat(8)))
                .andExpect(status().isBadRequest());
        var troca = mvc.perform(post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code").param("code", code)
                .param("client_id", "teste").param("redirect_uri", redirect)
                .param("code_verifier", verifier))
                .andExpect(status().isOk()).andReturn();
        String jwt = (String) mapper.readValue(troca.getResponse().getContentAsByteArray(), java.util.Map.class)
                .get("access_token");
        assertEquals(cliente.getId(), tokens.validar("Bearer " + jwt).principal().id());
        mvc.perform(get("/api/mcp/internal/me/lista-espera")
                .header("X-MCP-Service-Key", "test-service-key")
                .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
        mvc.perform(post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code").param("code", code)
                .param("client_id", "teste").param("redirect_uri", redirect)
                .param("code_verifier", verifier)).andExpect(status().isBadRequest());
    }

    @Test
    void confirmacaoDeEscritaMcpSoPodeSerUsadaUmaVez() throws Exception {
        autenticar(cliente);
        long id = agendamentos.criar(unidade.getId(),
                new Criacao(servico.getId(), profissional.getId(), inicio, null, null, null),
                UUID.randomUUID().toString()).id();
        SecurityContextHolder.clearContext();
        jdbc.update("insert into mcp_cliente_oauth(id,nome,redirect_uris) values ('teste','Teste','http://127.0.0.1:1234/callback')");
        jdbc.update("insert into mcp_autorizacao(usuario_id,cliente_id,versao) values (?, 'teste',1)", cliente.getId());
        String bearer = "Bearer " + tokens.emitir(cliente.getId(), "teste", "mcp:write");
        String body = "{\"motivo\":\"pedido do cliente\",\"confirmacaoNonce\":\"" + UUID.randomUUID() + "\"}";
        mvc.perform(post("/api/mcp/internal/agendamentos/{id}/cancelamentos", id)
                .header("X-MCP-Service-Key", "test-service-key").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(post("/api/mcp/internal/agendamentos/{id}/cancelamentos", id)
                .header("X-MCP-Service-Key", "test-service-key").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CONFIRMACAO_REUTILIZADA"));
    }
}
