package br.ufpa.dsai.estilomarcado.painel.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.ufpa.dsai.estilomarcado.painel.api.dto.AgendaAtendimentoResponse;
import br.ufpa.dsai.estilomarcado.painel.service.PainelProfissionalService;

@RestController
@RequestMapping("/api/painel")
public class PainelProfissionalController {

    private final PainelProfissionalService painelProfissionalService;

    public PainelProfissionalController(PainelProfissionalService painelProfissionalService) {
        this.painelProfissionalService = painelProfissionalService;
    }

    @GetMapping("/agenda")
    public ResponseEntity<List<AgendaAtendimentoResponse>> agenda(
            @RequestParam(value = "data", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(value = "de", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(value = "ate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        List<AgendaAtendimentoResponse> resposta;
        if (data != null) {
            if (de != null || ate != null) {
                throw new IllegalArgumentException("informe data ou de/ate, nao ambos");
            }
            resposta = painelProfissionalService.agendaAutenticada(data);
        } else {
            if (de == null || ate == null) {
                throw new IllegalArgumentException("informe data ou de/ate");
            }
            resposta = painelProfissionalService.agendaAutenticada(de, ate);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }
}
