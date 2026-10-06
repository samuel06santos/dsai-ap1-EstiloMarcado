package br.ufpa.dsai.estilomarcado.estabelecimento.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.ufpa.dsai.estilomarcado.estabelecimento.api.dto.FiliaisPublicasResponse;
import br.ufpa.dsai.estilomarcado.estabelecimento.api.dto.FiliaisPublicasResponse.ServicoOpcao;
import br.ufpa.dsai.estilomarcado.estabelecimento.service.FilialDescobertaService;

@RestController
@RequestMapping("/api/filiais/publicas")
public class FilialDescobertaController {
    private final FilialDescobertaService service;

    public FilialDescobertaController(FilialDescobertaService service) {
        this.service = service;
    }

    @GetMapping
    public FiliaisPublicasResponse listar(@RequestParam(defaultValue = "") String busca,
                                          @RequestParam(required = false) List<String> servico,
                                          @RequestParam(defaultValue = "1") String pagina,
                                          @RequestParam(defaultValue = "12") String tamanho) {
        return service.listar(busca, servico, pagina, tamanho);
    }

    @GetMapping("/servicos")
    public List<ServicoOpcao> servicos() { return service.servicos(); }
}
