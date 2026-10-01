package br.ufpa.dsai.estilomarcado.autenticacao.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NovaSenhaRequest(
        @NotBlank @Size(max = 200) String token,
        @NotBlank
        @Pattern(regexp = "(?=.{8,72}$)(?=.*\\p{L})(?=.*\\d)[^\\r\\n]+",
                 message = "deve ter entre 8 e 72 caracteres, com ao menos uma letra e um numero")
        String senha,
        @NotBlank String confirmacaoSenha) {
}
