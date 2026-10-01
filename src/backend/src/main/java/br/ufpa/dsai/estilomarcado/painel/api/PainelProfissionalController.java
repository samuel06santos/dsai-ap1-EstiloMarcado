package br.ufpa.dsai.estilomarcado.painel.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
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
    public List<AgendaAtendimentoResponse> agenda(
            @RequestParam("data") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return painelProfissionalService.agendaAutenticada(data);
    }
}
