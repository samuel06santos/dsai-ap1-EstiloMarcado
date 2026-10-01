package br.ufpa.dsai.estilomarcado.operacao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.ufpa.dsai.estilomarcado.agendamento.model.Atendimento;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioAtual;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.RecursoNaoEncontradoException;

@Service
public class NotificacaoService {
    public record Item(Long id, String tipo, String referenciaTipo, Long referenciaId,
                       Instant criadoEm, Instant lidoEm) {}
    public record Preferencias(boolean lembretes, boolean avisosLista) {}
    private record Trabalho(Long id, Long usuarioId, String tipo, String referenciaTipo, Long referenciaId,
                            int tentativas) {}

    private final JdbcTemplate jdbc;
    private final UsuarioAtual atual;
    private final JavaMailSender mail;
    private final Clock clock;
    private final TransactionTemplate transacoes;
    private final String frontendUrl;

    public NotificacaoService(JdbcTemplate jdbc, UsuarioAtual atual, JavaMailSender mail, Clock clock,
            PlatformTransactionManager manager, @Value("${app.frontend-url}") String frontendUrl) {
        this.jdbc = jdbc;
        this.atual = atual;
        this.mail = mail;
        this.clock = clock;
        this.transacoes = new TransactionTemplate(manager);
        this.frontendUrl = frontendUrl.replaceAll("/$", "");
    }

    @Transactional
    public void registrarAgendamento(Long eventoId, Atendimento atendimento, String tipo) {
        if (atendimento.getCliente().getUsuario() == null) return;
        Long usuarioId = atendimento.getCliente().getUsuario().getId();
        if ("CANCELAMENTO".equals(tipo) || "REAGENDAMENTO".equals(tipo)) {
            jdbc.update("update notificacao_outbox set status='CANCELADO' where referencia_tipo='AGENDAMENTO' "
                    + "and referencia_id=? and tipo='LEMBRETE' and status='PENDENTE'", atendimento.getId());
        }
        registrar(usuarioId, tipo, "AGENDAMENTO", atendimento.getId(), "evento:" + eventoId + ":" + usuarioId);
        if ("CRIACAO".equals(tipo) || "REAGENDAMENTO".equals(tipo)) {
            agendarLembrete(atendimento, usuarioId);
        }
    }

    @Transactional
    public void registrarOferta(Long ofertaId, Long usuarioId) {
        Boolean aceita = jdbc.queryForObject("select coalesce((select avisos_lista from notificacao_preferencia "
                + "where usuario_id=?),true)", Boolean.class, usuarioId);
        if (Boolean.TRUE.equals(aceita)) registrar(usuarioId, "OFERTA", "OFERTA", ofertaId,
                "oferta:" + ofertaId + ":" + usuarioId);
    }

    private void registrar(Long usuarioId, String tipo, String referenciaTipo, Long referenciaId, String chave) {
        jdbc.update("insert into notificacao_interna(usuario_id,tipo,referencia_tipo,referencia_id,dedupe_key) "
                + "values (?,?,?,?,?) on conflict (dedupe_key) do nothing",
                usuarioId, tipo, referenciaTipo, referenciaId, chave + ":interna");
        jdbc.update("insert into notificacao_outbox(usuario_id,tipo,referencia_tipo,referencia_id,dedupe_key) "
                + "values (?,?,?,?,?) on conflict (dedupe_key) do nothing",
                usuarioId, tipo, referenciaTipo, referenciaId, chave + ":email");
    }

    private void agendarLembrete(Atendimento a, Long usuarioId) {
        Instant alvo = a.getInicio().atZone(ZoneId.of(a.getFusoHorarioAgendamento()))
                .toInstant().minusSeconds(86400);
        if (!alvo.isAfter(clock.instant())) return;
        Boolean aceita = jdbc.queryForObject("select coalesce((select lembretes from notificacao_preferencia "
                + "where usuario_id=?),true)", Boolean.class, usuarioId);
        if (!Boolean.TRUE.equals(aceita)) return;
        String chave = "lembrete:" + a.getId() + ":" + a.getInicio() + ":" + usuarioId;
        jdbc.update("insert into notificacao_outbox(usuario_id,tipo,referencia_tipo,referencia_id,dedupe_key,enviar_apos) "
                + "values (?,'LEMBRETE','AGENDAMENTO',?,?,?) on conflict (dedupe_key) do nothing",
                usuarioId, a.getId(), chave, java.sql.Timestamp.from(alvo));
    }

