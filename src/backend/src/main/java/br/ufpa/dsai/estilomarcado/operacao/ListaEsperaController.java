package br.ufpa.dsai.estilomarcado.operacao;

import java.net.URI;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.ufpa.dsai.estilomarcado.agendamento.AgendamentoService.Resposta;
import br.ufpa.dsai.estilomarcado.agendamento.AgendamentoConflitoException;
import org.springframework.dao.DataIntegrityViolationException;

@RestController
public class ListaEsperaController {
    private final ListaEsperaService service;

    public ListaEsperaController(ListaEsperaService service) { this.service = service; }

    @PostMapping("/api/unidades/{unidadeId}/lista-espera")
    public ResponseEntity<ListaEsperaService.Solicitacao> entrar(@PathVariable Long unidadeId,
            @RequestBody ListaEsperaService.Preferencias preferencias) {
        var entrada = service.entrar(unidadeId, preferencias);
        var resposta = entrada.criada()
                ? ResponseEntity.created(URI.create("/api/me/lista-espera/" + entrada.solicitacao().id()))
                : ResponseEntity.ok();
        return resposta.cacheControl(CacheControl.noStore()).body(entrada.solicitacao());
    }

    @GetMapping("/api/me/lista-espera")
    public ResponseEntity<List<ListaEsperaService.Solicitacao>> minhas(
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "50") int tamanho) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.minhas(pagina, tamanho));
    }

    @GetMapping("/api/me/lista-espera/{id}/ofertas")
    public ResponseEntity<List<ListaEsperaService.Oferta>> ofertas(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.minhasOfertas(id));
    }

    @DeleteMapping("/api/me/lista-espera/{id}")
    public ResponseEntity<Void> cancelar(@PathVariable Long id) {
        service.cancelar(id, null);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/api/me/lista-espera/{id}/ofertas/{ofertaId}/aceitacoes")
    public ResponseEntity<Resposta> aceitar(@PathVariable Long id, @PathVariable Long ofertaId,
            @RequestHeader(value = "Idempotency-Key", required = false) String chave) {
        Resposta resposta;
        try {
            resposta = service.aceitar(id, ofertaId, chave);
        } catch (AgendamentoConflitoException ex) {
            if ("HORARIO_INDISPONIVEL".equals(ex.getCodigo())) service.marcarOfertaIndisponivel(id, ofertaId);
            throw ex;
        } catch (DataIntegrityViolationException ex) {
            service.marcarOfertaIndisponivel(id, ofertaId);
            throw ex;
        }
        return ResponseEntity.created(URI.create("/api/agendamentos/" + resposta.id()))
                .cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping("/api/unidades/{unidadeId}/lista-espera")
    public ResponseEntity<List<ListaEsperaService.Solicitacao>> fila(@PathVariable Long unidadeId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "50") int tamanho) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.daUnidade(unidadeId, status, pagina, tamanho));
    }

    @GetMapping("/api/unidades/{unidadeId}/lista-espera/{id}/ofertas")
    public ResponseEntity<List<ListaEsperaService.Oferta>> ofertasEquipe(@PathVariable Long unidadeId,
            @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.ofertasDaUnidade(unidadeId, id));
    }

    @DeleteMapping("/api/unidades/{unidadeId}/lista-espera/{id}")
    public ResponseEntity<Void> cancelarEquipe(@PathVariable Long unidadeId, @PathVariable Long id) {
        service.cancelar(id, unidadeId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/api/unidades/{unidadeId}/encaixes")
    public ResponseEntity<Resposta> encaixar(@PathVariable Long unidadeId,
            @RequestBody ListaEsperaService.Encaixe pedido,
            @RequestHeader(value = "Idempotency-Key", required = false) String chave) {
        var resposta = service.encaixar(unidadeId, pedido, chave);
        return ResponseEntity.created(URI.create("/api/agendamentos/" + resposta.id()))
                .cacheControl(CacheControl.noStore()).body(resposta);
    }
}
