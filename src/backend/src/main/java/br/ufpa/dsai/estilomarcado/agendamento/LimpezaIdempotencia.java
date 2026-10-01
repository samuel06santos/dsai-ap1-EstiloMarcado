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
    }
}
