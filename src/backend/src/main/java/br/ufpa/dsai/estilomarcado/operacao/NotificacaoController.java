package br.ufpa.dsai.estilomarcado.operacao;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificacaoController {
    private final NotificacaoService service;

    public NotificacaoController(NotificacaoService service) { this.service = service; }

    @GetMapping("/api/me/notificacoes")
    public ResponseEntity<List<NotificacaoService.Item>> minhas(
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "50") int tamanho) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.minhas(pagina, tamanho));
    }

    public record Contagem(long naoLidas) {}
    @GetMapping("/api/me/notificacoes/nao-lidas/contagem")
    public ResponseEntity<Contagem> naoLidas() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Contagem(service.naoLidas()));
    }

    @PatchMapping("/api/me/notificacoes/{id}/leitura")
    public ResponseEntity<Void> ler(@PathVariable Long id) {
        service.marcarLida(id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/api/me/notificacoes/preferencias")
    public ResponseEntity<NotificacaoService.Preferencias> preferencias() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.preferencias());
    }

    @PutMapping("/api/me/notificacoes/preferencias")
    public ResponseEntity<NotificacaoService.Preferencias> preferencias(
            @RequestBody NotificacaoService.Preferencias preferencias) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.alterarPreferencias(preferencias));
    }
}
