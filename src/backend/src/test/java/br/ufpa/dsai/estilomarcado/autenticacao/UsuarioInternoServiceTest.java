package br.ufpa.dsai.estilomarcado.autenticacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.firebase.auth.UserRecord;

import br.ufpa.dsai.estilomarcado.autenticacao.model.EstadoConta;
import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;
import br.ufpa.dsai.estilomarcado.autenticacao.model.Usuario;
import br.ufpa.dsai.estilomarcado.autenticacao.repository.UsuarioRepository;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioAtual;
import br.ufpa.dsai.estilomarcado.autenticacao.service.AuditoriaService;
import br.ufpa.dsai.estilomarcado.autenticacao.service.EmailAutenticacaoGateway;
import br.ufpa.dsai.estilomarcado.autenticacao.service.FirebaseIdentityService;
import br.ufpa.dsai.estilomarcado.autenticacao.service.SessaoService;
import br.ufpa.dsai.estilomarcado.autenticacao.service.TokenUsuarioService;
import br.ufpa.dsai.estilomarcado.autenticacao.service.UsuarioInternoService;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.ConflitoException;
import br.ufpa.dsai.estilomarcado.catalogo.model.Unidade;
import br.ufpa.dsai.estilomarcado.catalogo.repository.ProfissionalRepository;
import br.ufpa.dsai.estilomarcado.catalogo.repository.UnidadeRepository;

@ExtendWith(MockitoExtension.class)
class UsuarioInternoServiceTest {
    @Mock UsuarioRepository usuarios;
    @Mock UnidadeRepository unidades;
    @Mock ProfissionalRepository profissionais;
    @Mock TokenUsuarioService tokens;
    @Mock EmailAutenticacaoGateway email;
    @Mock UsuarioAtual usuarioAtual;
    @Mock SessaoService sessoes;
    @Mock AuditoriaService auditoria;
    @Mock FirebaseIdentityService firebase;
    @Mock UserRecord remoto;
    @Mock Unidade unidade;
    @InjectMocks UsuarioInternoService service;

    @Test
    void naoPromoveClienteSemEmailFirebaseVerificado() throws Exception {
        Usuario cliente = new Usuario("Cliente", "conta@example.com", "conta@example.com",
                null, PerfilUsuario.CLIENTE, EstadoConta.ATIVA, null, null);
        cliente.setFirebaseUid("uid-1");
        when(unidades.findById(1L)).thenReturn(Optional.of(unidade));
        when(usuarios.findWithLockByEmailNormalizado("conta@example.com"))
                .thenReturn(Optional.of(cliente));
        when(firebase.enabled()).thenReturn(true);
        when(firebase.getUser("uid-1")).thenReturn(remoto);
        when(remoto.isEmailVerified()).thenReturn(false);

        assertThrows(ConflitoException.class, () -> service.garantirAdministradorConfigurado(
                1L, "Cliente", "conta@example.com"));
        assertEquals(PerfilUsuario.CLIENTE, cliente.getPerfil());
        verifyNoInteractions(sessoes, auditoria);
    }

    @Test
    void vinculaIdentidadeFirebaseExistenteEVerificadaSemCriarOutra() throws Exception {
        when(unidades.findById(1L)).thenReturn(Optional.of(unidade));
        when(usuarios.findWithLockByEmailNormalizado("novo@example.com"))
                .thenReturn(Optional.empty());
        when(firebase.enabled()).thenReturn(true);
        when(firebase.findByEmail("novo@example.com")).thenReturn(remoto);
        when(remoto.isEmailVerified()).thenReturn(true);
        when(remoto.getEmail()).thenReturn("Novo@Example.com");
        when(remoto.getUid()).thenReturn("uid-existente");
        when(usuarios.saveAndFlush(any(Usuario.class)))
                .thenAnswer(invocacao -> invocacao.getArgument(0));

        service.garantirAdministradorConfigurado(1L, "Novo administrador", "novo@example.com");

        var captor = org.mockito.ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).saveAndFlush(captor.capture());
        assertEquals(PerfilUsuario.ADMINISTRADOR, captor.getValue().getPerfil());
        assertEquals(EstadoConta.ATIVA, captor.getValue().getEstado());
        assertEquals("uid-existente", captor.getValue().getFirebaseUid());
        verifyNoInteractions(email);
    }
}
