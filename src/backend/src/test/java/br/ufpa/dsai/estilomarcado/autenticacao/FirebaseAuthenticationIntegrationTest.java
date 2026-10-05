package br.ufpa.dsai.estilomarcado.autenticacao;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;

import br.ufpa.dsai.estilomarcado.autenticacao.model.EstadoConta;
import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;
import br.ufpa.dsai.estilomarcado.autenticacao.model.Usuario;
import br.ufpa.dsai.estilomarcado.autenticacao.repository.UsuarioRepository;
import br.ufpa.dsai.estilomarcado.autenticacao.service.FirebaseIdentityService;

@SpringBootTest(properties = {"app.auth.firebase.enabled=true", "app.auth.firebase.project-id=test-project",
        "app.auth.firebase.web-api-key=test-key"})
@AutoConfigureMockMvc
@Testcontainers
class FirebaseAuthenticationIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository users;
    @MockitoBean FirebaseIdentityService firebase;

    @Test
    void uidVinculadoEntraSemConfiarEmPerfilDoNavegador() throws Exception {
        when(firebase.enabled()).thenReturn(true);
        Usuario user = new Usuario("Cliente Teste", "firebase-test@example.com", "firebase-test@example.com",
                null, PerfilUsuario.CLIENTE, EstadoConta.ATIVA, null, null);
        user.setFirebaseUid("uid-test-1");
        users.saveAndFlush(user);
        token("uid-test-1", "firebase-test@example.com", "password");

        mvc.perform(post("/api/autenticacao/sessoes/firebase").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"valid-token\",\"perfil\":\"ADMINISTRADOR\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/autenticacao/sessoes/firebase").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"valid-token\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.perfil").value("CLIENTE"));
    }

    @Test
    void emailCoincidenteComOutroUidNaoFundeContas() throws Exception {
        when(firebase.enabled()).thenReturn(true);
        Usuario user = new Usuario("Existente", "collision@example.com", "collision@example.com",
                null, PerfilUsuario.CLIENTE, EstadoConta.ATIVA, null, null);
        user.setFirebaseUid("uid-original");
        users.saveAndFlush(user);
        token("uid-outro", "collision@example.com", "google.com");

        mvc.perform(post("/api/autenticacao/sessoes/firebase").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"valid-token\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void contaPendenteNaoRecebeSessaoAntesDaVerificacao() throws Exception {
        when(firebase.enabled()).thenReturn(true);
        Usuario user = new Usuario("Pendente", "pending-firebase@example.com", "pending-firebase@example.com",
                null, PerfilUsuario.CLIENTE, EstadoConta.PENDENTE, null, null);
        user.setFirebaseUid("uid-pending");
        users.saveAndFlush(user);
        token("uid-pending", "pending-firebase@example.com", "password");
        FirebaseToken verified = firebase.verify("valid-token");
        when(verified.isEmailVerified()).thenReturn(false);

        mvc.perform(post("/api/autenticacao/sessoes/firebase").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"valid-token\"}"))
                .andExpect(status().isUnauthorized());
        org.junit.jupiter.api.Assertions.assertEquals(EstadoConta.PENDENTE,
                users.findById(user.getId()).orElseThrow().getEstado());
    }

    @Test
    void contaDesativadaNaoRecebeSessaoMesmoComTokenValido() throws Exception {
        when(firebase.enabled()).thenReturn(true);
        Usuario user = new Usuario("Desativada", "disabled-firebase@example.com", "disabled-firebase@example.com",
                null, PerfilUsuario.CLIENTE, EstadoConta.DESATIVADA, null, null);
        user.setFirebaseUid("uid-disabled");
        users.saveAndFlush(user);
        token("uid-disabled", "disabled-firebase@example.com", "password");

        mvc.perform(post("/api/autenticacao/sessoes/firebase").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"valid-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void contaDesabilitadaNoFirebaseNaoRecebeSessao() throws Exception {
        when(firebase.enabled()).thenReturn(true);
        Usuario user = new Usuario("Ativa", "remote-disabled@example.com", "remote-disabled@example.com",
                null, PerfilUsuario.CLIENTE, EstadoConta.ATIVA, null, null);
        user.setFirebaseUid("uid-remote-disabled");
        users.saveAndFlush(user);
        token("uid-remote-disabled", "remote-disabled@example.com", "password");
        when(firebase.getUser("uid-remote-disabled").isDisabled()).thenReturn(true);

        mvc.perform(post("/api/autenticacao/sessoes/firebase").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"valid-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void primeiraEntradaGoogleCriaSomentePerfilCliente() throws Exception {
        when(firebase.enabled()).thenReturn(true);
        token("uid-google-new", "new-google-firebase@example.com", "google.com");

        mvc.perform(post("/api/autenticacao/sessoes/firebase").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"valid-token\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.perfil").value("CLIENTE"));
        Usuario created = users.findByFirebaseUid("uid-google-new").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(PerfilUsuario.CLIENTE, created.getPerfil());
    }

    private void token(String uid, String email, String provider) throws Exception {
        FirebaseToken token = mock(FirebaseToken.class);
        UserRecord remote = mock(UserRecord.class);
        when(firebase.verify(eq("valid-token"))).thenReturn(token);
        when(firebase.getUser(eq(uid))).thenReturn(remote);
        when(firebase.authTimeMillis(token)).thenReturn(Instant.now().minusSeconds(10).toEpochMilli());
        when(token.getUid()).thenReturn(uid);
        when(token.getEmail()).thenReturn(email);
        when(token.isEmailVerified()).thenReturn(true);
        when(token.getClaims()).thenReturn(Map.of("firebase", Map.of("sign_in_provider", provider)));
        when(remote.isEmailVerified()).thenReturn(true);
        when(remote.getEmail()).thenReturn(email);
    }
}
