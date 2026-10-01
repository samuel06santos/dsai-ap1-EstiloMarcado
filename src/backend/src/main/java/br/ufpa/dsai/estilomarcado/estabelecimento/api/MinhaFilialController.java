package br.ufpa.dsai.estilomarcado.estabelecimento.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.ufpa.dsai.estilomarcado.estabelecimento.api.dto.FilialResponse;
import br.ufpa.dsai.estilomarcado.estabelecimento.service.EstabelecimentoService;

@RestController
@RequestMapping("/api/unidades/me")
public class MinhaFilialController {
    private final EstabelecimentoService service;

    public MinhaFilialController(EstabelecimentoService service) {
        this.service = service;
    }

    @GetMapping
    public FilialResponse consultar() {
        return service.consultarMinhaFilial();
    }
}
