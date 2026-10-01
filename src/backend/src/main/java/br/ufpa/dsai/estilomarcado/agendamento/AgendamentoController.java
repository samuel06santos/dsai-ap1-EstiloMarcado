package br.ufpa.dsai.estilomarcado.agendamento;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.ufpa.dsai.estilomarcado.agendamento.AgendamentoService.Criacao;
import br.ufpa.dsai.estilomarcado.agendamento.AgendamentoService.Resposta;
import br.ufpa.dsai.estilomarcado.agendamento.model.AtendimentoStatus;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.ConsultaHorariosResponse;

@RestController
public class AgendamentoController {
    private final AgendamentoService service;

    public AgendamentoController(AgendamentoService service) { this.service = service; }

    @PostMapping("/api/unidades/{unidadeId}/agendamentos")
    public ResponseEntity<Resposta> criar(@PathVariable Long unidadeId, @RequestBody Criacao pedido,
            @RequestHeader(value = "Idempotency-Key", required = false) String chave) {
        Resposta resposta = service.criar(unidadeId, pedido, chave);
        return ResponseEntity.created(URI.create("/api/agendamentos/" + resposta.id()))
                .cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping("/api/me/agendamentos")
    public ResponseEntity<List<Resposta>> meus(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) AtendimentoStatus status,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "50") int tamanho) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.listar(null, de, ate, status, null, pagina, tamanho));
    }

    @GetMapping("/api/unidades/{unidadeId}/agendamentos")
    public ResponseEntity<List<Resposta>> unidade(@PathVariable Long unidadeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) AtendimentoStatus status,
            @RequestParam(required = false) Long profissionalId,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "50") int tamanho) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.listar(unidadeId, de, ate, status, profissionalId, pagina, tamanho));
    }

    @GetMapping("/api/agendamentos/{id}")
    public ResponseEntity<Resposta> detalhar(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.detalhar(id));
    }

    @GetMapping("/api/agendamentos/{id}/horarios-reagendamento")
    public ResponseEntity<ConsultaHorariosResponse> horariosReagendamento(@PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.horariosParaReagendar(id, data));
    }

    @PostMapping("/api/agendamentos/{id}/confirmacoes")
    public ResponseEntity<Resposta> confirmar(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.confirmar(id));
    }

    public record Cancelamento(String motivo) {}
    @PostMapping("/api/agendamentos/{id}/cancelamentos")
    public ResponseEntity<Resposta> cancelar(@PathVariable Long id,
            @RequestBody(required = false) Cancelamento pedido) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.cancelar(id, pedido == null ? null : pedido.motivo()));
    }

    public record Reagendamento(LocalDateTime inicio) {}
    @PatchMapping("/api/agendamentos/{id}/reagendamento")
    public ResponseEntity<Resposta> reagendar(@PathVariable Long id, @RequestBody Reagendamento pedido) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.reagendar(id, pedido == null ? null : pedido.inicio()));
    }
}
