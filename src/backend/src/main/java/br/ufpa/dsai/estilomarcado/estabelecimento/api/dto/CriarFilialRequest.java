package br.ufpa.dsai.estilomarcado.estabelecimento.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CriarFilialRequest(
        @NotBlank @Size(min = 2, max = 120) String nome,
        @Size(max = 250) String endereco,
        @Size(max = 30) String telefone,
        @NotBlank String fusoHorario,
        @NotNull @Valid PrimeiroAdministrador primeiroAdministrador,
        Long estabelecimentoId,
        Long unidadeId) {

    public record PrimeiroAdministrador(
            @NotBlank @Size(min = 2, max = 120) String nome,
            @NotBlank @Email @Size(max = 254) String email) {}
}
