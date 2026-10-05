package br.ufpa.dsai.estilomarcado.autenticacao.service;

import java.util.Map;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

import br.ufpa.dsai.estilomarcado.autenticacao.security.FirebaseSessionValidationFilter;

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

    public void invalidarRevogadas(String emailNormalizado, long tokensValidosApos) {
        if (tokensValidosApos <= 0) return;
        repository.findByPrincipalName(emailNormalizado).forEach((id, sessao) -> {
            Long autenticadoEm = sessao.getAttribute(FirebaseSessionValidationFilter.AUTH_TIME);
            if (autenticadoEm == null || autenticadoEm < tokensValidosApos) {
                repository.deleteById(id);
            }
        });
    }
}
