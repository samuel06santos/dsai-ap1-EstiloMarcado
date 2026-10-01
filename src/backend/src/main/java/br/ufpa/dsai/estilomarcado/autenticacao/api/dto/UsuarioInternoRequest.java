package br.ufpa.dsai.estilomarcado.autenticacao.api.dto;

import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UsuarioInternoRequest(
        @NotBlank @Size(min = 2, max = 120) String nome,
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull PerfilUsuario perfil,
        Long profissionalId) {
    public UsuarioInternoRequest {
        if (email != null) {
            email = email.trim();
        }
    }
}
