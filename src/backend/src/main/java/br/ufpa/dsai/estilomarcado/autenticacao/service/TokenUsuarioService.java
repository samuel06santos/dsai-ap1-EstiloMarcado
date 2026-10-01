package br.ufpa.dsai.estilomarcado.autenticacao.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

import br.ufpa.dsai.estilomarcado.autenticacao.exception.TokenInvalidoException;
import br.ufpa.dsai.estilomarcado.autenticacao.model.FinalidadeToken;
import br.ufpa.dsai.estilomarcado.autenticacao.model.TokenUsuario;
import br.ufpa.dsai.estilomarcado.autenticacao.model.Usuario;
import br.ufpa.dsai.estilomarcado.autenticacao.repository.TokenUsuarioRepository;

@Service
public class TokenUsuarioService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TokenUsuarioRepository repository;

    public TokenUsuarioService(TokenUsuarioRepository repository) {
        this.repository = repository;
    }

    public String emitir(Usuario usuario, FinalidadeToken finalidade, Duration validade) {
        invalidarAtivos(usuario, finalidade);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new TokenUsuario(usuario, hash(token), finalidade, Instant.now().plus(validade)));
        return token;
    }

    public TokenUsuario validar(String token, FinalidadeToken finalidade) {
        TokenUsuario encontrado = repository.findByTokenHashAndFinalidade(hash(token), finalidade)
                .orElseThrow(TokenInvalidoException::new);
        if (!encontrado.estaValido(Instant.now())) {
            throw new TokenInvalidoException();
        }
        return encontrado;
    }

    public void consumir(TokenUsuario token) {
        token.consumir(Instant.now());
        repository.save(token);
    }

    public void invalidarAtivos(Usuario usuario, FinalidadeToken finalidade) {
        Instant agora = Instant.now();
        repository.findByUsuarioIdAndFinalidadeAndConsumidoEmIsNull(usuario.getId(), finalidade)
                .forEach(token -> token.consumir(agora));
    }

    private String hash(String valor) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponivel", ex);
        }
    }
}
