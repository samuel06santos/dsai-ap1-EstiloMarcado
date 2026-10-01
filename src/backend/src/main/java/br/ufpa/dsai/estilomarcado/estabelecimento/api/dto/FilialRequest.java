package br.ufpa.dsai.estilomarcado.estabelecimento.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FilialRequest(
        @NotBlank @Size(min = 2, max = 120) String nome,
        @Size(max = 250) String endereco,
        @Size(max = 30) String telefone,
        @NotBlank String fusoHorario,
        Boolean ativa,
        Long estabelecimentoId,
        Long unidadeId) {}
