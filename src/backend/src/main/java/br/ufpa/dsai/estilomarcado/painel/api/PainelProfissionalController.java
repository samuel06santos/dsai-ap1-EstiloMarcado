package br.ufpa.dsai.estilomarcado.painel.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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

    /**
     * Agenda do dia do profissional autenticado.
     *
     * <p>A identificacao do profissional vem do cabecalho {@code X-Profissional-Id},
     * que representa o usuario autenticado nesta versao inicial. A spec de
     * autenticacao substituira esse cabecalho pelo contexto de seguranca.</p>
     */
    @GetMapping("/agenda")
    public List<AgendaAtendimentoResponse> agenda(
            @RequestHeader(name = "X-Profissional-Id", required = false) Long profissionalId,
            @RequestParam("data") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return painelProfissionalService.agendaDoDia(profissionalId, data);
    }
}
