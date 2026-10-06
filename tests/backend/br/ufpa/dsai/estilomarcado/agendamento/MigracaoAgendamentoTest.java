package br.ufpa.dsai.estilomarcado.agendamento;

import static org.junit.jupiter.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MigracaoAgendamentoTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void preservaLegadoPreencheSnapshotsEImpedeSobreposicao() {
        var fonte = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(fonte).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("11")).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(fonte);
        jdbc.execute("insert into estabelecimento(id,nome) values(1,'Salao')");
        jdbc.execute("insert into unidade(id,nome,nome_normalizado,estabelecimento_id,principal,fuso_horario) "
                + "values(1,'Centro','centro',1,true,'America/Sao_Paulo')");
        jdbc.execute("insert into profissional(id,nome,unidade_id) values(1,'Ana',1)");
        jdbc.execute("insert into servico(id,unidade_id,nome,duracao_minutos,intervalo_minutos,preco) "
                + "values(1,1,'Corte',30,90,50)");
        jdbc.execute("insert into cliente(id,nome) values(1,'Cliente')");
        jdbc.execute("insert into atendimento(id,profissional_id,servico_id,cliente_id,inicio,status,duracao_minutos,intervalo_minutos) "
                + "values(1,1,1,1,'2026-10-05 23:30','AGENDADO',30,90)");

        Flyway.configure().dataSource(fonte).locations("classpath:db/migration").load().migrate();
        assertEquals("Corte", jdbc.queryForObject("select servico_nome from atendimento where id=1", String.class));
        assertEquals("America/Sao_Paulo", jdbc.queryForObject(
                "select fuso_horario_agendamento from atendimento where id=1", String.class));
        assertEquals(0, new java.math.BigDecimal("50.00").compareTo(jdbc.queryForObject(
                "select preco_acordado from atendimento where id=1", java.math.BigDecimal.class)));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.execute("""
                insert into atendimento(profissional_id,servico_id,cliente_id,inicio,status,
                    duracao_minutos,intervalo_minutos,servico_nome,preco_acordado,fuso_horario_agendamento)
                values(1,1,1,'2026-10-06 00:15','AGENDADO',30,0,'Corte',50,'America/Sao_Paulo')
                """));
        jdbc.execute("update atendimento set status='CANCELADO' where id=1");
        jdbc.execute("""
                insert into atendimento(profissional_id,servico_id,cliente_id,inicio,status,
                    duracao_minutos,intervalo_minutos,servico_nome,preco_acordado,fuso_horario_agendamento)
                values(1,1,1,'2026-10-06 00:15','AGENDADO',30,0,'Corte',50,'America/Sao_Paulo')
                """);
        assertEquals(2, jdbc.queryForObject("select count(*) from atendimento", Integer.class));
    }

    @Test
    void rejeitaMigracaoComLegadoConflitanteSemAlterarHistorico() {
        try (PostgreSQLContainer<?> isolado = new PostgreSQLContainer<>("postgres:17-alpine")) {
            isolado.start();
            var fonte = new DriverManagerDataSource(isolado.getJdbcUrl(), isolado.getUsername(), isolado.getPassword());
            Flyway.configure().dataSource(fonte).locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("11")).load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(fonte);
            jdbc.execute("insert into estabelecimento(id,nome) values(1,'Salao')");
            jdbc.execute("insert into unidade(id,nome,nome_normalizado,estabelecimento_id,principal) "
                    + "values(1,'Centro','centro',1,true)");
            jdbc.execute("insert into profissional(id,nome,unidade_id) values(1,'Ana',1)");
            jdbc.execute("insert into servico(id,unidade_id,nome,duracao_minutos,intervalo_minutos,preco) "
                    + "values(1,1,'Corte',30,15,50)");
            jdbc.execute("insert into cliente(id,nome) values(1,'Cliente')");
            jdbc.execute("""
                    insert into atendimento(id,profissional_id,servico_id,cliente_id,inicio,status,
                        duracao_minutos,intervalo_minutos) values
                    (1,1,1,1,'2026-10-05 09:00','AGENDADO',30,15),
                    (2,1,1,1,'2026-10-05 09:30','CONFIRMADO',30,15)
                    """);
            FlywayException falha = assertThrows(FlywayException.class, () ->
                    Flyway.configure().dataSource(fonte).locations("classpath:db/migration").load().migrate());
            assertTrue(falha.getMessage().contains("atendimentos legados ativos sobrepostos"));
            assertEquals(2, jdbc.queryForObject("select count(*) from atendimento", Integer.class));
        }
    }
}
