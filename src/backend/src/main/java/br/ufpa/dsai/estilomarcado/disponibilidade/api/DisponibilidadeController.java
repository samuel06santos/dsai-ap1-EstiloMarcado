package br.ufpa.dsai.estilomarcado.disponibilidade.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.AfastamentoRequest;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.AfastamentoResponse;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.BloqueioRequest;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.BloqueioResponse;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.ExcecaoRequest;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.ExcecaoResponse;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.FeriadoRequest;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.FeriadoResponse;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.JanelaResponse;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.JornadaRequest;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.JornadaResponse;
import br.ufpa.dsai.estilomarcado.disponibilidade.service.DisponibilidadeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Manutencao de jornada, folgas, feriados, afastamentos e bloqueios por filial e
 * profissional. A autorizacao e aplicada no servico.
 */
@RestController
@RequestMapping("/api/unidades/{unidadeId}")
public class DisponibilidadeController {

    private final DisponibilidadeService service;

    public DisponibilidadeController(DisponibilidadeService service) {
        this.service = service;
    }

    @GetMapping("/profissionais/{profissionalId}/jornada")
    public JornadaResponse consultarJornada(@PathVariable Long unidadeId,
                                            @PathVariable Long profissionalId) {
        return service.consultarJornada(unidadeId, profissionalId);
    }

    @PutMapping("/profissionais/{profissionalId}/jornada")
    public JornadaResponse atualizarJornada(@PathVariable Long unidadeId,
                                            @PathVariable Long profissionalId,
                                            @Valid @RequestBody JornadaRequest request,
                                            HttpServletRequest http) {
        return service.atualizarJornada(unidadeId, profissionalId, request, http.getRemoteAddr());
    }

    @GetMapping("/profissionais/{profissionalId}/janelas")
    public List<JanelaResponse> consultarJanelas(
            @PathVariable Long unidadeId,
            @PathVariable Long profissionalId,
            @RequestParam("data") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return service.consultarJanelas(unidadeId, profissionalId, data);
    }

    @GetMapping("/profissionais/{profissionalId}/excecoes")
    public List<ExcecaoResponse> listarExcecoes(
            @PathVariable Long unidadeId,
            @PathVariable Long profissionalId,
            @RequestParam("de") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam("ate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return service.listarExcecoes(unidadeId, profissionalId, de, ate);
    }

    @PostMapping("/profissionais/{profissionalId}/excecoes")
    @ResponseStatus(HttpStatus.CREATED)
    public ExcecaoResponse criarExcecao(@PathVariable Long unidadeId,
                                        @PathVariable Long profissionalId,
                                        @Valid @RequestBody ExcecaoRequest request,
                                        HttpServletRequest http) {
        return service.criarExcecao(unidadeId, profissionalId, request, http.getRemoteAddr());
    }

    @PatchMapping("/profissionais/{profissionalId}/excecoes/{id}")
    public ExcecaoResponse atualizarExcecao(@PathVariable Long unidadeId,
                                            @PathVariable Long profissionalId,
                                            @PathVariable Long id,
                                            @Valid @RequestBody ExcecaoRequest request,
                                            HttpServletRequest http) {
        return service.atualizarExcecao(unidadeId, profissionalId, id, request, http.getRemoteAddr());
    }

    @DeleteMapping("/profissionais/{profissionalId}/excecoes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerExcecao(@PathVariable Long unidadeId,
                               @PathVariable Long profissionalId,
                               @PathVariable Long id,
                               HttpServletRequest http) {
        service.removerExcecao(unidadeId, profissionalId, id, http.getRemoteAddr());
    }

    @GetMapping("/profissionais/{profissionalId}/afastamentos")
    public List<AfastamentoResponse> listarAfastamentos(
            @PathVariable Long unidadeId,
            @PathVariable Long profissionalId,
            @RequestParam("de") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam("ate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return service.listarAfastamentos(unidadeId, profissionalId, de, ate);
    }

    @PostMapping("/profissionais/{profissionalId}/afastamentos")
    @ResponseStatus(HttpStatus.CREATED)
    public AfastamentoResponse criarAfastamento(@PathVariable Long unidadeId,
                                                @PathVariable Long profissionalId,
                                                @Valid @RequestBody AfastamentoRequest request,
                                                HttpServletRequest http) {
        return service.criarAfastamento(unidadeId, profissionalId, request, http.getRemoteAddr());
    }

    @PatchMapping("/profissionais/{profissionalId}/afastamentos/{id}")
    public AfastamentoResponse atualizarAfastamento(@PathVariable Long unidadeId,
                                                    @PathVariable Long profissionalId,
                                                    @PathVariable Long id,
                                                    @Valid @RequestBody AfastamentoRequest request,
                                                    HttpServletRequest http) {
        return service.atualizarAfastamento(unidadeId, profissionalId, id, request, http.getRemoteAddr());
    }

    @DeleteMapping("/profissionais/{profissionalId}/afastamentos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerAfastamento(@PathVariable Long unidadeId,
                                   @PathVariable Long profissionalId,
                                   @PathVariable Long id,
                                   HttpServletRequest http) {
        service.removerAfastamento(unidadeId, profissionalId, id, http.getRemoteAddr());
    }

    @GetMapping("/feriados")
    public List<FeriadoResponse> listarFeriados(
            @PathVariable Long unidadeId,
            @RequestParam("de") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam("ate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return service.listarFeriados(unidadeId, de, ate);
    }

    @PostMapping("/feriados")
    @ResponseStatus(HttpStatus.CREATED)
    public FeriadoResponse criarFeriado(@PathVariable Long unidadeId,
                                        @Valid @RequestBody FeriadoRequest request,
                                        HttpServletRequest http) {
        return service.criarFeriado(unidadeId, request, http.getRemoteAddr());
    }

    @PatchMapping("/feriados/{id}")
    public FeriadoResponse atualizarFeriado(@PathVariable Long unidadeId, @PathVariable Long id,
                                            @Valid @RequestBody FeriadoRequest request,
                                            HttpServletRequest http) {
        return service.atualizarFeriado(unidadeId, id, request, http.getRemoteAddr());
    }

    @DeleteMapping("/feriados/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerFeriado(@PathVariable Long unidadeId, @PathVariable Long id,
                               HttpServletRequest http) {
        service.removerFeriado(unidadeId, id, http.getRemoteAddr());
    }

    @GetMapping("/bloqueios")
    public List<BloqueioResponse> listarBloqueios(
            @PathVariable Long unidadeId,
            @RequestParam("de") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam("ate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(value = "profissionalId", required = false) Long profissionalId) {
        return service.listarBloqueios(unidadeId, de, ate, profissionalId);
    }

    @PostMapping("/bloqueios")
    @ResponseStatus(HttpStatus.CREATED)
    public BloqueioResponse criarBloqueio(@PathVariable Long unidadeId,
                                          @Valid @RequestBody BloqueioRequest request,
                                          HttpServletRequest http) {
        return service.criarBloqueio(unidadeId, request, http.getRemoteAddr());
    }

    @PatchMapping("/bloqueios/{id}")
    public BloqueioResponse atualizarBloqueio(@PathVariable Long unidadeId, @PathVariable Long id,
                                              @Valid @RequestBody BloqueioRequest request,
                                              HttpServletRequest http) {
        return service.atualizarBloqueio(unidadeId, id, request, http.getRemoteAddr());
    }

    @DeleteMapping("/bloqueios/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerBloqueio(@PathVariable Long unidadeId, @PathVariable Long id,
                                HttpServletRequest http) {
        service.removerBloqueio(unidadeId, id, http.getRemoteAddr());
    }
}
