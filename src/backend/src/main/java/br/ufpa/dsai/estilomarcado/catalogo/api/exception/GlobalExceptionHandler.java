package br.ufpa.dsai.estilomarcado.catalogo.api.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import br.ufpa.dsai.estilomarcado.autenticacao.exception.CredenciaisInvalidasException;
import br.ufpa.dsai.estilomarcado.autenticacao.exception.LimiteTentativasException;
import br.ufpa.dsai.estilomarcado.autenticacao.exception.TokenInvalidoException;

/**
 * Traduz excecoes do catalogo de servicos em respostas HTTP consistentes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErroResponse> handleAutenticacao(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErroResponse(HttpStatus.UNAUTHORIZED.value(), "autenticacao necessaria"));
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ResponseEntity<ErroResponse> handleCredenciais(CredenciaisInvalidasException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErroResponse(HttpStatus.UNAUTHORIZED.value(), ex.getMessage()));
    }

    @ExceptionHandler(TokenInvalidoException.class)
    public ResponseEntity<ErroResponse> handleToken(TokenInvalidoException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(new ErroResponse(HttpStatus.UNPROCESSABLE_ENTITY.value(), ex.getMessage()));
    }

    @ExceptionHandler(LimiteTentativasException.class)
    public ResponseEntity<ErroResponse> handleLimite(LimiteTentativasException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ErroResponse(HttpStatus.TOO_MANY_REQUESTS.value(), ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErroResponse> handleAcessoNegado(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErroResponse(HttpStatus.FORBIDDEN.value(), "acesso negado"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResponse> handleJsonInvalido(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(new ErroResponse(HttpStatus.BAD_REQUEST.value(), "requisicao invalida"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErroResponse> handleArgumento(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(new ErroResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResponse> handleNaoEncontrado(RecursoNaoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErroResponse(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    @ExceptionHandler(ConflitoException.class)
    public ResponseEntity<ErroResponse> handleConflito(ConflitoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErroResponse(HttpStatus.CONFLICT.value(), ex.getMessage()));
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    public ResponseEntity<ErroResponse> handleRegraDeNegocio(RegraDeNegocioException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErroResponse(HttpStatus.UNPROCESSABLE_ENTITY.value(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> handleValidacao(MethodArgumentNotValidException ex) {
        Map<String, String> campos = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(erro -> campos.putIfAbsent(erro.getField(), erro.getDefaultMessage()));

        ErroResponse resposta = new ErroResponse(HttpStatus.BAD_REQUEST.value(),
                "requisicao invalida", campos);
        return ResponseEntity.badRequest().body(resposta);
    }

    public record ErroResponse(int status, String mensagem, Map<String, String> campos, Instant timestamp) {
        public ErroResponse(int status, String mensagem) {
            this(status, mensagem, Map.of(), Instant.now());
        }

        public ErroResponse(int status, String mensagem, Map<String, String> campos) {
            this(status, mensagem, campos, Instant.now());
        }
    }
}
