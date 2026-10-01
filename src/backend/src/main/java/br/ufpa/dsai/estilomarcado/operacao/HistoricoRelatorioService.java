package br.ufpa.dsai.estilomarcado.operacao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioAtual;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioPrincipal;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.RecursoNaoEncontradoException;

@Service
public class HistoricoRelatorioService {
    public record Evento(Long id, String tipo, Instant ocorridoEm, Long autorId, String estadoAnterior,
                         String estadoNovo, LocalDateTime inicioAnterior, LocalDateTime inicioNovo) {}
    public record Indicadores(long agendados, long confirmados, long cancelados, long criacoes,
                             long confirmacoes, long cancelamentos, long reagendamentos, long encaixes,
                             long solicitacoesAtivas) {
        Indicadores somar(Indicadores outro) {
            return new Indicadores(agendados + outro.agendados, confirmados + outro.confirmados,
                    cancelados + outro.cancelados, criacoes + outro.criacoes,
                    confirmacoes + outro.confirmacoes, cancelamentos + outro.cancelamentos,
                    reagendamentos + outro.reagendamentos, encaixes + outro.encaixes,
                    solicitacoesAtivas + outro.solicitacoesAtivas);
        }
    }
    public record Dia(LocalDate data, Indicadores indicadores) {}
    public record Relatorio(Instant geradoEm, Long unidadeId, String fusoHorario, LocalDate de,
                           LocalDate ate, Long servicoId, Long profissionalId,
                           Indicadores total, List<Dia> dias) {}

    private final JdbcTemplate jdbc;
    private final UsuarioAtual atual;
    private final Clock clock;

