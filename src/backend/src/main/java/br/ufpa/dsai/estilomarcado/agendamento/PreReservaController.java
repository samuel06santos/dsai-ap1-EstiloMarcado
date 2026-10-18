package br.ufpa.dsai.estilomarcado.agendamento;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.ufpa.dsai.estilomarcado.agendamento.AgendamentoService.Resposta;

@RestController
@RequestMapping("/api/checkout/pre-reservas")
public class PreReservaController {
    private final PreReservaService service;

    public PreReservaController(PreReservaService service) { this.service = service; }

    public record TokenPedido(String token) {}

    @PostMapping("/consulta")
    public ResponseEntity<PreReservaService.Detalhe> consultar(@RequestBody TokenPedido pedido) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer").body(service.detalhar(pedido.token()));
    }

    @PostMapping("/confirmacoes")
    public ResponseEntity<Resposta> confirmar(@RequestBody TokenPedido pedido) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer").body(service.confirmar(pedido.token()));
    }
}
