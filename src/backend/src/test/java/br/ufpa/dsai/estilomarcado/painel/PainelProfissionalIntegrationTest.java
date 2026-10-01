package br.ufpa.dsai.estilomarcado.painel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import br.ufpa.dsai.estilomarcado.catalogo.model.Profissional;
import br.ufpa.dsai.estilomarcado.catalogo.model.Servico;
import br.ufpa.dsai.estilomarcado.catalogo.model.Unidade;
import br.ufpa.dsai.estilomarcado.catalogo.repository.ProfissionalRepository;
import br.ufpa.dsai.estilomarcado.catalogo.repository.ServicoRepository;
import br.ufpa.dsai.estilomarcado.catalogo.repository.UnidadeRepository;
import br.ufpa.dsai.estilomarcado.painel.api.dto.AgendaAtendimentoResponse;
import br.ufpa.dsai.estilomarcado.painel.api.exception.PerfilNaoIdentificadoException;
import br.ufpa.dsai.estilomarcado.painel.service.PainelProfissionalService;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PainelProfissionalIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    PainelProfissionalService painelProfissionalService;

    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    UnidadeRepository unidadeRepository;

    @Autowired
    ProfissionalRepository profissionalRepository;

    @Autowired
    ServicoRepository servicoRepository;

    @Autowired
    ClienteRepository clienteRepository;

    @Autowired
    AtendimentoRepository atendimentoRepository;

    private static final LocalDate DIA = LocalDate.of(2026, 10, 1);

    private Profissional profissional;
    private Profissional outroProfissional;
    private Servico servico;
    private Cliente cliente;

    @BeforeEach
    void prepararBanco() {
        jdbcTemplate.execute("DELETE FROM spring_session_attributes");
        jdbcTemplate.execute("DELETE FROM spring_session");
        jdbcTemplate.execute("DELETE FROM evento_seguranca");
        jdbcTemplate.execute("DELETE FROM token_usuario");
        jdbcTemplate.execute("DELETE FROM usuario");
        jdbcTemplate.execute("DELETE FROM atendimento");
        jdbcTemplate.execute("DELETE FROM servico_profissional");
        jdbcTemplate.execute("DELETE FROM servico");
        jdbcTemplate.execute("DELETE FROM cliente");
        jdbcTemplate.execute("DELETE FROM profissional");
        jdbcTemplate.execute("DELETE FROM unidade");

        Unidade unidade = unidadeRepository.save(new Unidade("Unidade Centro"));
        profissional = profissionalRepository.save(new Profissional("Ana", unidade));
        outroProfissional = profissionalRepository.save(new Profissional("Bruno", unidade));
        servico = servicoRepository.save(new Servico(unidade, "Corte de cabelo", 30, new BigDecimal("50.00")));
        cliente = clienteRepository.save(new Cliente("Maria"));
    }

    @Test
    void deveListarSomenteOsAtendimentosDoProfissionalOrdenadosPorHorario() {
        agendar(profissional, LocalDateTime.of(2026, 10, 1, 14, 0), AtendimentoStatus.CONFIRMADO);
        agendar(profissional, LocalDateTime.of(2026, 10, 1, 9, 0), AtendimentoStatus.CONFIRMADO);
        agendar(outroProfissional, LocalDateTime.of(2026, 10, 1, 10, 0), AtendimentoStatus.CONFIRMADO);

        List<AgendaAtendimentoResponse> agenda =
                painelProfissionalService.agendaDoDia(profissional.getId(), DIA);

        assertEquals(2, agenda.size());
        assertEquals(LocalDateTime.of(2026, 10, 1, 9, 0), agenda.get(0).inicio());
        assertEquals(LocalDateTime.of(2026, 10, 1, 14, 0), agenda.get(1).inicio());
    }

    @Test
    void deveMostrarHorarioServicoClienteEStatus() {
        agendar(profissional, LocalDateTime.of(2026, 10, 1, 9, 0), AtendimentoStatus.CONFIRMADO);

        AgendaAtendimentoResponse item =
                painelProfissionalService.agendaDoDia(profissional.getId(), DIA).get(0);

        assertEquals(LocalDateTime.of(2026, 10, 1, 9, 0), item.inicio());
        assertEquals("Corte de cabelo", item.servico());
        assertEquals("Maria", item.cliente());
        assertEquals("CONFIRMADO", item.status());
    }

    @Test
    void deveExibirAtendimentoCanceladoComStatusCancelado() {
        agendar(profissional, LocalDateTime.of(2026, 10, 1, 9, 0), AtendimentoStatus.CANCELADO);

        AgendaAtendimentoResponse item =
                painelProfissionalService.agendaDoDia(profissional.getId(), DIA).get(0);

        assertEquals("CANCELADO", item.status());
    }

    @Test
    void naoDeveIncluirAtendimentosDeOutrosDias() {
        agendar(profissional, LocalDateTime.of(2026, 10, 2, 9, 0), AtendimentoStatus.CONFIRMADO);

        assertTrue(painelProfissionalService.agendaDoDia(profissional.getId(), DIA).isEmpty());
    }

    @Test
    void deveRejeitarRequisicaoSemProfissionalIdentificado() {
        assertThrows(PerfilNaoIdentificadoException.class,
                () -> painelProfissionalService.agendaDoDia(null, DIA));
    }

    @Test
    void rotaDeAgendaUsaVinculoDaSessaoEIgnoraCabecalhoAdulterado() throws Exception {
        agendar(profissional, LocalDateTime.of(2026, 10, 1, 9, 0), AtendimentoStatus.CONFIRMADO);
        agendar(outroProfissional, LocalDateTime.of(2026, 10, 1, 10, 0), AtendimentoStatus.CONFIRMADO);
        var sessao = login(profissional, PerfilUsuario.PROFISSIONAL);

        mockMvc.perform(get("/api/painel/agenda?data=2026-10-01")
                        .cookie(sessao).header("X-Profissional-Id", outroProfissional.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].inicio").value("2026-10-01T09:00:00"));
        mockMvc.perform(get("/api/painel/agenda?data=2026-10-01"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rotaDeAgendaRejeitaClienteEProfissionalInativo() throws Exception {
        var clienteSessao = login(null, PerfilUsuario.CLIENTE);
        mockMvc.perform(get("/api/painel/agenda?data=2026-10-01").cookie(clienteSessao))
                .andExpect(status().isForbidden());

        var profissionalSessao = login(profissional, PerfilUsuario.PROFISSIONAL);
        profissional.setAtivo(false);
        profissionalRepository.save(profissional);
        mockMvc.perform(get("/api/painel/agenda?data=2026-10-01").cookie(profissionalSessao))
                .andExpect(status().isForbidden());
    }

    private jakarta.servlet.http.Cookie login(Profissional vinculo, PerfilUsuario perfil) throws Exception {
        String email = perfil.name().toLowerCase() + usuarioRepository.count() + "@example.com";
        usuarioRepository.save(new Usuario("Pessoa Teste", email, email,
                passwordEncoder.encode("Senha123"), perfil, EstadoConta.ATIVA,
                vinculo == null ? null : vinculo.getUnidade(), vinculo));
        return mockMvc.perform(post("/api/autenticacao/sessoes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"Senha123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
    }

    private void agendar(Profissional profissional, LocalDateTime inicio, AtendimentoStatus status) {
        atendimentoRepository.save(new Atendimento(profissional, servico, cliente, inicio, status));
    }
}
