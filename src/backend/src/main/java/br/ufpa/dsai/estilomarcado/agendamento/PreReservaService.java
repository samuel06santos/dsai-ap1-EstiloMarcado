package br.ufpa.dsai.estilomarcado.agendamento;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.ufpa.dsai.estilomarcado.agendamento.AgendamentoService.Criacao;
import br.ufpa.dsai.estilomarcado.agendamento.AgendamentoService.Resposta;
import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;
import br.ufpa.dsai.estilomarcado.autenticacao.model.EstadoConta;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioAtual;
import br.ufpa.dsai.estilomarcado.autenticacao.repository.UsuarioRepository;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.RecursoNaoEncontradoException;
import br.ufpa.dsai.estilomarcado.catalogo.model.Profissional;
import br.ufpa.dsai.estilomarcado.catalogo.model.Servico;
import br.ufpa.dsai.estilomarcado.catalogo.repository.ProfissionalRepository;
import br.ufpa.dsai.estilomarcado.catalogo.repository.ServicoRepository;
import br.ufpa.dsai.estilomarcado.disponibilidade.service.MotorDisponibilidadeService;

@Service
public class PreReservaService {
    public record Pedido(Long unidadeId, Long servicoId, LocalDateTime inicio) {}
    public record Criada(Long id, Long profissionalId, Instant expiraEm, String checkoutUrl) {}
    public record Detalhe(Long filialId, String filial, Long servicoId, String servico,
                          java.math.BigDecimal preco, Long profissionalId, String profissional, LocalDateTime inicio,
                          String fusoHorario, Instant expiraEm, String estado) {}

    private final JdbcTemplate jdbc;
    private final ProfissionalRepository profissionais;
    private final ServicoRepository servicos;
    private final MotorDisponibilidadeService motor;
    private final PreReservaOcupacao ocupacao;
    private final AgendamentoService agendamentos;
    private final UsuarioAtual atual;
    private final UsuarioRepository usuarios;
    private final String frontendUrl;
    private final SecretKeySpec chave;
    private final SecureRandom random = new SecureRandom();

    public PreReservaService(JdbcTemplate jdbc, ProfissionalRepository profissionais,
            ServicoRepository servicos, MotorDisponibilidadeService motor, PreReservaOcupacao ocupacao,
            AgendamentoService agendamentos, UsuarioAtual atual, UsuarioRepository usuarios,
            @Value("${app.frontend-url:http://localhost:4200}") String frontendUrl,
            @Value("${app.mcp.checkout-key:}") String chaveBase64) {
        this.jdbc = jdbc;
        this.profissionais = profissionais;
        this.servicos = servicos;
        this.motor = motor;
        this.ocupacao = ocupacao;
        this.agendamentos = agendamentos;
        this.atual = atual;
        this.usuarios = usuarios;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        if (!this.frontendUrl.startsWith("https://")
                && !this.frontendUrl.startsWith("http://localhost:")
                && !this.frontendUrl.startsWith("http://127.0.0.1:"))
            throw new IllegalArgumentException("FRONTEND_URL do checkout deve usar HTTPS");
        byte[] bytes = chaveBase64.isBlank() ? new byte[32] : Base64.getDecoder().decode(chaveBase64);
        if (chaveBase64.isBlank()) random.nextBytes(bytes);
        if (bytes.length != 32) throw new IllegalArgumentException("MCP_CHECKOUT_KEY deve ter 32 bytes base64");
        this.chave = new SecretKeySpec(bytes, "AES");
    }

