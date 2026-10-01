package br.ufpa.dsai.estilomarcado.autenticacao.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.ufpa.dsai.estilomarcado.autenticacao.model.EventoSeguranca;
import br.ufpa.dsai.estilomarcado.autenticacao.repository.EventoSegurancaRepository;

@Service
public class AuditoriaService {

    private final EventoSegurancaRepository repository;

    public AuditoriaService(EventoSegurancaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void registrar(Long usuarioId, String tipo, boolean sucesso, String origem, String detalhes) {
        repository.save(new EventoSeguranca(
                usuarioId,
                tipo,
                sucesso ? "SUCESSO" : "FALHA",
                limitar(origem, 100),
                limitar(detalhes, 500)));
    }

    private String limitar(String valor, int tamanho) {
        if (valor == null || valor.length() <= tamanho) {
            return valor;
        }
        return valor.substring(0, tamanho);
    }
}
