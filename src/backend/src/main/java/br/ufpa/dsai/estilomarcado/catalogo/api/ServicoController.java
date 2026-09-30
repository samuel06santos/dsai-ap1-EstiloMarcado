package br.ufpa.dsai.estilomarcado.catalogo.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.ufpa.dsai.estilomarcado.catalogo.api.dto.ServicoRequest;
import br.ufpa.dsai.estilomarcado.catalogo.api.dto.ServicoResponse;
import br.ufpa.dsai.estilomarcado.catalogo.service.ServicoService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class ServicoController {

    private final ServicoService servicoService;

    public ServicoController(ServicoService servicoService) {
        this.servicoService = servicoService;
    }

    @GetMapping("/unidades/{unidadeId}/servicos")
    public List<ServicoResponse> listar(
            @PathVariable Long unidadeId,
            @RequestParam(name = "somenteDisponiveis", defaultValue = "false") boolean somenteDisponiveis) {
        return somenteDisponiveis
                ? servicoService.listarDisponiveis(unidadeId)
                : servicoService.listar(unidadeId);
    }

    @PostMapping("/unidades/{unidadeId}/servicos")
    @ResponseStatus(HttpStatus.CREATED)
    public ServicoResponse criar(@PathVariable Long unidadeId,
                                 @Valid @RequestBody ServicoRequest request) {
        return servicoService.criar(unidadeId, request);
    }

    @GetMapping("/servicos/{id}")
    public ServicoResponse buscar(@PathVariable Long id) {
        return servicoService.buscar(id);
    }

    @PutMapping("/servicos/{id}")
    public ServicoResponse atualizar(@PathVariable Long id,
                                     @Valid @RequestBody ServicoRequest request) {
        return servicoService.atualizar(id, request);
    }

    @PatchMapping("/servicos/{id}/ativar")
    public ServicoResponse ativar(@PathVariable Long id) {
        return servicoService.ativar(id);
    }

    @PatchMapping("/servicos/{id}/desativar")
    public ServicoResponse desativar(@PathVariable Long id) {
        return servicoService.desativar(id);
    }
}
