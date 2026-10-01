package br.ufpa.dsai.estilomarcado.autenticacao.service;

import java.util.Map;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

@Service
public class SessaoService {

    private final FindByIndexNameSessionRepository<? extends Session> repository;

    public SessaoService(FindByIndexNameSessionRepository<? extends Session> repository) {
        this.repository = repository;
    }

    public void invalidarTodas(String emailNormalizado) {
        Map<String, ? extends Session> sessoes = repository.findByPrincipalName(emailNormalizado);
        sessoes.keySet().forEach(repository::deleteById);
    }
}
