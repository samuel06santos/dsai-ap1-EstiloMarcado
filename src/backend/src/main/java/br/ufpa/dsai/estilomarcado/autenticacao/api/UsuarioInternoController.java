package br.ufpa.dsai.estilomarcado.autenticacao.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.ufpa.dsai.estilomarcado.autenticacao.api.dto.UsuarioInternoPatchRequest;
import br.ufpa.dsai.estilomarcado.autenticacao.api.dto.UsuarioInternoRequest;
import br.ufpa.dsai.estilomarcado.autenticacao.api.dto.UsuarioResponse;
import br.ufpa.dsai.estilomarcado.autenticacao.service.UsuarioInternoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/unidades/{unidadeId}/usuarios-internos")
public class UsuarioInternoController {

    private final UsuarioInternoService service;

    public UsuarioInternoController(UsuarioInternoService service) {
        this.service = service;
    }

    @GetMapping
    public List<UsuarioResponse> listar(@PathVariable Long unidadeId) {
        return service.listar(unidadeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse criar(@PathVariable Long unidadeId,
                                  @Valid @RequestBody UsuarioInternoRequest request,
                                  HttpServletRequest servletRequest) {
        return service.criar(unidadeId, request, servletRequest.getRemoteAddr());
    }

    @PatchMapping("/{id}")
    public UsuarioResponse atualizar(@PathVariable Long unidadeId,
                                      @PathVariable Long id,
                                      @Valid @RequestBody UsuarioInternoPatchRequest request,
                                      HttpServletRequest servletRequest) {
        return service.atualizar(unidadeId, id, request, servletRequest.getRemoteAddr());
    }

    @PostMapping("/{id}/reenviar-convite")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void reenviarConvite(@PathVariable Long unidadeId,
                                @PathVariable Long id,
                                HttpServletRequest servletRequest) {
        service.reenviarConvite(unidadeId, id, servletRequest.getRemoteAddr());
    }
}
