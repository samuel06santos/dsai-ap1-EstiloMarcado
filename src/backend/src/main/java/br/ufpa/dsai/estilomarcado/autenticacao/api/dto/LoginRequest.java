package br.ufpa.dsai.estilomarcado.autenticacao.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 72) String senha) {
    public LoginRequest {
        if (email != null) {
            email = email.trim();
        }
    }
}
