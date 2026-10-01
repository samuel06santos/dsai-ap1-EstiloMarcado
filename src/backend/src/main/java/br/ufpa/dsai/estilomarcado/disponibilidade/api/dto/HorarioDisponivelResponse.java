package br.ufpa.dsai.estilomarcado.disponibilidade.api.dto;

import java.time.LocalDateTime;

public record HorarioDisponivelResponse(Long profissionalId, LocalDateTime inicio,
                                        LocalDateTime fim) {
}
