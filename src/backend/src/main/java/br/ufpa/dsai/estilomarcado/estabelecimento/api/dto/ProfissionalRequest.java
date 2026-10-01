package br.ufpa.dsai.estilomarcado.estabelecimento.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfissionalRequest(
        @NotBlank @Size(min = 2, max = 120) String nome,
        @Size(max = 500) String apresentacao,
        Boolean ativo,
        Long unidadeId,
        Long profissionalId) {}
