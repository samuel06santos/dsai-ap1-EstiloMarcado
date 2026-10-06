package br.ufpa.dsai.estilomarcado.autenticacao.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

import org.springframework.stereotype.Service;

import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.auth.UserRecord;

import br.ufpa.dsai.estilomarcado.autenticacao.model.Usuario;
import br.ufpa.dsai.estilomarcado.autenticacao.repository.UsuarioRepository;

/** Foto de apresentação obtida somente do provedor Google vinculado ao usuário local. */
@Service
public class GoogleAvatarService {

    private final FirebaseIdentityService firebase;
    private final UsuarioRepository usuarios;

    public GoogleAvatarService(FirebaseIdentityService firebase, UsuarioRepository usuarios) {
        this.firebase = firebase;
        this.usuarios = usuarios;
    }

    public String fotoDoUsuario(Long usuarioId) {
        return usuarios.findById(usuarioId).map(this::fotoDoUsuario).orElse(null);
    }

    public String fotoDoUsuario(Usuario usuario) {
        if (!firebase.enabled() || usuario.getFirebaseUid() == null) return null;
        try {
            UserRecord remoto = firebase.getUser(usuario.getFirebaseUid());
            if (remoto.isDisabled() || remoto.getProviderData() == null) return null;
            return Arrays.stream(remoto.getProviderData())
                    .filter(provedor -> "google.com".equals(provedor.getProviderId()))
                    .map(UserInfo::getPhotoUrl)
                    .map(GoogleAvatarService::urlConfiavel)
                    .filter(url -> url != null)
                    .findFirst().orElse(null);
        } catch (FirebaseAuthException ex) {
            // Uma indisponibilidade da foto não impede o uso da sessão ou do perfil.
            return null;
        }
    }

    static String urlConfiavel(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            URI url = new URI(valor);
            if (!"https".equalsIgnoreCase(url.getScheme())
                    || !"lh3.googleusercontent.com".equalsIgnoreCase(url.getHost())
                    || url.getPort() != -1 || url.getUserInfo() != null
                    || url.getRawFragment() != null || url.getRawPath() == null
                    || url.getRawPath().isBlank() || "/".equals(url.getRawPath())) {
                return null;
            }
            return url.toString();
        } catch (URISyntaxException ex) {
            return null;
        }
    }
}
