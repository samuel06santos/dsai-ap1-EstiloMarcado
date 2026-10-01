package br.ufpa.dsai.estilomarcado.autenticacao.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import br.ufpa.dsai.estilomarcado.autenticacao.exception.LimiteTentativasException;

@Service
public class LimiteRequisicoesService {

    private final Map<String, Deque<Instant>> requisicoes = new ConcurrentHashMap<>();

    public void verificarLogin(String origem) {
        verificar("login:" + origem, 30, Duration.ofMinutes(15));
    }

    public void verificarEnvio(String operacao, String origem, String emailNormalizado) {
        verificar(operacao + ":origem:" + origem, 10, Duration.ofHours(1));
        verificar(operacao + ":email:" + emailNormalizado, 5, Duration.ofHours(1));
    }

    private void verificar(String chave, int maximo, Duration janela) {
        Instant limite = Instant.now().minus(janela);
        Deque<Instant> instantes = requisicoes.computeIfAbsent(chave, ignorada -> new ArrayDeque<>());
        synchronized (instantes) {
            while (!instantes.isEmpty() && instantes.peekFirst().isBefore(limite)) {
                instantes.removeFirst();
            }
            if (instantes.size() >= maximo) {
                throw new LimiteTentativasException();
            }
            instantes.addLast(Instant.now());
        }
    }
}
