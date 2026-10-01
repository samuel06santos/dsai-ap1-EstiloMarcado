package br.ufpa.dsai.estilomarcado.disponibilidade.api.dto;

import java.time.LocalDate;
import java.util.List;

public record ConsultaHorariosResponse(Long unidadeId, Long servicoId, LocalDate data,
                                       String fusoHorario, List<HorarioDisponivelResponse> horarios) {
}
