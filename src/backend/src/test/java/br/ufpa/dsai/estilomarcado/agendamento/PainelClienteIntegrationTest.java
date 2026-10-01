package br.ufpa.dsai.estilomarcado.agendamento;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

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
class PainelClienteIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UsuarioRepository usuarios;
    @Autowired UnidadeRepository unidades;
    @Autowired ProfissionalRepository profissionais;
    @Autowired ServicoRepository servicos;
    @Autowired ClienteRepository clientes;
    @Autowired AtendimentoRepository atendimentos;
    @Autowired PasswordEncoder encoder;
    @MockitoBean EmailAutenticacaoGateway email;

    private Unidade unidade;
    private Profissional profissional;
    private Servico servico;
    private Cliente cliente;
    private Cookie cookieCliente;
    private LocalDate hoje;

    @BeforeEach
    void preparar() throws Exception {
        jdbc.execute("""
                TRUNCATE TABLE agendamento_evento, agendamento_idempotencia, atendimento,
                servico_profissional, servico, cliente, jornada_intervalo, excecao_jornada_intervalo,
                excecao_jornada, afastamento, feriado, bloqueio_agenda, spring_session_attributes,
                spring_session, evento_seguranca, token_usuario, usuario, profissional, unidade,
                estabelecimento RESTART IDENTITY CASCADE
                """);
        unidade = unidades.save(new Unidade("Centro"));
        profissional = profissionais.save(new Profissional("Ana", unidade));
        servico = new Servico(unidade, "Corte", 30, new BigDecimal("50.00"));
        servico.setIntervaloMinutos(15);
        servico.substituirProfissionais(Set.of(profissional));
        servico = servicos.saveAndFlush(servico);
        Usuario clienteUsuario = usuarios.save(new Usuario("Cliente", "cliente@test.local",
                "cliente@test.local", encoder.encode("Senha123"), PerfilUsuario.CLIENTE,
                EstadoConta.ATIVA, null, null));
        cliente = new Cliente("Cliente");
        cliente.setUsuario(clienteUsuario);
        cliente = clientes.save(cliente);
        cookieCliente = login("cliente@test.local", "Senha123");
        hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    }

    @Test
    void painelVazioParaContaSemVinculo() throws Exception {
        Usuario semVinculo = usuarios.save(new Usuario("Nova", "nova@test.local", "nova@test.local",
                encoder.encode("Senha123"), PerfilUsuario.CLIENTE, EstadoConta.ATIVA, null, null));
        assertNotNull(semVinculo.getId());
        Cookie cookie = login("nova@test.local", "Senha123");

        mvc.perform(get("/api/me/painel").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agora").exists())
                .andExpect(jsonPath("$.cliente.nome").value("Nova"))
                .andExpect(jsonPath("$.proximo").doesNotExist())
                .andExpect(jsonPath("$.proximos.length()").value(0))
                .andExpect(jsonPath("$.historico.length()").value(0))
                .andExpect(jsonPath("$.resumo.proximosAtivos").value(0))
                .andExpect(jsonPath("$.resumo.realizados").value(0))
                .andExpect(jsonPath("$.resumo.cancelados").value(0));
    }

    @Test
    void painelOrdenaProximosEHistoricoComNomes() throws Exception {
        Atendimento futuro1 = salvar(hoje.plusDays(3).atTime(10, 0), AtendimentoStatus.AGENDADO);
        Atendimento futuro2 = salvar(hoje.plusDays(5).atTime(9, 0), AtendimentoStatus.CONFIRMADO);
        salvar(hoje.minusDays(2).atTime(10, 0), AtendimentoStatus.CONFIRMADO);
        salvar(hoje.minusDays(1).atTime(10, 0), AtendimentoStatus.CANCELADO);

        mvc.perform(get("/api/me/painel").cookie(cookieCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proximo.id").value(futuro1.getId().intValue()))
                .andExpect(jsonPath("$.proximos.length()").value(2))
                .andExpect(jsonPath("$.proximos[0].id").value(futuro1.getId().intValue()))
                .andExpect(jsonPath("$.proximos[1].id").value(futuro2.getId().intValue()))
                .andExpect(jsonPath("$.proximos[0].unidadeNome").value("Centro"))
                .andExpect(jsonPath("$.proximos[0].profissionalNome").value("Ana"))
                .andExpect(jsonPath("$.proximos[0].servicoAtivo").value(true))
                .andExpect(jsonPath("$.proximos[0].unidadeAtiva").value(true))
                .andExpect(jsonPath("$.proximos[0].fim").exists())
                .andExpect(jsonPath("$.proximos[0].clienteId").doesNotExist())
                .andExpect(jsonPath("$.historico.length()").value(2))
                .andExpect(jsonPath("$.resumo.proximosAtivos").value(2))
                .andExpect(jsonPath("$.resumo.realizados").value(1))
                .andExpect(jsonPath("$.resumo.cancelados").value(1));
    }

    @Test
    void respeitaLimitesEAutorizacao() throws Exception {
        mvc.perform(get("/api/me/painel").param("limiteProximos", "0").cookie(cookieCliente))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/me/painel").param("limiteHistorico", "21").cookie(cookieCliente))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/me/painel")).andExpect(status().isUnauthorized());

        Usuario contaProfissional = usuarios.save(new Usuario("Ana", "ana@test.local",
                "ana@test.local", encoder.encode("Senha123"), PerfilUsuario.PROFISSIONAL,
                EstadoConta.ATIVA, unidade, profissional));
        assertNotNull(contaProfissional.getId());
        Cookie cookieProfissional = login("ana@test.local", "Senha123");
        mvc.perform(get("/api/me/painel").cookie(cookieProfissional))
                .andExpect(status().isForbidden());
    }

    @Test
    void listaFiltraPorFilialServicoEIsolaOutroCliente() throws Exception {
        Atendimento doCliente = salvar(hoje.plusDays(3).atTime(10, 0), AtendimentoStatus.AGENDADO);
        Servico servico2 = servicos.save(new Servico(unidade, "Barba", 20, new BigDecimal("30.00")));
        Atendimento outroServico = salvar(servico2, hoje.plusDays(4).atTime(10, 0),
                AtendimentoStatus.AGENDADO);

        String de = hoje.minusDays(10).toString();
        String ate = hoje.plusDays(10).toString();
        mvc.perform(get("/api/me/agendamentos").cookie(cookieCliente)
                        .param("de", de).param("ate", ate).param("servicoId", servico2.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(outroServico.getId().intValue()))
                .andExpect(jsonPath("$[0].servicoNome").value("Barba"));
        mvc.perform(get("/api/me/agendamentos").cookie(cookieCliente)
                        .param("de", de).param("ate", ate)
                        .param("unidadeId", String.valueOf(unidade.getId() + 999)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Outro cliente nao enxerga nem acessa os atendimentos alheios.
        Usuario outra = usuarios.save(new Usuario("Outra", "outra@test.local", "outra@test.local",
                encoder.encode("Senha123"), PerfilUsuario.CLIENTE, EstadoConta.ATIVA, null, null));
        Cliente clienteOutra = new Cliente("Outra");
        clienteOutra.setUsuario(outra);
        clientes.save(clienteOutra);
        Cookie cookieOutra = login("outra@test.local", "Senha123");
        mvc.perform(get("/api/me/painel").cookie(cookieOutra))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proximos.length()").value(0));
        mvc.perform(get("/api/agendamentos/{id}", doCliente.getId()).cookie(cookieOutra))
                .andExpect(status().isNotFound());
    }

    private Atendimento salvar(java.time.LocalDateTime inicio, AtendimentoStatus status) {
        return salvar(servico, inicio, status);
    }

    private Atendimento salvar(Servico alvo, java.time.LocalDateTime inicio, AtendimentoStatus status) {
        return atendimentos.saveAndFlush(new Atendimento(profissional, alvo, cliente, inicio, status));
    }

    private Cookie login(String emailUsuario, String senha) throws Exception {
        Cookie cookie = mvc.perform(post("/api/autenticacao/sessoes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + emailUsuario + "\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
        assertNotNull(cookie);
        return cookie;
    }
}
