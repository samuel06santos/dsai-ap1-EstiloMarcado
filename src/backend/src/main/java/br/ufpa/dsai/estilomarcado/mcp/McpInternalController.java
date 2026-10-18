package br.ufpa.dsai.estilomarcado.mcp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import br.ufpa.dsai.estilomarcado.agendamento.AgendamentoConflitoException;
import br.ufpa.dsai.estilomarcado.agendamento.AgendamentoService;
import br.ufpa.dsai.estilomarcado.agendamento.PreReservaOcupacao;
import br.ufpa.dsai.estilomarcado.agendamento.PreReservaService;
import br.ufpa.dsai.estilomarcado.agendamento.AgendamentoService.Criacao;
import br.ufpa.dsai.estilomarcado.agendamento.model.AtendimentoStatus;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioPrincipal;
import br.ufpa.dsai.estilomarcado.autenticacao.repository.UsuarioRepository;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.RecursoNaoEncontradoException;
import br.ufpa.dsai.estilomarcado.catalogo.model.Profissional;
import br.ufpa.dsai.estilomarcado.catalogo.repository.ProfissionalRepository;
import br.ufpa.dsai.estilomarcado.catalogo.repository.ServicoRepository;
import br.ufpa.dsai.estilomarcado.catalogo.service.ServicoService;
import br.ufpa.dsai.estilomarcado.disponibilidade.service.MotorDisponibilidadeService;
import br.ufpa.dsai.estilomarcado.estabelecimento.service.FilialDescobertaService;
import br.ufpa.dsai.estilomarcado.operacao.ListaEsperaService;

@Controller
@ResponseBody
@RequestMapping("/api/mcp/internal")
public class McpInternalController {
    public record Pedido(Long filialId, Long servicoId, LocalDateTime inicio, String chaveIdempotencia) {}
    public record Reagendar(LocalDateTime inicio, String confirmacaoNonce) {}
    public record Cancelar(String motivo, String confirmacaoNonce) {}

    private final McpTokenService tokens;
    private final String serviceKey;
    private final FilialDescobertaService filiais;
    private final ServicoService servicos;
    private final MotorDisponibilidadeService motor;
    private final AgendamentoService agendamentos;
    private final ListaEsperaService listaEspera;
    private final PreReservaService preReservas;
    private final ServicoRepository servicoRepo;
    private final ProfissionalRepository profissionais;
    private final PreReservaOcupacao ocupacao;
    private final UsuarioRepository usuarios;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public McpInternalController(McpTokenService tokens,
            @Value("${app.mcp.service-key:}") String serviceKey,
            FilialDescobertaService filiais, ServicoService servicos, MotorDisponibilidadeService motor,
            AgendamentoService agendamentos, ListaEsperaService listaEspera,
            PreReservaService preReservas, ServicoRepository servicoRepo,
            ProfissionalRepository profissionais, PreReservaOcupacao ocupacao,
            UsuarioRepository usuarios, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.tokens = tokens; this.serviceKey = serviceKey; this.filiais = filiais; this.servicos = servicos;
        this.motor = motor; this.agendamentos = agendamentos; this.listaEspera = listaEspera;
        this.preReservas = preReservas; this.servicoRepo = servicoRepo;
        this.profissionais = profissionais; this.ocupacao = ocupacao;
        this.usuarios = usuarios;
        this.jdbc = jdbc;
    }

    @GetMapping("/filiais")
    public ResponseEntity<Object> listarFiliais(@RequestHeader("X-MCP-Service-Key") String chave,
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @RequestParam(defaultValue = "") String busca,
            @RequestParam(defaultValue = "1") String pagina,
            @RequestParam(defaultValue = "12") String tamanho) {
        verificarServico(chave); identidadeOpcional(bearer);
        return ok(filiais.listar(busca, null, pagina, tamanho));
    }

    @GetMapping("/filiais/{filialId}/servicos")
    public ResponseEntity<Object> listarServicos(@RequestHeader("X-MCP-Service-Key") String chave,
            @RequestHeader(value = "Authorization", required = false) String bearer, @PathVariable Long filialId) {
        verificarServico(chave); identidadeOpcional(bearer);
        return ok(servicos.listarDisponiveis(filialId));
    }

    @GetMapping("/filiais/{filialId}/servicos/{servicoId}/horarios")
    public ResponseEntity<Object> consultarHorarios(@RequestHeader("X-MCP-Service-Key") String chave,
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @PathVariable Long filialId, @PathVariable Long servicoId,
            @RequestParam LocalDate data) {
        verificarServico(chave); identidadeOpcional(bearer);
        return ok(motor.consultar(filialId, servicoId, data, null));
    }

    @GetMapping("/me/agendamentos")
    public ResponseEntity<Object> meus(@RequestHeader("X-MCP-Service-Key") String chave,
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @RequestParam LocalDate de, @RequestParam LocalDate ate,
            @RequestParam(required = false) AtendimentoStatus status,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamanho) {
        verificarServico(chave);
        return privado(bearer, "mcp:read", () -> agendamentos.listar(null, de, ate, status,
                null, null, null, pagina, tamanho));
    }

    @GetMapping("/agendamentos/{id}")
    public ResponseEntity<Object> detalhe(@RequestHeader("X-MCP-Service-Key") String chave,
            @RequestHeader(value = "Authorization", required = false) String bearer, @PathVariable Long id) {
        verificarServico(chave);
        return privado(bearer, "mcp:read", () -> agendamentos.detalhar(id));
    }

