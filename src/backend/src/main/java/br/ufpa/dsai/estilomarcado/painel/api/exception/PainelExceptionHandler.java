package br.ufpa.dsai.estilomarcado.painel.api.exception;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz as excecoes do painel profissional em respostas HTTP.
 */
@RestControllerAdvice(basePackages = "br.ufpa.dsai.estilomarcado.painel")
public class PainelExceptionHandler {

    @ExceptionHandler(PerfilNaoIdentificadoException.class)
    public ResponseEntity<ErroPainel> handlePerfilNaoIdentificado(PerfilNaoIdentificadoException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErroPainel(HttpStatus.UNAUTHORIZED.value(), ex.getMessage(), Instant.now()));
    }

    public record ErroPainel(int status, String mensagem, Instant timestamp) {
    }
}
