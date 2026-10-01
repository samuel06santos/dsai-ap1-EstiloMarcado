package br.ufpa.dsai.estilomarcado.autenticacao.api.dto;

import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioPrincipal;

public record SessaoResponse(
        Long id,
        String nome,
        String email,
        PerfilUsuario perfil,
        Long unidadeId,
        Long profissionalId) {

    public static SessaoResponse from(UsuarioPrincipal principal) {
        return new SessaoResponse(principal.id(), principal.nome(), principal.email(), principal.perfil(),
                principal.unidadeId(), principal.profissionalId());
    }
}