    @Transactional
    public Criada criar(Pedido pedido, String chaveTexto) {
        if (pedido == null || pedido.unidadeId() == null || pedido.servicoId() == null || pedido.inicio() == null)
            throw new IllegalArgumentException("filial, servico e inicio obrigatorios");
        UUID idempotencia = parseUuid(chaveTexto);
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))", idempotencia.toString());
        String hashPedido = sha256(pedido.unidadeId() + "|" + pedido.servicoId() + "|" + pedido.inicio());
        List<Map<String, Object>> anteriores = jdbc.queryForList(
                "select id,pedido_hash,profissional_id,expira_em,estado,token_cifrado from pre_reserva where chave_idempotencia=?",
                idempotencia);
        if (!anteriores.isEmpty()) {
            Map<String, Object> anterior = anteriores.getFirst();
            if (!hashPedido.equals(anterior.get("pedido_hash")))
                throw new AgendamentoConflitoException("CHAVE_IDEMPOTENCIA_REUTILIZADA", "chave reutilizada com outro pedido");
            if (!"ATIVA".equals(anterior.get("estado")) || !((java.sql.Timestamp) anterior.get("expira_em")).toInstant().isAfter(agoraBanco()))
                throw new AgendamentoConflitoException("PRE_RESERVA_EXPIRADA", "pre-reserva indisponivel");
            String token = decifrar((byte[]) anterior.get("token_cifrado"));
            return new Criada(((Number) anterior.get("id")).longValue(),
                    ((Number) anterior.get("profissional_id")).longValue(),
                    ((java.sql.Timestamp) anterior.get("expira_em")).toInstant(), link(token));
        }
        Servico servico = servicos.findById(pedido.servicoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("servico nao encontrado"));
        if (!servico.isAtivo() || !servico.getUnidade().isAtiva()
                || !servico.getUnidade().getId().equals(pedido.unidadeId()))
            throw new RecursoNaoEncontradoException("servico nao encontrado");
        List<Profissional> elegiveis = servico.getProfissionais().stream()
                .filter(Profissional::isAtivo).filter(p -> p.getUnidade().getId().equals(pedido.unidadeId()))
                .sorted(Comparator.comparing(Profissional::getId)).toList();
        for (Profissional candidato : elegiveis) {
            profissionais.bloquear(candidato.getId()).orElseThrow();
            ocupacao.liberarExpiradas(candidato.getId());
            if (motor.consultar(pedido.unidadeId(), pedido.servicoId(), pedido.inicio().toLocalDate(),
                    candidato.getId()).horarios().stream().noneMatch(h -> h.inicio().equals(pedido.inicio()))) continue;
            byte[] bruto = new byte[32];
            random.nextBytes(bruto);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bruto);
            Instant criado = agoraBanco();
            Instant expira = criado.plusSeconds(600);
            Long id = jdbc.queryForObject("""
                    insert into pre_reserva(unidade_id,servico_id,profissional_id,inicio,
                      duracao_minutos,intervalo_minutos,fuso_horario,token_hash,token_cifrado,
                      chave_idempotencia,pedido_hash,criado_em,expira_em)
                    values (?,?,?,?,?,?,?,?,?,?,?,?,?) returning id
                    """, Long.class, pedido.unidadeId(), pedido.servicoId(), candidato.getId(), pedido.inicio(),
                    servico.getDuracaoMinutos(), servico.getIntervaloMinutos() == null ? 0 : servico.getIntervaloMinutos(),
                    servico.getUnidade().getFusoHorario(), sha256(token), cifrar(token), idempotencia,
                    hashPedido, java.sql.Timestamp.from(criado), java.sql.Timestamp.from(expira));
            return new Criada(id, candidato.getId(), expira, link(token));
        }
        throw new AgendamentoConflitoException("HORARIO_INDISPONIVEL", "horario indisponivel");
    }

    @Transactional(readOnly = true)
    public Detalhe detalhar(String token) {
        validarFormato(token);
        Instant agora = agoraBanco();
        List<Detalhe> itens = jdbc.query("""
                select p.unidade_id,u.nome,p.servico_id,s.nome,s.preco,p.profissional_id,pr.nome,
                       p.inicio,p.fuso_horario,p.expira_em,p.estado
                from pre_reserva p join unidade u on u.id=p.unidade_id
                join servico s on s.id=p.servico_id join profissional pr on pr.id=p.profissional_id
                where p.token_hash=?
                """, (rs, n) -> new Detalhe(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4),
                        rs.getBigDecimal(5), rs.getLong(6), rs.getString(7),
                        rs.getObject(8, LocalDateTime.class), rs.getString(9), rs.getTimestamp(10).toInstant(),
                        "ATIVA".equals(rs.getString(11)) && !rs.getTimestamp(10).toInstant().isAfter(agora)
                                ? "EXPIRADA" : rs.getString(11)), sha256(token));
        if (itens.isEmpty()) throw new RecursoNaoEncontradoException("pre-reserva nao encontrada");
        return itens.getFirst();
    }

    @Transactional
    public Resposta confirmar(String token) {
        validarFormato(token);
        var sessao = atual.get();
        if (sessao.perfil() != PerfilUsuario.CLIENTE) throw new AccessDeniedException("acesso negado");
        var conta = usuarios.bloquearPorId(sessao.id()).orElseThrow();
        if (conta.getEstado() != EstadoConta.ATIVA || conta.getPerfil() != PerfilUsuario.CLIENTE)
            throw new AccessDeniedException("conta de cliente inativa");
        String hash = sha256(token);
        List<Map<String, Object>> preliminares = jdbc.queryForList(
                "select id,profissional_id from pre_reserva where token_hash=?", hash);
        if (preliminares.isEmpty()) throw new RecursoNaoEncontradoException("pre-reserva nao encontrada");
        Long profissionalId = ((Number) preliminares.getFirst().get("profissional_id")).longValue();
        profissionais.bloquear(profissionalId).orElseThrow();
        ocupacao.liberarExpiradas(profissionalId);
        Map<String, Object> reserva = jdbc.queryForMap("select * from pre_reserva where token_hash=? for update", hash);
        if ("CONSUMIDA".equals(reserva.get("estado"))) {
            throw new AgendamentoConflitoException("PRE_RESERVA_CONSUMIDA", "pre-reserva indisponivel");
        }
        if (!"ATIVA".equals(reserva.get("estado"))
                || !((java.sql.Timestamp) reserva.get("expira_em")).toInstant().isAfter(agoraBanco()))
            throw new AgendamentoConflitoException("PRE_RESERVA_EXPIRADA", "pre-reserva expirada");
        Servico servicoAtual = servicos.findById(((Number) reserva.get("servico_id")).longValue())
                .orElseThrow(() -> new AgendamentoConflitoException("PRE_RESERVA_ALTERADA",
                        "servico da pre-reserva alterado"));
        int intervaloAtual = servicoAtual.getIntervaloMinutos() == null ? 0 : servicoAtual.getIntervaloMinutos();
        if (servicoAtual.getDuracaoMinutos() != ((Number) reserva.get("duracao_minutos")).intValue()
                || intervaloAtual != ((Number) reserva.get("intervalo_minutos")).intValue()
                || !servicoAtual.getUnidade().getFusoHorario().equals(reserva.get("fuso_horario")))
            throw new AgendamentoConflitoException("PRE_RESERVA_ALTERADA",
                    "regras da pre-reserva alteradas");
        Long id = ((Number) reserva.get("id")).longValue();
        jdbc.update("update pre_reserva set estado='CONSUMIDA',consumido_em=clock_timestamp(),consumido_por=?,token_cifrado=decode('','hex') where id=?",
                sessao.id(), id);
        Resposta resultado = agendamentos.criar(((Number) reserva.get("unidade_id")).longValue(),
                new Criacao(((Number) reserva.get("servico_id")).longValue(), profissionalId,
                        ((java.sql.Timestamp) reserva.get("inicio")).toLocalDateTime(), null, null, null),
                UUID.nameUUIDFromBytes(("pre-reserva:" + id).getBytes(StandardCharsets.UTF_8)).toString());
        jdbc.update("update pre_reserva set atendimento_id=? where id=?", resultado.id(), id);
        return resultado;
    }

    private UUID parseUuid(String texto) {
        try { return UUID.fromString(texto); }
        catch (Exception ex) { throw new IllegalArgumentException("Idempotency-Key deve ser UUID"); }
    }

    private Instant agoraBanco() {
        return jdbc.queryForObject("select clock_timestamp()", java.sql.Timestamp.class).toInstant();
    }

    private void validarFormato(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}"))
            throw new RecursoNaoEncontradoException("pre-reserva nao encontrada");
    }

    private String link(String token) { return frontendUrl + "/checkout#t=" + token; }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private byte[] cifrar(String token) {
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(128, nonce));
            byte[] cifrado = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            byte[] resultado = Arrays.copyOf(nonce, nonce.length + cifrado.length);
            System.arraycopy(cifrado, 0, resultado, nonce.length, cifrado.length);
            return resultado;
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private String decifrar(byte[] dados) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(128, Arrays.copyOf(dados, 12)));
            return new String(cipher.doFinal(dados, 12, dados.length - 12), StandardCharsets.UTF_8);
        } catch (Exception ex) { throw new IllegalStateException("token de checkout indisponivel", ex); }
    }
}
