package br.ufpa.dsai.estilomarcado.disponibilidade;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.ufpa.dsai.estilomarcado.agendamento.model.Atendimento;
import br.ufpa.dsai.estilomarcado.agendamento.model.AtendimentoStatus;
import br.ufpa.dsai.estilomarcado.agendamento.model.Cliente;
import br.ufpa.dsai.estilomarcado.agendamento.repository.AtendimentoRepository;
import br.ufpa.dsai.estilomarcado.agendamento.repository.ClienteRepository;
import br.ufpa.dsai.estilomarcado.autenticacao.model.EstadoConta;
import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;
import br.ufpa.dsai.estilomarcado.autenticacao.model.Usuario;
import br.ufpa.dsai.estilomarcado.autenticacao.repository.UsuarioRepository;
import br.ufpa.dsai.estilomarcado.autenticacao.service.EmailAutenticacaoGateway;
import br.ufpa.dsai.estilomarcado.catalogo.model.Profissional;
import br.ufpa.dsai.estilomarcado.catalogo.model.Servico;
import br.ufpa.dsai.estilomarcado.catalogo.model.Unidade;
import br.ufpa.dsai.estilomarcado.catalogo.repository.ProfissionalRepository;
import br.ufpa.dsai.estilomarcado.catalogo.repository.ServicoRepository;
import br.ufpa.dsai.estilomarcado.catalogo.repository.UnidadeRepository;
import jakarta.servlet.http.Cookie;

