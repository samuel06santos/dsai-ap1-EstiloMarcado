package br.ufpa.dsai.estilomarcado.operacao;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HistoricoRelatorioController {
    private final HistoricoRelatorioService service;

    public HistoricoRelatorioController(HistoricoRelatorioService service) { this.service = service; }

    @GetMapping("/api/agendamentos/{id}/eventos")
    public ResponseEntity<List<HistoricoRelatorioService.Evento>> eventos(@PathVariable Long id,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "50") int tamanho) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.eventos(id, pagina, tamanho));
    }

    @GetMapping("/api/unidades/{unidadeId}/relatorios/operacionais")
    public ResponseEntity<HistoricoRelatorioService.Relatorio> relatorio(@PathVariable Long unidadeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) Long servicoId,
            @RequestParam(required = false) Long profissionalId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.relatorio(unidadeId, de, ate, servicoId, profissionalId));
    }
}
