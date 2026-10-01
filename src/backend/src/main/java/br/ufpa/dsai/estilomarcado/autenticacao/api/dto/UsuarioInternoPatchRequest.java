package br.ufpa.dsai.estilomarcado.autenticacao.api.dto;

import br.ufpa.dsai.estilomarcado.autenticacao.model.EstadoConta;
import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;
import jakarta.validation.constraints.Size;

public record UsuarioInternoPatchRequest(
        @Size(min = 2, max = 120) String nome,
        PerfilUsuario perfil,
        EstadoConta estado,
        Long profissionalId) {
}
