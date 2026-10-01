package br.ufpa.dsai.estilomarcado.disponibilidade.api;

import java.time.LocalDate;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.ConsultaHorariosResponse;
import br.ufpa.dsai.estilomarcado.disponibilidade.service.MotorDisponibilidadeService;

@RestController
@RequestMapping("/api/unidades/{unidadeId}/servicos/{servicoId}/horarios")
public class MotorDisponibilidadeController {

    private final MotorDisponibilidadeService motor;

    public MotorDisponibilidadeController(MotorDisponibilidadeService motor) {
        this.motor = motor;
    }

    @GetMapping
    public ResponseEntity<ConsultaHorariosResponse> consultar(@PathVariable Long unidadeId,
            @PathVariable Long servicoId, @RequestParam LocalDate data,
            @RequestParam(required = false) Long profissionalId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(motor.consultar(unidadeId, servicoId, data, profissionalId));
    }
}
