package br.ufpa.dsai.estilomarcado.autenticacao.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CadastroRequest(
        @NotBlank @Size(min = 2, max = 120) String nome,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank
        @Pattern(regexp = "(?=.{8,72}$)(?=.*\\p{L})(?=.*\\d)[^\\r\\n]+",
                 message = "deve ter entre 8 e 72 caracteres, com ao menos uma letra e um numero")
        String senha,
        @NotBlank String confirmacaoSenha) {

    public CadastroRequest {
        if (email != null) {
            email = email.trim();
        }
    }
}