    public HistoricoRelatorioService(JdbcTemplate jdbc, UsuarioAtual atual, Clock clock) {
        this.jdbc = jdbc;
        this.atual = atual;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Evento> eventos(Long atendimentoId, int pagina, int tamanho) {
        paginacao(pagina, tamanho);
        UsuarioPrincipal sessao = atual.get();
        Boolean visivel = jdbc.query("""
                select a.cliente_id,c.usuario_id,p.id as profissional_id,p.unidade_id
                from atendimento a join cliente c on c.id=a.cliente_id
                join profissional p on p.id=a.profissional_id where a.id=?
                """, (r, n) -> {
                    if (sessao.perfil() == PerfilUsuario.CLIENTE) {
                        return sessao.id().equals(r.getObject("usuario_id"));
                    }
                    if (sessao.perfil() == PerfilUsuario.PROFISSIONAL) {
                        return sessao.profissionalId().equals(r.getObject("profissional_id"));
                    }
                    return (sessao.perfil() == PerfilUsuario.RECEPCAO
                            || sessao.perfil() == PerfilUsuario.ADMINISTRADOR)
                            && sessao.unidadeId().equals(r.getObject("unidade_id"));
                }, atendimentoId).stream().findFirst().orElse(false);
        if (!visivel) throw new RecursoNaoEncontradoException("agendamento nao encontrado");
        return jdbc.query("""
                select id,tipo,ocorrido_em,autor_id,estado_anterior,estado_novo,inicio_anterior,inicio_novo
                from agendamento_evento where atendimento_id=? order by ocorrido_em,id limit ? offset ?
                """, this::mapearEvento, atendimentoId, tamanho, pagina * tamanho);
    }

    @Transactional(readOnly = true)
    public Relatorio relatorio(Long unidadeId, LocalDate de, LocalDate ate, Long servicoId, Long profissionalId) {
        UsuarioPrincipal sessao = atual.get();
        if (sessao.perfil() != PerfilUsuario.ADMINISTRADOR) throw new AccessDeniedException("acesso negado");
        if (!unidadeId.equals(sessao.unidadeId())) throw new RecursoNaoEncontradoException("filial nao encontrada");
        if (de == null || ate == null || de.isAfter(ate) || ate.isAfter(de.plusDays(89))) {
            throw new IllegalArgumentException("periodo de ate 90 dias obrigatorio");
        }
        String fuso = jdbc.query("select fuso_horario from unidade where id=?", (r, n) -> r.getString(1),
                unidadeId).stream().findFirst().orElseThrow(() -> new RecursoNaoEncontradoException("filial nao encontrada"));
        if (servicoId != null && !Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from servico where id=? and unidade_id=?)", Boolean.class, servicoId, unidadeId))) {
            throw new RecursoNaoEncontradoException("servico nao encontrado");
        }
        if (profissionalId != null && !Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from profissional where id=? and unidade_id=?)", Boolean.class, profissionalId, unidadeId))) {
            throw new RecursoNaoEncontradoException("profissional nao encontrado");
        }
        List<Dia> dias = new ArrayList<>();
        Indicadores total = vazio();
        for (LocalDate dia = de; !dia.isAfter(ate); dia = dia.plusDays(1)) {
            long[] estados = jdbc.queryForObject("""
                    select count(*) filter(where a.status='AGENDADO'),
                      count(*) filter(where a.status='CONFIRMADO'),
                      count(*) filter(where a.status='CANCELADO')
                    from atendimento a join profissional p on p.id=a.profissional_id
                    where p.unidade_id=? and a.inicio::date=? and (cast(? as bigint) is null or a.servico_id=?)
                      and (cast(? as bigint) is null or a.profissional_id=?)
                    """, (r, n) -> new long[]{r.getLong(1), r.getLong(2), r.getLong(3)},
                    unidadeId, dia, servicoId, servicoId, profissionalId, profissionalId);
            long[] eventos = jdbc.queryForObject("""
                    select count(*) filter(where e.tipo='CRIACAO'),
                      count(*) filter(where e.tipo='CONFIRMACAO'),
                      count(*) filter(where e.tipo='CANCELAMENTO'),
                      count(*) filter(where e.tipo='REAGENDAMENTO'),
                      count(*) filter(where e.tipo='CRIACAO' and a.lista_espera_id is not null)
                    from agendamento_evento e join atendimento a on a.id=e.atendimento_id
                    join profissional p on p.id=a.profissional_id
                    where p.unidade_id=? and (e.ocorrido_em at time zone a.fuso_horario_agendamento)::date=?
                      and (cast(? as bigint) is null or a.servico_id=?)
                      and (cast(? as bigint) is null or a.profissional_id=?)
                    """, (r, n) -> new long[]{r.getLong(1), r.getLong(2), r.getLong(3),
                            r.getLong(4), r.getLong(5)}, unidadeId, dia,
                    servicoId, servicoId, profissionalId, profissionalId);
            Indicadores indicadores = new Indicadores(estados[0], estados[1], estados[2],
                    eventos[0], eventos[1], eventos[2], eventos[3], eventos[4], 0);
            dias.add(new Dia(dia, indicadores));
            total = total.somar(indicadores);
        }
        Long ativas = jdbc.queryForObject("""
                select count(*) from lista_espera where unidade_id=? and status='ATIVA'
                  and (cast(? as bigint) is null or servico_id=?)
                  and (cast(? as bigint) is null or profissional_id=? or profissional_id is null)
                """, Long.class, unidadeId, servicoId, servicoId, profissionalId, profissionalId);
        total = total.somar(new Indicadores(0, 0, 0, 0, 0, 0, 0, 0, ativas));
        return new Relatorio(clock.instant(), unidadeId, fuso, de, ate, servicoId, profissionalId, total, dias);
    }

    private Evento mapearEvento(ResultSet r, int n) throws SQLException {
        return new Evento(r.getLong("id"), r.getString("tipo"), r.getTimestamp("ocorrido_em").toInstant(),
                r.getLong("autor_id"), r.getString("estado_anterior"), r.getString("estado_novo"),
                r.getObject("inicio_anterior", LocalDateTime.class),
                r.getObject("inicio_novo", LocalDateTime.class));
    }

    private Indicadores vazio() { return new Indicadores(0, 0, 0, 0, 0, 0, 0, 0, 0); }

    private void paginacao(int pagina, int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > 100) throw new IllegalArgumentException("paginacao invalida");
    }
}
