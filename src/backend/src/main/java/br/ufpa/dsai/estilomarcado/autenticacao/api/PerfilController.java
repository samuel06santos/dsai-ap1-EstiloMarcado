package br.ufpa.dsai.estilomarcado.autenticacao.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.ufpa.dsai.estilomarcado.autenticacao.api.dto.AtualizarPerfilRequest;
import br.ufpa.dsai.estilomarcado.autenticacao.api.dto.UsuarioResponse;
import br.ufpa.dsai.estilomarcado.autenticacao.service.PerfilService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/usuarios/me")
public class PerfilController {

    private final PerfilService service;

    public PerfilController(PerfilService service) {
        this.service = service;
    }

    @GetMapping
    public UsuarioResponse consultar() {
        return service.consultar();
    }

    @PatchMapping
    public UsuarioResponse atualizar(@Valid @RequestBody AtualizarPerfilRequest request) {
        return service.atualizarNome(request.nome());
    }
}
