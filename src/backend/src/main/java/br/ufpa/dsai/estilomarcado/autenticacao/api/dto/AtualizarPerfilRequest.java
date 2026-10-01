package br.ufpa.dsai.estilomarcado.autenticacao.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AtualizarPerfilRequest(@NotBlank @Size(min = 2, max = 120) String nome) {
}
