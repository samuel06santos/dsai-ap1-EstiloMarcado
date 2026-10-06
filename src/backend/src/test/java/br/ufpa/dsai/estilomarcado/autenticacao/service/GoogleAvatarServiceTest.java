package br.ufpa.dsai.estilomarcado.autenticacao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.auth.UserRecord;

import br.ufpa.dsai.estilomarcado.autenticacao.model.EstadoConta;
import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;
import br.ufpa.dsai.estilomarcado.autenticacao.model.Usuario;
import br.ufpa.dsai.estilomarcado.autenticacao.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class GoogleAvatarServiceTest {

    @Mock FirebaseIdentityService firebase;
    @Mock UsuarioRepository usuarios;
    @Mock UserRecord remoto;
    @Mock UserInfo provedor;
    @InjectMocks GoogleAvatarService avatar;

    @Test
    void usaFotoDoGoogleVinculadoIndependentementeDoMetodoDaSessao() throws Exception {
        Usuario usuario = usuarioComUid();
        when(firebase.enabled()).thenReturn(true);
        when(firebase.getUser("uid-1")).thenReturn(remoto);
        when(remoto.getProviderData()).thenReturn(new UserInfo[] { provedor });
        when(provedor.getProviderId()).thenReturn("google.com");
        when(provedor.getPhotoUrl()).thenReturn("https://lh3.googleusercontent.com/a/foto=s96-c");

        assertEquals("https://lh3.googleusercontent.com/a/foto=s96-c", avatar.fotoDoUsuario(usuario));
    }

    @Test
    void semVinculoOuFirebaseDesabilitadoUsaAvatarPadrao() {
        Usuario usuario = usuarioComUid();
        assertNull(avatar.fotoDoUsuario(usuario));
        verifyNoInteractions(remoto);
    }

    @Test
    void ignoraUrlExternaMesmoQuandoVemDoGoogle() throws Exception {
        Usuario usuario = usuarioComUid();
        when(firebase.enabled()).thenReturn(true);
        when(firebase.getUser("uid-1")).thenReturn(remoto);
        when(remoto.getProviderData()).thenReturn(new UserInfo[] { provedor });
        when(provedor.getProviderId()).thenReturn("google.com");
        when(provedor.getPhotoUrl()).thenReturn("https://outro.example/foto.png");

        assertNull(avatar.fotoDoUsuario(usuario));
        assertNull(GoogleAvatarService.urlConfiavel("http://lh3.googleusercontent.com/a/foto"));
        assertNull(GoogleAvatarService.urlConfiavel("https://lh3.googleusercontent.com.evil.example/a/foto"));
        assertNull(GoogleAvatarService.urlConfiavel("https://usuario@lh3.googleusercontent.com/a/foto"));
        assertNull(GoogleAvatarService.urlConfiavel("data:image/png;base64,abc"));
    }

    @Test
    void ignoraFotoSemVinculoGoogle() throws Exception {
        when(firebase.enabled()).thenReturn(true);
        when(firebase.getUser("uid-1")).thenReturn(remoto);
        when(remoto.getProviderData()).thenReturn(new UserInfo[] { provedor });
        when(provedor.getProviderId()).thenReturn("password");

        assertNull(avatar.fotoDoUsuario(usuarioComUid()));
    }

    @Test
    void falhaNoFirebaseNaoImpedeRespostaDaSessao() throws Exception {
        when(firebase.enabled()).thenReturn(true);
        when(firebase.getUser("uid-1")).thenThrow(mock(FirebaseAuthException.class));

        assertNull(avatar.fotoDoUsuario(usuarioComUid()));
    }

    private Usuario usuarioComUid() {
        Usuario usuario = new Usuario("Cliente", "cliente@example.com", "cliente@example.com",
                null, PerfilUsuario.CLIENTE, EstadoConta.ATIVA, null, null);
        usuario.setFirebaseUid("uid-1");
        return usuario;
    }
}
