package br.ufpa.dsai.estilomarcado.disponibilidade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MigracaoMotorDisponibilidadeTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void preservaAtendimentosLegadosESeparaDuracaoDoCatalogo() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").target(MigrationVersion.fromVersion("10"))
                .load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        jdbc.execute("INSERT INTO estabelecimento(id, nome) VALUES (1, 'Salao')");
        jdbc.execute("INSERT INTO unidade(id, nome, nome_normalizado, estabelecimento_id, principal) "
                + "VALUES (1, 'Centro', 'centro', 1, true)");
        jdbc.execute("INSERT INTO profissional(id, nome, unidade_id) VALUES (1, 'Ana', 1)");
        jdbc.execute("INSERT INTO servico(id, unidade_id, nome, duracao_minutos, preco, intervalo_minutos) "
                + "VALUES (1, 1, 'Corte', 45, 50, 15)");
        jdbc.execute("INSERT INTO cliente(id, nome) VALUES (1, 'Cliente')");
        jdbc.execute("INSERT INTO atendimento(id, profissional_id, servico_id, cliente_id, inicio, status) "
                + "VALUES (1, 1, 1, 1, '2026-10-05 09:00:00', 'AGENDADO')");

        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();

        assertEquals(45, jdbc.queryForObject(
                "SELECT duracao_minutos FROM atendimento WHERE id = 1", Integer.class));
        assertEquals(15, jdbc.queryForObject(
                "SELECT intervalo_minutos FROM atendimento WHERE id = 1", Integer.class));
        jdbc.execute("UPDATE servico SET duracao_minutos = 30, intervalo_minutos = 0 WHERE id = 1");
        assertEquals(45, jdbc.queryForObject(
                "SELECT duracao_minutos FROM atendimento WHERE id = 1", Integer.class));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.execute("UPDATE atendimento SET duracao_minutos = 0 WHERE id = 1"));
    }
}
