package br.ufpa.dsai.estilomarcado.autenticacao.security;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;
import br.ufpa.dsai.estilomarcado.autenticacao.model.Usuario;

public record UsuarioPrincipal(
        Long id,
        String nome,
        String email,
        PerfilUsuario perfil,
        Long unidadeId,
        Long profissionalId) implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static UsuarioPrincipal from(Usuario usuario) {
        return new UsuarioPrincipal(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmailNormalizado(),
                usuario.getPerfil(),
                usuario.getUnidade() == null ? null : usuario.getUnidade().getId(),
                usuario.getProfissional() == null ? null : usuario.getProfissional().getId());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + perfil.name()));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return email;
    }
}
