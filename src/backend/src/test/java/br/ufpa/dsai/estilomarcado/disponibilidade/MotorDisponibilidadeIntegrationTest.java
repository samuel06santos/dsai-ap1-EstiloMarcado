package br.ufpa.dsai.estilomarcado.disponibilidade;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MotorDisponibilidadeIntegrationTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @TestConfiguration
    static class RelogioTeste {
        @Bean @Primary
        Clock relogioFixo() {
            return Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UnidadeRepository unidades;
    @Autowired ProfissionalRepository profissionais;
    @Autowired ServicoRepository servicos;
    @Autowired AtendimentoRepository atendimentos;
    @Autowired ClienteRepository clientes;

    private Unidade unidade;
    private Profissional ana;
    private Profissional bia;
    private Servico servico;
    private LocalDate dia;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE TABLE atendimento, servico_profissional, servico, cliente, "
                + "jornada_intervalo, excecao_jornada_intervalo, excecao_jornada, "
                + "afastamento, feriado, bloqueio_agenda, usuario, profissional, unidade, "
                + "estabelecimento RESTART IDENTITY CASCADE");
        unidade = unidades.save(new Unidade("Centro"));
        ana = profissionais.save(new Profissional("Ana", unidade));
        bia = profissionais.save(new Profissional("Bia", unidade));
        servico = new Servico(unidade, "Corte", 45, BigDecimal.TEN);
        servico.setIntervaloMinutos(15);
        servico.substituirProfissionais(Set.of(ana, bia));
        servico = servicos.saveAndFlush(servico);
        dia = LocalDate.of(2026, 10, 5);
        jornada(ana, "09:00", "12:00");
        jornada(bia, "09:00", "10:00");
    }

    @Test
    void consultaPublicaOrdenadaEFiltradaSemDadosInternos() throws Exception {
        mvc.perform(get(rota()).param("data", dia.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.fusoHorario").value("America/Sao_Paulo"))
                .andExpect(jsonPath("$.horarios[0].profissionalId").value(ana.getId()))
                .andExpect(jsonPath("$.horarios[1].profissionalId").value(bia.getId()))
                .andExpect(jsonPath("$.horarios[0].inicio").value("2026-10-05T09:00:00"))
                .andExpect(jsonPath("$.horarios[0].clienteId").doesNotExist());
        mvc.perform(get(rota()).param("data", dia.toString())
                        .param("profissionalId", bia.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horarios.length()").value(2))
                .andExpect(jsonPath("$.horarios[0].profissionalId").value(bia.getId()));
    }

    @Test
    void atendimentoAtivoUsaDuracaoHistoricaECanceladoNaoOcupa() throws Exception {
        Cliente cliente = clientes.save(new Cliente("Cliente"));
        Atendimento reservado = atendimentos.saveAndFlush(new Atendimento(ana, servico, cliente,
                dia.atTime(9, 0), AtendimentoStatus.CONFIRMADO));
        servico.setDuracaoMinutos(30);
        servico.setIntervaloMinutos(0);
        servicos.saveAndFlush(servico);
        mvc.perform(get(rota()).param("data", dia.toString())
                        .param("profissionalId", ana.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horarios[0].inicio").value("2026-10-05T10:00:00"));
        reservado.setStatus(AtendimentoStatus.CANCELADO);
        atendimentos.saveAndFlush(reservado);
        mvc.perform(get(rota()).param("data", dia.toString())
                        .param("profissionalId", ana.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horarios[0].inicio").value("2026-10-05T09:00:00"));
    }

    @Test
    void intervaloDoDiaAnteriorTambemOcupaManhaSeguinte() throws Exception {
        Cliente cliente = clientes.save(new Cliente("Cliente"));
        jdbc.update("""
                insert into atendimento(profissional_id,servico_id,cliente_id,inicio,status,
                    duracao_minutos,intervalo_minutos,servico_nome,preco_acordado,fuso_horario_agendamento)
                values (?,?,?,'2026-10-04 23:30','AGENDADO',30,600,'Corte',10,'America/Sao_Paulo')
                """, ana.getId(), servico.getId(), cliente.getId());
        mvc.perform(get(rota()).param("data", dia.toString())
                        .param("profissionalId", ana.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horarios[0].inicio").value("2026-10-05T10:00:00"));
    }

    @Test
    void bloqueioEFeriadoRemovemHorarios() throws Exception {
        Long autorId = jdbc.queryForObject("INSERT INTO usuario "
                + "(nome, email, email_normalizado, perfil, estado, unidade_id) "
                + "VALUES ('Admin', 'admin@teste.local', 'admin@teste.local', "
                + "'ADMINISTRADOR', 'ATIVA', ?) RETURNING id", Long.class, unidade.getId());
        jdbc.update("INSERT INTO bloqueio_agenda (unidade_id, profissional_id, data, dia_inteiro, "
                + "hora_inicio, hora_fim, criado_por) VALUES (?, ?, ?, false, '09:00', '10:00', ?)",
                unidade.getId(), ana.getId(), dia, autorId);
        mvc.perform(get(rota()).param("data", dia.toString())
                        .param("profissionalId", ana.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horarios[0].inicio").value("2026-10-05T10:00:00"));
        jdbc.update("INSERT INTO feriado (unidade_id, data, nome) VALUES (?, ?, 'Feriado')",
                unidade.getId(), dia);
        mvc.perform(get(rota()).param("data", dia.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horarios.length()").value(0));
    }

    @Test
    void folgaJornadaEspecialEAfastamentoRespeitamCadaProfissional() throws Exception {
        jdbc.update("INSERT INTO excecao_jornada (profissional_id, data, tipo) "
                + "VALUES (?, ?, 'FOLGA')", ana.getId(), dia);
        mvc.perform(get(rota()).param("data", dia.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horarios.length()").value(2))
                .andExpect(jsonPath("$.horarios[0].profissionalId").value(bia.getId()));

        jdbc.update("DELETE FROM excecao_jornada WHERE profissional_id = ?", ana.getId());
        Long excecaoId = jdbc.queryForObject("INSERT INTO excecao_jornada "
                + "(profissional_id, data, tipo) VALUES (?, ?, 'JORNADA_ESPECIAL') RETURNING id",
                Long.class, ana.getId(), dia);
        jdbc.update("INSERT INTO excecao_jornada_intervalo (excecao_id, hora_inicio, hora_fim) "
                + "VALUES (?, '11:00', '12:00')", excecaoId);
        mvc.perform(get(rota()).param("data", dia.toString())
                        .param("profissionalId", ana.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horarios.length()").value(2))
                .andExpect(jsonPath("$.horarios[0].inicio").value("2026-10-05T11:00:00"));

        jdbc.update("INSERT INTO afastamento (profissional_id, data_inicio, data_fim, tipo) "
                + "VALUES (?, ?, ?, 'FERIAS')", ana.getId(), dia, dia);
        mvc.perform(get(rota()).param("data", dia.toString())
                        .param("profissionalId", ana.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horarios.length()").value(0));
    }

    @Test
    void rejeitaParametrosERecursosInelegiveis() throws Exception {
        mvc.perform(get(rota()).param("data", dia.minusDays(1).toString()))
                .andExpect(status().isOk());
        mvc.perform(get(rota()).param("data", "2026-09-30"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(rota()).param("data", "2026-12-02"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(rota())).andExpect(status().isBadRequest());
        mvc.perform(get(rota()).param("data", dia.toString()).param("profissionalId", "99999"))
                .andExpect(status().isNotFound());
        bia.setAtivo(false);
        profissionais.saveAndFlush(bia);
        mvc.perform(get(rota()).param("data", dia.toString())
                        .param("profissionalId", bia.getId().toString()))
                .andExpect(status().isNotFound());
        servico.setAtivo(false);
        servicos.saveAndFlush(servico);
        mvc.perform(get(rota()).param("data", dia.toString()))
                .andExpect(status().isNotFound());
    }

    private void jornada(Profissional profissional, String inicio, String fim) {
        jdbc.update("INSERT INTO jornada_intervalo (profissional_id, dia_semana, hora_inicio, hora_fim) "
                + "VALUES (?, 1, ?::time, ?::time)", profissional.getId(), inicio, fim);
    }

    private String rota() {
        return "/api/unidades/" + unidade.getId() + "/servicos/" + servico.getId() + "/horarios";
    }
}
