package br.ufpa.dsai.estilomarcado.estabelecimento.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.ufpa.dsai.estilomarcado.estabelecimento.api.dto.CriarFilialRequest;
import br.ufpa.dsai.estilomarcado.estabelecimento.api.dto.EstabelecimentoRequest;
import br.ufpa.dsai.estilomarcado.estabelecimento.api.dto.EstabelecimentoResponse;
import br.ufpa.dsai.estilomarcado.estabelecimento.api.dto.FilialResponse;
import br.ufpa.dsai.estilomarcado.estabelecimento.service.EstabelecimentoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/estabelecimentos/{id}")
public class EstabelecimentoController {
    private final EstabelecimentoService service;
    public EstabelecimentoController(EstabelecimentoService service) { this.service = service; }

    @GetMapping
    public EstabelecimentoResponse consultar(@PathVariable Long id) {
        return service.consultarEstabelecimento(id);
    }

    @PatchMapping
    public EstabelecimentoResponse atualizar(@PathVariable Long id,
                                              @Valid @RequestBody EstabelecimentoRequest request,
                                              HttpServletRequest http) {
        return service.atualizarEstabelecimento(id, request, http.getRemoteAddr());
    }

    @PostMapping("/unidades")
    @ResponseStatus(HttpStatus.CREATED)
    public FilialResponse criarFilial(@PathVariable Long id,
                                      @Valid @RequestBody CriarFilialRequest request,
                                      HttpServletRequest http) {
        return service.criarFilial(id, request, http.getRemoteAddr());
    }
}