@SpringBootTest(properties = "app.auth.password-cost=4")
@AutoConfigureMockMvc
@Testcontainers
class DisponibilidadeIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UnidadeRepository unidades;
    @Autowired ProfissionalRepository profissionais;
    @Autowired ServicoRepository servicos;
    @Autowired ClienteRepository clientes;
    @Autowired AtendimentoRepository atendimentos;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;
    @MockitoBean EmailAutenticacaoGateway email;

    private Unidade unidade;
    private Long anaId;
    private Long ana2Id;
    private Servico servico;
    private Cliente cliente;
    private Cookie cookieAdmin;
    private Cookie cookieAna;
    private LocalDate terca;

    @BeforeEach
    void preparar() throws Exception {
        limparBanco();
        unidade = unidades.save(new Unidade("Centro"));

        Usuario admin = usuarios.save(new Usuario("Admin", "admin@example.com", "admin@example.com",
                encoder.encode("Senha123"), PerfilUsuario.ADMINISTRADOR,
                EstadoConta.ATIVA, unidade, null));
        Profissional ana = profissionais.save(new Profissional("Ana", unidade));
        anaId = ana.getId();
        Usuario contaAna = usuarios.save(new Usuario("Ana", "ana@example.com", "ana@example.com",
                encoder.encode("Senha123"), PerfilUsuario.PROFISSIONAL,
                EstadoConta.ATIVA, unidade, ana));
        ana2Id = profissionais.save(new Profissional("Beatriz", unidade)).getId();
        servico = servicos.save(new Servico(unidade, "Corte", 30, BigDecimal.TEN));
        cliente = clientes.save(new Cliente("Cliente"));

        cookieAdmin = login("admin@example.com", "Senha123");
        cookieAna = login("ana@example.com", "Senha123");
        terca = proximaTerca();
    }

    @Test
    void compoeJanelasComFolgaFeriadoAfastamentoEBloqueio() throws Exception {
        salvarJornadaTerca();

        mvc.perform(get("/api/unidades/{u}/profissionais/{p}/janelas", unidade.getId(), anaId)
                        .cookie(cookieAdmin).param("data", terca.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/unidades/{u}/profissionais/{p}/janelas", unidade.getId(), anaId)
                        .cookie(cookieAdmin)
                        .param("data", terca.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        criarExcecao("FOLGA", terca, null);
        mvc.perform(get("/api/unidades/{u}/profissionais/{p}/janelas", unidade.getId(), anaId)
                        .cookie(cookieAdmin).param("data", terca.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        jdbc.update("DELETE FROM excecao_jornada WHERE data = ?", terca);
        criarFeriado(terca);
        mvc.perform(get("/api/unidades/{u}/profissionais/{p}/janelas", unidade.getId(), anaId)
                        .cookie(cookieAdmin).param("data", terca.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        jdbc.update("DELETE FROM feriado WHERE data = ?", terca);

        mvc.perform(post("/api/unidades/{u}/profissionais/{p}/afastamentos", unidade.getId(), anaId)
                        .cookie(cookieAdmin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(afastamento(terca.minusDays(1), terca.plusDays(1), "FERIAS")))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/unidades/{u}/profissionais/{p}/janelas", unidade.getId(), anaId)
                        .cookie(cookieAdmin).param("data", terca.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        jdbc.update("DELETE FROM afastamento WHERE profissional_id = ?", anaId);

        mvc.perform(post("/api/unidades/{u}/bloqueios", unidade.getId())
                        .cookie(cookieAdmin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(bloqueio(anaId, terca, false, "13:00", "14:00")))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/unidades/{u}/profissionais/{p}/janelas", unidade.getId(), anaId)
                        .cookie(cookieAdmin).param("data", terca.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        jdbc.update("DELETE FROM bloqueio_agenda WHERE profissional_id = ?", anaId);
        mvc.perform(post("/api/unidades/{u}/bloqueios", unidade.getId())
                        .cookie(cookieAdmin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(bloqueio(null, terca, true, null, null)))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/unidades/{u}/profissionais/{p}/janelas", unidade.getId(), anaId)
                        .cookie(cookieAdmin).param("data", terca.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void recusaAlteracaoQueInvalidaAtendimentoFuturo() throws Exception {
        salvarJornadaTerca();
        Atendimento atendimento = atendimentos.save(new Atendimento(
                profissionais.findById(anaId).orElseThrow(), servico, cliente,
                terca.atTime(10, 0), AtendimentoStatus.AGENDADO));

        mvc.perform(post("/api/unidades/{u}/profissionais/{p}/excecoes", unidade.getId(), anaId)
                        .cookie(cookieAdmin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(excecao("FOLGA", terca, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflitos.length()").value(1))
                .andExpect(jsonPath("$.conflitos[0].id").value(atendimento.getId().intValue()));

        mvc.perform(post("/api/unidades/{u}/feriados", unidade.getId())
                        .cookie(cookieAdmin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(feriado(terca)))
                .andExpect(status().isConflict());

        mvc.perform(put("/api/unidades/{u}/profissionais/{p}/jornada", unidade.getId(), anaId)
                        .cookie(cookieAdmin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intervalos\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflitos[0].id").value(atendimento.getId().intValue()));

        mvc.perform(post("/api/unidades/{u}/bloqueios", unidade.getId())
                        .cookie(cookieAdmin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(bloqueio(anaId, terca, false, "09:30", "10:30")))
                .andExpect(status().isConflict());

        // Atendimento cancelado nao bloqueia a configuracao.
        atendimento.setStatus(AtendimentoStatus.CANCELADO);
        atendimentos.save(atendimento);
        mvc.perform(post("/api/unidades/{u}/profissionais/{p}/excecoes", unidade.getId(), anaId)
                        .cookie(cookieAdmin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(excecao("FOLGA", terca, null)))
                .andExpect(status().isCreated());
    }

    @Test
    void validaIntervalosSobreposicoesEDuplicidades() throws Exception {
        mvc.perform(put("/api/unidades/{u}/profissionais/{p}/jornada", unidade.getId(), anaId)
                        .cookie(cookieAdmin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intervalos\":[{\"diaSemana\":2,\"horaInicio\":\"09:00\",\"horaFim\":\"12:00\"},"
                                + "{\"diaSemana\":2,\"horaInicio\":\"11:00\",\"horaFim\":\"13:00\"}]}"))
                .andExpect(status().isConflict());

        mvc.perform(put("/api/unidades/{u}/profissionais/{p}/jornada", unidade.getId(), anaId)
                        .cookie(cookieAdmin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intervalos\":[{\"diaSemana\":2,\"horaInicio\":\"12:00\",\"horaFim\":\"09:00\"}]}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/unidades/{u}/profissionais/{p}/jornada", unidade.getId(), anaId)
                        .cookie(cookieAdmin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intervalos\":["
                                + "{\"diaSemana\":2,\"horaInicio\":\"08:00\",\"horaFim\":\"09:00\"},"
                                + "{\"diaSemana\":2,\"horaInicio\":\"09:00\",\"horaFim\":\"10:00\"},"
                                + "{\"diaSemana\":2,\"horaInicio\":\"10:00\",\"horaFim\":\"11:00\"},"
                                + "{\"diaSemana\":2,\"horaInicio\":\"11:00\",\"horaFim\":\"12:00\"},"
                                + "{\"diaSemana\":2,\"horaInicio\":\"12:00\",\"horaFim\":\"13:00\"}]}"))
                .andExpect(status().isBadRequest());

        criarFeriado(terca);
        mvc.perform(post("/api/unidades/{u}/feriados", unidade.getId())
                        .cookie(cookieAdmin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(feriado(terca)))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/unidades/{u}/profissionais/{p}/afastamentos", unidade.getId(), anaId)
                        .cookie(cookieAdmin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(afastamento(terca, terca.plusDays(5), "FERIAS")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/unidades/{u}/profissionais/{p}/afastamentos", unidade.getId(), anaId)
                        .cookie(cookieAdmin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(afastamento(terca.plusDays(3), terca.plusDays(8), "LICENCA")))
                .andExpect(status().isConflict());

        criarExcecao("JORNADA_ESPECIAL", terca.plusDays(20),
                "[{\"horaInicio\":\"10:00\",\"horaFim\":\"15:00\"}]");
        mvc.perform(post("/api/unidades/{u}/profissionais/{p}/excecoes", unidade.getId(), anaId)
                        .cookie(cookieAdmin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(excecao("FOLGA", terca.plusDays(20), null)))
                .andExpect(status().isConflict());
    }

    @Test
    void respeitaPermissoesPorPerfilFilialEProprietario() throws Exception {
        Unidade outra = unidades.save(new Unidade("Bairro"));
        usuarios.save(new Usuario("Admin Bairro", "bairro@example.com", "bairro@example.com",
                encoder.encode("Senha123"), PerfilUsuario.ADMINISTRADOR,
                EstadoConta.ATIVA, outra, null));
        Cookie cookieOutra = login("bairro@example.com", "Senha123");

        salvarJornadaTerca();

        // Administrador de outra filial nao administra a jornada de Ana.
        mvc.perform(put("/api/unidades/{u}/profissionais/{p}/jornada", unidade.getId(), anaId)
                        .cookie(cookieOutra).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intervalos\":[]}"))
                .andExpect(status().isForbidden());

        // Profissional nao altera a jornada de outro profissional.
        mvc.perform(put("/api/unidades/{u}/profissionais/{p}/jornada", unidade.getId(), ana2Id)
                        .cookie(cookieAna).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intervalos\":[]}"))
                .andExpect(status().isForbidden());

        // Profissional nao cria feriado nem bloqueio de filial.
        mvc.perform(post("/api/unidades/{u}/feriados", unidade.getId())
                        .cookie(cookieAna).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(feriado(terca.plusDays(2))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/unidades/{u}/bloqueios", unidade.getId())
                        .cookie(cookieAna).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(bloqueio(null, terca.plusDays(2), true, null, null)))
                .andExpect(status().isForbidden());

        // Sem sessao, 401.
        mvc.perform(get("/api/unidades/{u}/profissionais/{p}/jornada", unidade.getId(), anaId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void autoatendimentoUsaIdentidadeDaSessao() throws Exception {
        mvc.perform(put("/api/profissionais/me/jornada")
                        .cookie(cookieAna).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intervalos\":[{\"diaSemana\":2,\"horaInicio\":\"09:00\",\"horaFim\":\"12:00\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profissionalId").value(anaId.intValue()));

        mvc.perform(get("/api/profissionais/me/janelas")
                        .cookie(cookieAna).param("data", terca.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(post("/api/profissionais/me/excecoes")
                        .cookie(cookieAna).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(excecao("FOLGA", terca, null)))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/profissionais/me/janelas")
                        .cookie(cookieAna).param("data", terca.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Bloqueio com outro profissional e recusado mesmo no autoatendimento.
        mvc.perform(post("/api/profissionais/me/bloqueios")
                        .cookie(cookieAna).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(bloqueio(ana2Id, terca.plusDays(3), false, "09:00", "10:00")))
                .andExpect(status().isForbidden());

        // Cliente nao acessa o autoatendimento de profissional.
        clientsCookie();
    }

    private void clientsCookie() throws Exception {
        Usuario clienteUsuario = usuarios.save(new Usuario("Cliente", "cliente@example.com",
                "cliente@example.com", encoder.encode("Senha123"), PerfilUsuario.CLIENTE,
                EstadoConta.ATIVA, null, null));
        assertNotNull(clienteUsuario.getId());
        Cookie cookie = login("cliente@example.com", "Senha123");
        mvc.perform(get("/api/profissionais/me/jornada").cookie(cookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void filialInativaNaoGeraJanelas() throws Exception {
        salvarJornadaTerca();
        unidade.setAtiva(false);
        unidades.save(unidade);
        mvc.perform(get("/api/unidades/{u}/profissionais/{p}/janelas", unidade.getId(), anaId)
                        .cookie(cookieAdmin).param("data", terca.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private void salvarJornadaTerca() throws Exception {
        mvc.perform(put("/api/unidades/{u}/profissionais/{p}/jornada", unidade.getId(), anaId)
                        .cookie(cookieAdmin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intervalos\":["
                                + "{\"diaSemana\":2,\"horaInicio\":\"09:00\",\"horaFim\":\"12:00\"},"
                                + "{\"diaSemana\":2,\"horaInicio\":\"13:00\",\"horaFim\":\"18:00\"}]}"))
                .andExpect(status().isOk());
    }

    private void criarExcecao(String tipo, LocalDate data, String intervalos) throws Exception {
        mvc.perform(post("/api/unidades/{u}/profissionais/{p}/excecoes", unidade.getId(), anaId)
                        .cookie(cookieAdmin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(excecao(tipo, data, intervalos)))
                .andExpect(status().isCreated());
    }

    private void criarFeriado(LocalDate data) throws Exception {
        mvc.perform(post("/api/unidades/{u}/feriados", unidade.getId())
                        .cookie(cookieAdmin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(feriado(data)))
                .andExpect(status().isCreated());
    }

    private String excecao(String tipo, LocalDate data, String intervalos) {
        String corpo = "{\"data\":\"" + data + "\",\"tipo\":\"" + tipo + "\",\"motivo\":\"teste\"";
        if (intervalos != null) {
            corpo += ",\"intervalos\":" + intervalos;
        }
        return corpo + "}";
    }

    private String feriado(LocalDate data) {
        return "{\"data\":\"" + data + "\",\"nome\":\"Feriado de teste\"}";
    }

    private String afastamento(LocalDate inicio, LocalDate fim, String tipo) {
        return "{\"dataInicio\":\"" + inicio + "\",\"dataFim\":\"" + fim
                + "\",\"tipo\":\"" + tipo + "\"}";
    }

    private String bloqueio(Long profissionalId, LocalDate data, boolean diaInteiro,
                            String inicio, String fim) {
        String corpo = "{\"data\":\"" + data + "\",\"diaInteiro\":" + diaInteiro;
        if (profissionalId != null) {
            corpo += ",\"profissionalId\":" + profissionalId;
        }
        if (!diaInteiro) {
            corpo += ",\"horaInicio\":\"" + inicio + "\",\"horaFim\":\"" + fim + "\"";
        }
        return corpo + "}";
    }

    private LocalDate proximaTerca() {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        LocalDate candidato = hoje;
        while (candidato.getDayOfWeek() != DayOfWeek.TUESDAY) {
            candidato = candidato.plusDays(1);
        }
        return candidato.isAfter(hoje) ? candidato : candidato.plusDays(7);
    }

    private Cookie login(String emailUsuario, String senha) throws Exception {
        Cookie cookie = mvc.perform(post("/api/autenticacao/sessoes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + emailUsuario + "\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
        assertNotNull(cookie);
        return cookie;
    }

    private void limparBanco() {
        jdbc.execute("DELETE FROM excecao_jornada_intervalo");
        jdbc.execute("DELETE FROM excecao_jornada");
        jdbc.execute("DELETE FROM jornada_intervalo");
        jdbc.execute("DELETE FROM afastamento");
        jdbc.execute("DELETE FROM feriado");
        jdbc.execute("DELETE FROM bloqueio_agenda");
        jdbc.execute("DELETE FROM atendimento");
        jdbc.execute("DELETE FROM servico_profissional");
        jdbc.execute("DELETE FROM servico");
        jdbc.execute("DELETE FROM cliente");
        jdbc.execute("DELETE FROM spring_session_attributes");
        jdbc.execute("DELETE FROM spring_session");
        jdbc.execute("DELETE FROM evento_seguranca");
        jdbc.execute("DELETE FROM token_usuario");
        jdbc.execute("DELETE FROM usuario");
        jdbc.execute("DELETE FROM profissional");
        jdbc.execute("DELETE FROM unidade");
        jdbc.execute("DELETE FROM estabelecimento");
    }
}
