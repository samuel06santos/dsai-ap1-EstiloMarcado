package br.ufpa.dsai.estilomarcado.autenticacao.api.dto;

import jakarta.validation.constraints.NotBlank;

public record FirebaseTokenRequest(@NotBlank String idToken) {}
