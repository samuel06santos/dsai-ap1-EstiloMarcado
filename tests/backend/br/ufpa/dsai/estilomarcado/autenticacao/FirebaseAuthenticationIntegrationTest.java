package br.ufpa.dsai.estilomarcado.autenticacao;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import jakarta.servlet.http.Cookie;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
import br.ufpa.dsai.estilomarcado.mcp.McpTokenService;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"app.auth.firebase.enabled=true", "app.auth.firebase.project-id=test-project",
        "app.auth.firebase.web-api-key=placeholder", "app.mcp.service-key=test-service-key"})
@AutoConfigureMockMvc
@Testcontainers
class FirebaseAuthenticationIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository users;
    @Autowired McpTokenService mcpTokens;
    @Autowired ObjectMapper mapper;
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

    @Test
    void loginGoogleConcedeJwtMcpAoMesmoCliente() throws Exception {
        oauthAposLoginFirebase("google.com", "uid-google-mcp", "google-mcp@example.com");
    }

    @Test
    void loginEmailSenhaFirebaseConcedeJwtMcpAoMesmoCliente() throws Exception {
        oauthAposLoginFirebase("password", "uid-password-mcp", "password-mcp@example.com");
    }

    private void oauthAposLoginFirebase(String provider, String uid, String email) throws Exception {
        when(firebase.enabled()).thenReturn(true);
        Usuario usuario = new Usuario("Cliente MCP", email, email, null,
                PerfilUsuario.CLIENTE, EstadoConta.ATIVA, null, null);
        usuario.setFirebaseUid(uid);
        usuario = users.saveAndFlush(usuario);
        token(uid, email, provider);
        Cookie sessao = mvc.perform(post("/api/autenticacao/sessoes/firebase").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"valid-token\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
        assertNotNull(sessao);
        String redirect = "http://127.0.0.1:1234/callback";
        String clientId = (String) mapper.readValue(mvc.perform(post("/oauth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"client_name\":\"Teste OAuth\",\"redirect_uris\":[\"" + redirect + "\"]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray(), Map.class)
                .get("client_id");
        String verifier = "v".repeat(43);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        mvc.perform(get("/oauth/authorize").cookie(sessao)
                .param("response_type", "code").param("client_id", clientId).param("redirect_uri", redirect)
                .param("code_challenge", challenge).param("code_challenge_method", "S256")
                .param("scope", "mcp:read").param("state", "estado-mcp")
                .param("resource", mcpTokens.audience())).andExpect(status().isOk());
        String retorno = mvc.perform(post("/oauth/authorize").cookie(sessao).with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("response_type", "code").param("client_id", clientId).param("redirect_uri", redirect)
                .param("code_challenge", challenge).param("code_challenge_method", "S256")
                .param("scope", "mcp:read").param("state", "estado-mcp")
                .param("resource", mcpTokens.audience()).param("decisao", "permitir"))
                .andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
        assertNotNull(retorno);
        String code = new URI(retorno).getRawQuery().split("code=")[1].split("&")[0];
        String jwt = (String) mapper.readValue(mvc.perform(post("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code").param("code", code)
                .param("client_id", clientId).param("redirect_uri", redirect)
                .param("code_verifier", verifier)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray(), Map.class).get("access_token");
        assertEquals(usuario.getId(), mcpTokens.validar("Bearer " + jwt).principal().id());
        mvc.perform(get("/api/mcp/internal/me/lista-espera")
                .header("X-MCP-Service-Key", "test-service-key")
                .header("Authorization", "Bearer " + jwt)).andExpect(status().isOk());
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