    @Transactional(readOnly = true)
    public List<Item> minhas(int pagina, int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > 100) throw new IllegalArgumentException("paginacao invalida");
        return jdbc.query("select * from notificacao_interna where usuario_id=? "
                + "order by criado_em desc,id desc limit ? offset ?", this::mapear,
                atual.get().id(), tamanho, pagina * tamanho);
    }

    @Transactional(readOnly = true)
    public long naoLidas() {
        return jdbc.queryForObject("select count(*) from notificacao_interna where usuario_id=? and lido_em is null",
                Long.class, atual.get().id());
    }

    @Transactional
    public void marcarLida(Long id) {
        if (jdbc.update("update notificacao_interna set lido_em=coalesce(lido_em,?) "
                + "where id=? and usuario_id=?", java.sql.Timestamp.from(clock.instant()), id, atual.get().id()) == 0) {
            throw new RecursoNaoEncontradoException("notificacao nao encontrada");
        }
    }

    @Transactional(readOnly = true)
    public Preferencias preferencias() {
        return jdbc.query("select lembretes,avisos_lista from notificacao_preferencia where usuario_id=?",
                (r, n) -> new Preferencias(r.getBoolean(1), r.getBoolean(2)), atual.get().id())
                .stream().findFirst().orElse(new Preferencias(true, true));
    }

    @Transactional
    public Preferencias alterarPreferencias(Preferencias p) {
        if (p == null) throw new IllegalArgumentException("preferencias obrigatorias");
        jdbc.update("insert into notificacao_preferencia(usuario_id,lembretes,avisos_lista) values (?,?,?) "
                + "on conflict(usuario_id) do update set lembretes=excluded.lembretes,avisos_lista=excluded.avisos_lista",
                atual.get().id(), p.lembretes(), p.avisosLista());
        if (!p.lembretes()) jdbc.update("update notificacao_outbox set status='CANCELADO' "
                + "where usuario_id=? and tipo='LEMBRETE' and status='PENDENTE'", atual.get().id());
        if (!p.avisosLista()) jdbc.update("update notificacao_outbox set status='CANCELADO' "
                + "where usuario_id=? and tipo='OFERTA' and status='PENDENTE'", atual.get().id());
        return p;
    }

    @Scheduled(fixedDelayString = "${app.notificacoes.intervalo-ms:30000}")
    public void processar() {
        for (int i = 0; i < 20; i++) {
            Trabalho trabalho = transacoes.execute(status -> reservarProximo());
            if (trabalho == null) return;
            try {
                if (!elegivel(trabalho)) {
                    transacoes.executeWithoutResult(status -> concluir(trabalho.id(), "CANCELADO", trabalho.tentativas()));
                    continue;
                }
                if ("LEMBRETE".equals(trabalho.tipo())) {
                    transacoes.executeWithoutResult(status -> registrarLembreteInterno(trabalho));
                }
                enviar(trabalho);
                transacoes.executeWithoutResult(status -> concluir(trabalho.id(), "ENVIADO", trabalho.tentativas()));
            } catch (Exception ex) {
                transacoes.executeWithoutResult(status -> concluir(trabalho.id(),
                        trabalho.tentativas() >= 5 ? "FALHA_FINAL" : "PENDENTE", trabalho.tentativas()));
            }
        }
    }

    private Trabalho reservarProximo() {
        return jdbc.query("""
                with proximo as (
                  select id from notificacao_outbox where
                    (status='PENDENTE' and enviar_apos<=now()) or
                    (status='PROCESSANDO' and bloqueado_ate<now())
                  order by enviar_apos,id limit 1 for update skip locked
                )
                update notificacao_outbox n set status='PROCESSANDO',tentativas=tentativas+1,
                    bloqueado_ate=now()+interval '2 minutes' from proximo p where n.id=p.id
                returning n.id,n.usuario_id,n.tipo,n.referencia_tipo,n.referencia_id,n.tentativas
                """, (r, n) -> new Trabalho(r.getLong(1), r.getLong(2), r.getString(3),
                        r.getString(4), r.getLong(5), r.getInt(6))).stream().findFirst().orElse(null);
    }

    private boolean elegivel(Trabalho t) {
        Boolean ativa = jdbc.queryForObject("select estado='ATIVA' from usuario where id=?", Boolean.class, t.usuarioId());
        if (!Boolean.TRUE.equals(ativa)) return false;
        if ("LEMBRETE".equals(t.tipo())) {
            if (!Boolean.TRUE.equals(jdbc.queryForObject("select coalesce((select lembretes "
                    + "from notificacao_preferencia where usuario_id=?),true)", Boolean.class, t.usuarioId()))) return false;
            Boolean valido = jdbc.queryForObject("select status in ('AGENDADO','CONFIRMADO') and "
                    + "(inicio at time zone fuso_horario_agendamento)>now() from atendimento where id=?",
                    Boolean.class, t.referenciaId());
            return Boolean.TRUE.equals(valido);
        }
        if ("OFERTA".equals(t.tipo())) {
            if (!Boolean.TRUE.equals(jdbc.queryForObject("select coalesce((select avisos_lista "
                    + "from notificacao_preferencia where usuario_id=?),true)", Boolean.class, t.usuarioId()))) return false;
            Boolean valido = jdbc.queryForObject("""
                    select o.status='ENVIADA' and o.expira_em>now() and l.status='ATIVA'
                    from lista_espera_oferta o join lista_espera l on l.id=o.lista_espera_id where o.id=?
                    """, Boolean.class, t.referenciaId());
            return Boolean.TRUE.equals(valido);
        }
        return true;
    }

    private void enviar(Trabalho t) {
        String email = jdbc.queryForObject("select email from usuario where id=?", String.class, t.usuarioId());
        String detalhe = "";
        if ("AGENDAMENTO".equals(t.referenciaTipo()) && !"CANCELAMENTO".equals(t.tipo())) {
            List<String> horarios = jdbc.query("select inicio,fuso_horario_agendamento from atendimento where id=?",
                    (r, n) -> r.getObject(1, LocalDateTime.class) + " (" + r.getString(2) + ")",
                    t.referenciaId());
            if (!horarios.isEmpty()) detalhe = "\nHorario: " + horarios.getFirst();
        }
        try {
            MimeMessage msg = mail.createMimeMessage();
            msg.setFrom(new InternetAddress("nao-responda@estilomarcado.local"));
            msg.setRecipients(Message.RecipientType.TO, email);
            msg.setSubject("Estilo Marcado: " + t.tipo().toLowerCase().replace('_', ' '), "UTF-8");
            msg.setText("Ha uma atualizacao na sua conta do Estilo Marcado." + detalhe
                    + "\nConsulte os detalhes em " + frontendUrl
                    + ("OFERTA".equals(t.tipo()) ? "/lista-espera" : "/meus-agendamentos") + ".", "UTF-8");
            msg.saveChanges();
            msg.setHeader("Message-ID", "<estilo-marcado-" + t.id() + "@estilomarcado.local>");
            mail.send(msg);
        } catch (MessagingException ex) {
            throw new IllegalStateException("mensagem indisponivel", ex);
        }
    }

    private void concluir(Long id, String status, int tentativas) {
        jdbc.update("update notificacao_outbox set status=?,bloqueado_ate=null,"
                + "enviado_em=case when ?='ENVIADO' then now() else enviado_em end,"
                + "enviar_apos=case when ?='PENDENTE' then now()+(? * ?)*interval '1 minute' else enviar_apos end "
                + "where id=? and status='PROCESSANDO'", status, status, status,
                Math.min(tentativas, 10), Math.min(tentativas, 10), id);
    }

    private void registrarLembreteInterno(Trabalho t) {
        jdbc.update("""
                insert into notificacao_interna(usuario_id,tipo,referencia_tipo,referencia_id,dedupe_key)
                select usuario_id,tipo,referencia_tipo,referencia_id,dedupe_key||':interna'
                from notificacao_outbox where id=? on conflict (dedupe_key) do nothing
                """, t.id());
    }

    private Item mapear(ResultSet r, int n) throws SQLException {
        return new Item(r.getLong("id"), r.getString("tipo"), r.getString("referencia_tipo"),
                r.getLong("referencia_id"), r.getTimestamp("criado_em").toInstant(),
                r.getTimestamp("lido_em") == null ? null : r.getTimestamp("lido_em").toInstant());
    }
}
