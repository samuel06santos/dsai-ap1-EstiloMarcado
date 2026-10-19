package br.ufpa.dsai.estilomarcado.agendamento;

import java.time.Clock;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LimpezaIdempotencia {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public LimpezaIdempotencia(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void limpar() {
        jdbc.update("delete from agendamento_idempotencia where expira_em < ?",
                java.sql.Timestamp.from(clock.instant()));
        jdbc.update("delete from mcp_confirmacao_consumida where consumido_em < clock_timestamp()-interval '1 day'");
        jdbc.update("delete from mcp_codigo_oauth where expira_em < clock_timestamp()-interval '1 day'");
        jdbc.update("delete from mcp_token_revogado where expira_em < clock_timestamp()");
        jdbc.update("delete from mcp_refresh_token where expira_em < clock_timestamp() "
                + "or consumido_em < clock_timestamp()-interval '1 day'");
    }
}
