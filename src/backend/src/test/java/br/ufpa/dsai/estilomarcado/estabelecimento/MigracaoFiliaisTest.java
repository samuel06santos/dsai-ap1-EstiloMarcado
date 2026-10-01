package br.ufpa.dsai.estilomarcado.estabelecimento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MigracaoFiliaisTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void converteUnidadesLegadasSemAlterarIdsOuVinculos() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").target(MigrationVersion.fromVersion("5"))
                .load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        jdbc.execute("INSERT INTO unidade(id, nome) VALUES (41, 'Centro'), (42, 'Centro')");
        jdbc.execute("INSERT INTO profissional(id, nome, unidade_id) VALUES (51, 'Ana', 41)");
        jdbc.execute("INSERT INTO servico(id, unidade_id, nome, duracao_minutos, preco) "
                + "VALUES (61, 41, 'Corte', 30, 50)");
        jdbc.execute("INSERT INTO servico_profissional(servico_id, profissional_id) VALUES (61, 51)");
        jdbc.execute("INSERT INTO usuario(id, nome, email, email_normalizado, perfil, estado, unidade_id) "
                + "VALUES (71, 'Admin', 'admin@example.com', 'admin@example.com', 'ADMINISTRADOR', 'PENDENTE', 41)");

        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();

        assertEquals(2L, jdbc.queryForObject("SELECT count(*) FROM estabelecimento", Long.class));
        assertEquals(41L, jdbc.queryForObject(
                "SELECT estabelecimento_id FROM unidade WHERE id = 41", Long.class));
        assertEquals(42L, jdbc.queryForObject(
                "SELECT estabelecimento_id FROM unidade WHERE id = 42", Long.class));
        assertTrue(jdbc.queryForObject("SELECT principal FROM unidade WHERE id = 41", Boolean.class));
        assertEquals("America/Sao_Paulo", jdbc.queryForObject(
                "SELECT fuso_horario FROM unidade WHERE id = 41", String.class));
        assertEquals(41L, jdbc.queryForObject(
                "SELECT unidade_id FROM profissional WHERE id = 51", Long.class));
        assertEquals(41L, jdbc.queryForObject(
                "SELECT unidade_id FROM servico WHERE id = 61", Long.class));
        assertEquals(41L, jdbc.queryForObject(
                "SELECT unidade_id FROM usuario WHERE id = 71", Long.class));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT count(*) FROM servico_profissional WHERE servico_id = 61 AND profissional_id = 51",
                Long.class));
    }
}
