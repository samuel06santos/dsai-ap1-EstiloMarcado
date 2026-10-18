package br.ufpa.dsai.estilomarcado.agendamento;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import br.ufpa.dsai.estilomarcado.disponibilidade.service.CalculadoraHorarios.Ocupacao;

@Component
public class PreReservaOcupacao {
    private final JdbcTemplate jdbc;

    public PreReservaOcupacao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Deve ser chamado depois do bloqueio transacional do profissional. */
    public void liberarExpiradas(Long profissionalId) {
        jdbc.update("""
                update pre_reserva set estado='EXPIRADA', token_cifrado=decode('','hex')
                where profissional_id=? and estado='ATIVA' and expira_em<=clock_timestamp()
                """, profissionalId);
    }

    private record Registro(Long profissionalId, Ocupacao ocupacao) {}

    public Map<Long, List<Ocupacao>> ativas(List<Long> profissionais, LocalDateTime inicio, LocalDateTime fim) {
        if (profissionais.isEmpty()) return Map.of();
        String marcadores = String.join(",", profissionais.stream().map(_id -> "?").toList());
        Object[] parametros = new Object[profissionais.size() + 2];
        for (int i = 0; i < profissionais.size(); i++) parametros[i] = profissionais.get(i);
        parametros[profissionais.size()] = inicio;
        parametros[profissionais.size() + 1] = fim;
        return jdbc.query("""
                select p.profissional_id,p.inicio,p.duracao_minutos,p.intervalo_minutos
                from pre_reserva p where p.profissional_id in (%s)
                  and p.estado='ATIVA' and p.expira_em>clock_timestamp()
                  and tsrange(p.inicio,p.inicio+(p.duracao_minutos+p.intervalo_minutos)*interval '1 minute','[)')
                      && tsrange(?,?,'[)')
                """.formatted(marcadores), (rs, row) -> new Registro(rs.getLong(1), new Ocupacao(
                rs.getObject(2, LocalDateTime.class), rs.getInt(3), rs.getInt(4))), parametros)
                .stream().collect(Collectors.groupingBy(Registro::profissionalId,
                        Collectors.mapping(Registro::ocupacao, Collectors.toList())));
    }
}
