package br.ufpa.dsai.estilomarcado.autenticacao.api.dto;

import br.ufpa.dsai.estilomarcado.autenticacao.model.EstadoConta;
import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;
import br.ufpa.dsai.estilomarcado.autenticacao.model.Usuario;

public record UsuarioResponse(
        Long id,
        String nome,
        String email,
        PerfilUsuario perfil,
        EstadoConta estado,
        Long unidadeId,
        Long profissionalId) {

    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getPerfil(),
                usuario.getEstado(),
                usuario.getUnidade() == null ? null : usuario.getUnidade().getId(),
                usuario.getProfissional() == null ? null : usuario.getProfissional().getId());
    }
}
