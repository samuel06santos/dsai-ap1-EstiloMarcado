package br.ufpa.dsai.estilomarcado.autenticacao.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.ufpa.dsai.estilomarcado.autenticacao.api.dto.UsuarioResponse;
import br.ufpa.dsai.estilomarcado.autenticacao.repository.UsuarioRepository;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioAtual;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.RecursoNaoEncontradoException;

@Service
public class PerfilService {

    private final UsuarioRepository repository;
    private final UsuarioAtual usuarioAtual;

    public PerfilService(UsuarioRepository repository, UsuarioAtual usuarioAtual) {
        this.repository = repository;
        this.usuarioAtual = usuarioAtual;
    }

    @Transactional(readOnly = true)
    public UsuarioResponse consultar() {
        Long id = usuarioAtual.get().id();
        return repository.findById(id).map(UsuarioResponse::from)
                .orElseThrow(() -> new RecursoNaoEncontradoException("usuario nao encontrado"));
    }

    @Transactional
    public UsuarioResponse atualizarNome(String nome) {
        Long id = usuarioAtual.get().id();
        var usuario = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("usuario nao encontrado"));
        usuario.setNome(nome.trim());
        return UsuarioResponse.from(repository.save(usuario));
    }
}