    @GetMapping("/agendamentos/{id}/horarios-reagendamento")
    public ResponseEntity<Object> horariosReagendamento(@RequestHeader("X-MCP-Service-Key") String chave,
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @PathVariable Long id, @RequestParam LocalDate data) {
        verificarServico(chave);
        return privado(bearer, "mcp:read", () -> agendamentos.horariosParaReagendar(id, data));
    }

    @GetMapping("/me/lista-espera")
    public ResponseEntity<Object> minhaLista(@RequestHeader("X-MCP-Service-Key") String chave,
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamanho) {
        verificarServico(chave);
        return privado(bearer, "mcp:read", () -> listaEspera.minhas(pagina, tamanho));
    }

    @PostMapping("/agendamentos")
    @Transactional
    public ResponseEntity<Object> criar(@RequestHeader("X-MCP-Service-Key") String chave,
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @RequestBody Pedido pedido) {
        verificarServico(chave);
        if (pedido == null || pedido.filialId() == null || pedido.servicoId() == null || pedido.inicio() == null)
            throw new IllegalArgumentException("filial, servico e inicio obrigatorios");
        if (bearer == null || bearer.isBlank()) {
            return ok(preReservas.criar(new PreReservaService.Pedido(
                    pedido.filialId(), pedido.servicoId(), pedido.inicio()), pedido.chaveIdempotencia()));
        }
        return privado(bearer, "mcp:write", () -> criarAutenticado(pedido));
    }

    private Object criarAutenticado(Pedido pedido) {
        var principal = (UsuarioPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        usuarios.bloquearPorId(principal.id()).orElseThrow();
        var servico = servicoRepo.findById(pedido.servicoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("servico nao encontrado"));
        if (!servico.getUnidade().getId().equals(pedido.filialId()) || !servico.isAtivo())
            throw new RecursoNaoEncontradoException("servico nao encontrado");
        for (Profissional candidato : servico.getProfissionais().stream().filter(Profissional::isAtivo)
                .sorted(Comparator.comparing(Profissional::getId)).toList()) {
            profissionais.bloquear(candidato.getId()).orElseThrow();
            ocupacao.liberarExpiradas(candidato.getId());
            if (motor.consultar(pedido.filialId(), pedido.servicoId(), pedido.inicio().toLocalDate(),
                    candidato.getId()).horarios().stream().anyMatch(h -> h.inicio().equals(pedido.inicio()))) {
                return agendamentos.criar(pedido.filialId(), new Criacao(pedido.servicoId(),
                        candidato.getId(), pedido.inicio(), null, null, null), pedido.chaveIdempotencia());
            }
        }
        throw new AgendamentoConflitoException("HORARIO_INDISPONIVEL", "horario indisponivel");
    }

    @PostMapping("/agendamentos/{id}/cancelamentos")
    @Transactional
    public ResponseEntity<Object> cancelar(@RequestHeader("X-MCP-Service-Key") String chave,
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @PathVariable Long id, @RequestBody(required = false) Cancelar pedido) {
        verificarServico(chave);
        return privado(bearer, "mcp:write", () -> {
            consumirConfirmacao(pedido == null ? null : pedido.confirmacaoNonce());
            return agendamentos.cancelar(id, pedido.motivo());
        });
    }

    @PostMapping("/agendamentos/{id}/reagendamentos")
    @Transactional
    public ResponseEntity<Object> reagendar(@RequestHeader("X-MCP-Service-Key") String chave,
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @PathVariable Long id, @RequestBody Reagendar pedido) {
        verificarServico(chave);
        return privado(bearer, "mcp:write", () -> {
            consumirConfirmacao(pedido == null ? null : pedido.confirmacaoNonce());
            return agendamentos.reagendar(id, pedido.inicio());
        });
    }

    private void consumirConfirmacao(String texto) {
        UUID nonce;
        try { nonce = UUID.fromString(texto); }
        catch (Exception ex) { throw new IllegalArgumentException("confirmacao MCP ausente"); }
        if (jdbc.update("insert into mcp_confirmacao_consumida(nonce) values (?) on conflict do nothing",
                nonce) == 0)
            throw new AgendamentoConflitoException("CONFIRMACAO_REUTILIZADA", "confirmação MCP já utilizada");
    }

    private void verificarServico(String chave) {
        if (serviceKey.isBlank() || chave == null || !MessageDigest.isEqual(
                serviceKey.getBytes(StandardCharsets.UTF_8), chave.getBytes(StandardCharsets.UTF_8)))
            throw new BadCredentialsException("servico MCP nao autorizado");
    }

    private McpTokenService.Identidade identidadeOpcional(String bearer) {
        return bearer == null || bearer.isBlank() ? null : tokens.validar(bearer);
    }

    private ResponseEntity<Object> privado(String bearer, String escopo, java.util.function.Supplier<Object> acao) {
        if (bearer == null || bearer.isBlank()) return ok(Map.of("codigo", "AUTENTICACAO_MCP_NECESSARIA",
                "mensagem", "Autorize o cliente MCP no Estilo Marcado e repita a chamada."));
        McpTokenService.Identidade identidade = tokens.validar(bearer);
        identidade.exigir(escopo);
        SecurityContext anterior = SecurityContextHolder.getContext();
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        UsuarioPrincipal p = identidade.principal();
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p, null, p.getAuthorities()));
        SecurityContextHolder.setContext(contexto);
        try { return ok(acao.get()); }
        finally { SecurityContextHolder.setContext(anterior); }
    }

    private ResponseEntity<Object> ok(Object corpo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(corpo);
    }
}
