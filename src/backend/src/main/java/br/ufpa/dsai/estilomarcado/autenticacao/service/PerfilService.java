package br.ufpa.dsai.estilomarcado.autenticacao.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.ufpa.dsai.estilomarcado.autenticacao.api.dto.AtualizarPerfilRequest;
import br.ufpa.dsai.estilomarcado.autenticacao.api.dto.MeuPerfilResponse;
import br.ufpa.dsai.estilomarcado.autenticacao.model.Usuario;
import br.ufpa.dsai.estilomarcado.autenticacao.repository.UsuarioRepository;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioAtual;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.RecursoNaoEncontradoException;

@Service
public class PerfilService {

    private final UsuarioRepository repository;
    private final UsuarioAtual usuarioAtual;
    private final GoogleAvatarService avatar;

    public PerfilService(UsuarioRepository repository, UsuarioAtual usuarioAtual, GoogleAvatarService avatar) {
        this.repository = repository;
        this.usuarioAtual = usuarioAtual;
        this.avatar = avatar;
    }

    @Transactional(readOnly = true)
    public MeuPerfilResponse consultar() {
        Long id = usuarioAtual.get().id();
        return repository.findById(id).map(usuario ->
                MeuPerfilResponse.from(usuario, avatar.fotoDoUsuario(usuario)))
                .orElseThrow(() -> new RecursoNaoEncontradoException("usuario nao encontrado"));
    }

    @Transactional
    public MeuPerfilResponse atualizar(AtualizarPerfilRequest request) {
        Long id = usuarioAtual.get().id();
        var usuario = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("usuario nao encontrado"));
        usuario.setNome(request.nome());
        if (request.telefoneInformado()) {
            usuario.setTelefoneContato(normalizarTelefone(request.telefoneContato()));
        }
        Usuario salvo = repository.save(usuario);
        return MeuPerfilResponse.from(salvo, avatar.fotoDoUsuario(salvo));
    }

    private String normalizarTelefone(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String texto = valor.trim();
        if (!texto.matches("\\+?[0-9() .-]+")) {
            throw new IllegalArgumentException("telefoneContato invalido");
        }
        String digitos = texto.replaceAll("\\D", "");
        if (!texto.startsWith("+")) {
            if ((digitos.length() != 10 && digitos.length() != 11)
                    || digitos.charAt(0) == '0') {
                throw new IllegalArgumentException("telefoneContato deve incluir DDD");
            }
            digitos = "55" + digitos;
        }
        if (!digitos.matches("[1-9][0-9]{7,14}")) {
            throw new IllegalArgumentException("telefoneContato invalido");
        }
        return "+" + digitos;
    }
}
