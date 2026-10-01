package br.ufpa.dsai.estilomarcado.estabelecimento.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.ufpa.dsai.estilomarcado.estabelecimento.api.dto.FilialRequest;
import br.ufpa.dsai.estilomarcado.estabelecimento.api.dto.FilialResponse;
import br.ufpa.dsai.estilomarcado.estabelecimento.api.dto.ProfissionalRequest;
import br.ufpa.dsai.estilomarcado.estabelecimento.api.dto.ProfissionalResponse;
import br.ufpa.dsai.estilomarcado.estabelecimento.service.EstabelecimentoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/unidades/{unidadeId}")
public class FilialController {
    private final EstabelecimentoService service;
    public FilialController(EstabelecimentoService service) { this.service = service; }

    @GetMapping("/publico")
    public FilialResponse publico(@PathVariable Long unidadeId) {
        return service.filialPublica(unidadeId);
    }

    @GetMapping
    public FilialResponse consultar(@PathVariable Long unidadeId) {
        return service.consultarFilial(unidadeId);
    }

    @PatchMapping
    public FilialResponse atualizar(@PathVariable Long unidadeId,
                                    @Valid @RequestBody FilialRequest request,
                                    HttpServletRequest http) {
        return service.atualizarFilial(unidadeId, request, http.getRemoteAddr());
    }

    @GetMapping("/profissionais")
    public List<ProfissionalResponse> listarProfissionais(@PathVariable Long unidadeId,
            @RequestParam(defaultValue = "false") boolean incluirInativos) {
        return service.listarProfissionais(unidadeId, incluirInativos);
    }

    @GetMapping("/profissionais/{id}")
    public ProfissionalResponse profissional(@PathVariable Long unidadeId, @PathVariable Long id) {
        return service.profissionalPublico(unidadeId, id);
    }

    @PostMapping("/profissionais")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfissionalResponse criarProfissional(@PathVariable Long unidadeId,
            @Valid @RequestBody ProfissionalRequest request, HttpServletRequest http) {
        return service.criarProfissional(unidadeId, request, http.getRemoteAddr());
    }

    @PatchMapping("/profissionais/{id}")
    public ProfissionalResponse atualizarProfissional(@PathVariable Long unidadeId,
            @PathVariable Long id, @Valid @RequestBody ProfissionalRequest request,
            HttpServletRequest http) {
        return service.atualizarProfissional(unidadeId, id, request, http.getRemoteAddr());
    }
}
