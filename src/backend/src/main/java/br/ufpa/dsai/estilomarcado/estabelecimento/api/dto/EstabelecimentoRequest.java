package br.ufpa.dsai.estilomarcado.estabelecimento.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EstabelecimentoRequest(@NotBlank @Size(min = 2, max = 120) String nome) {}
