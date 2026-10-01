package br.ufpa.dsai.estilomarcado.painel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.ufpa.dsai.estilomarcado.agendamento.model.Atendimento;
import br.ufpa.dsai.estilomarcado.agendamento.model.AtendimentoStatus;
import br.ufpa.dsai.estilomarcado.agendamento.model.Cliente;
import br.ufpa.dsai.estilomarcado.agendamento.repository.AtendimentoRepository;
import br.ufpa.dsai.estilomarcado.agendamento.repository.ClienteRepository;
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
@Testcontainers
class PainelProfissionalIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    PainelProfissionalService painelProfissionalService;

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

    private void agendar(Profissional profissional, LocalDateTime inicio, AtendimentoStatus status) {
        atendimentoRepository.save(new Atendimento(profissional, servico, cliente, inicio, status));
    }
}
