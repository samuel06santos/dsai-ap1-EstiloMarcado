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
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.JanelaResponse;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.JornadaRequest;
import br.ufpa.dsai.estilomarcado.disponibilidade.api.dto.JornadaResponse;
import br.ufpa.dsai.estilomarcado.disponibilidade.service.DisponibilidadeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Autoatendimento de disponibilidade do profissional autenticado. A identidade
 * e obtida da sessao, nunca do corpo da requisicao.
 */
@RestController
@RequestMapping("/api/profissionais/me")
public class MinhaDisponibilidadeController {

    private final DisponibilidadeService service;

    public MinhaDisponibilidadeController(DisponibilidadeService service) {
        this.service = service;
    }

    @GetMapping("/jornada")
    public JornadaResponse consultarJornada() {
        return service.consultarMinhaJornada();
    }

    @PutMapping("/jornada")
    public JornadaResponse atualizarJornada(@Valid @RequestBody JornadaRequest request,
                                            HttpServletRequest http) {
        return service.atualizarMinhaJornada(request, http.getRemoteAddr());
    }

    @GetMapping("/janelas")
    public List<JanelaResponse> consultarJanelas(
            @RequestParam("data") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return service.consultarMinhasJanelas(data);
    }

    @GetMapping("/excecoes")
    public List<ExcecaoResponse> listarExcecoes(
            @RequestParam("de") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam("ate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return service.listarMinhasExcecoes(de, ate);
    }

    @PostMapping("/excecoes")
    @ResponseStatus(HttpStatus.CREATED)
    public ExcecaoResponse criarExcecao(@Valid @RequestBody ExcecaoRequest request,
                                        HttpServletRequest http) {
        return service.criarMinhaExcecao(request, http.getRemoteAddr());
    }

    @PatchMapping("/excecoes/{id}")
    public ExcecaoResponse atualizarExcecao(@PathVariable Long id,
                                            @Valid @RequestBody ExcecaoRequest request,
                                            HttpServletRequest http) {
        return service.atualizarMinhaExcecao(id, request, http.getRemoteAddr());
    }

    @DeleteMapping("/excecoes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerExcecao(@PathVariable Long id, HttpServletRequest http) {
        service.removerMinhaExcecao(id, http.getRemoteAddr());
    }

    @GetMapping("/afastamentos")
    public List<AfastamentoResponse> listarAfastamentos(
            @RequestParam("de") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam("ate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return service.listarMeusAfastamentos(de, ate);
    }

    @PostMapping("/afastamentos")
    @ResponseStatus(HttpStatus.CREATED)
    public AfastamentoResponse criarAfastamento(@Valid @RequestBody AfastamentoRequest request,
                                                HttpServletRequest http) {
        return service.criarMeuAfastamento(request, http.getRemoteAddr());
    }

    @PatchMapping("/afastamentos/{id}")
    public AfastamentoResponse atualizarAfastamento(@PathVariable Long id,
                                                    @Valid @RequestBody AfastamentoRequest request,
                                                    HttpServletRequest http) {
        return service.atualizarMeuAfastamento(id, request, http.getRemoteAddr());
    }

    @DeleteMapping("/afastamentos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerAfastamento(@PathVariable Long id, HttpServletRequest http) {
        service.removerMeuAfastamento(id, http.getRemoteAddr());
    }

    @PostMapping("/bloqueios")
    @ResponseStatus(HttpStatus.CREATED)
    public BloqueioResponse criarBloqueio(@Valid @RequestBody BloqueioRequest request,
                                          HttpServletRequest http) {
        return service.criarMeuBloqueio(request, http.getRemoteAddr());
    }

    @PatchMapping("/bloqueios/{id}")
    public BloqueioResponse atualizarBloqueio(@PathVariable Long id,
                                              @Valid @RequestBody BloqueioRequest request,
                                              HttpServletRequest http) {
        return service.atualizarMeuBloqueio(id, request, http.getRemoteAddr());
    }

    @DeleteMapping("/bloqueios/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerBloqueio(@PathVariable Long id, HttpServletRequest http) {
        service.removerMeuBloqueio(id, http.getRemoteAddr());
    }
}
