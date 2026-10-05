package br.ufpa.dsai.estilomarcado.autenticacao.service;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.List;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.ImportUserRecord;
import com.google.firebase.auth.UserImportOptions;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.auth.hash.Bcrypt;

import br.ufpa.dsai.estilomarcado.autenticacao.model.Usuario;
import br.ufpa.dsai.estilomarcado.autenticacao.model.EstadoConta;
import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;

/** Credenciais no Firebase, permissões e sessão HTTP na aplicação. */
@Service
public class FirebaseIdentityService {

    private final boolean enabled;
    private final String projectId;
    private final String apiKey;
    private final String resetEndpoint;
    private final FirebaseAuth auth;
    private final RestClient rest = RestClient.create();

    public FirebaseIdentityService(
            @Value("${app.auth.firebase.enabled:false}") boolean enabled,
            @Value("${app.auth.firebase.project-id:}") String projectId,
            @Value("${app.auth.firebase.web-api-key:}") String apiKey,
            @Value("${app.auth.firebase.credentials-path:}") String credentialsPath) throws IOException {
        this.enabled = enabled;
        this.projectId = projectId;
        this.apiKey = apiKey;
        String emulatorHost = System.getenv("FIREBASE_AUTH_EMULATOR_HOST");
        this.resetEndpoint = (emulatorHost == null || emulatorHost.isBlank()
                ? "https://identitytoolkit.googleapis.com"
                : "http://" + emulatorHost + "/identitytoolkit.googleapis.com")
                + "/v1/accounts:resetPassword?key=" + apiKey;
        if (!enabled) {
            auth = null;
            return;
        }
        if (projectId.isBlank() || apiKey.isBlank()) {
            throw new IllegalStateException("Firebase exige FIREBASE_PROJECT_ID e FIREBASE_WEB_API_KEY");
        }
        GoogleCredentials credentials;
        if (emulatorHost != null && !emulatorHost.isBlank()) {
            credentials = GoogleCredentials.create(new AccessToken("emulator", Date.from(Instant.now().plusSeconds(86400))));
        } else if (!credentialsPath.isBlank()) {
            try (FileInputStream input = new FileInputStream(credentialsPath)) {
                credentials = GoogleCredentials.fromStream(input);
            }
        } else {
            credentials = GoogleCredentials.getApplicationDefault();
        }
        if (credentials instanceof ServiceAccountCredentials serviceAccount
                && !projectId.equals(serviceAccount.getProjectId())) {
            throw new IllegalStateException("a conta de servico Firebase pertence a outro projeto");
        }
        FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
                .setCredentials(credentials).setProjectId(projectId).build(), "estilo-marcado-auth");
        auth = FirebaseAuth.getInstance(app);
    }

    public boolean enabled() { return enabled; }

    public FirebaseToken verify(String idToken) throws FirebaseAuthException {
        exigirHabilitado();
        return auth.verifyIdToken(idToken, true);
    }

    public UserRecord getUser(String uid) throws FirebaseAuthException {
        exigirHabilitado();
        return auth.getUser(uid);
    }

    public UserRecord getUserByEmail(String email) throws FirebaseAuthException {
        exigirHabilitado();
        return auth.getUserByEmail(email);
    }

    public UserRecord findUser(String uid) throws FirebaseAuthException {
        try { return getUser(uid); }
        catch (FirebaseAuthException ex) {
            if (ex.getAuthErrorCode() == AuthErrorCode.USER_NOT_FOUND) return null;
            throw ex;
        }
    }

    public UserRecord findByEmail(String email) throws FirebaseAuthException {
        try { return getUserByEmail(email); }
        catch (FirebaseAuthException ex) {
            if (ex.getAuthErrorCode() == AuthErrorCode.USER_NOT_FOUND
                    || ex.getAuthErrorCode() == AuthErrorCode.EMAIL_NOT_FOUND) return null;
            throw ex;
        }
    }

    /** Importação individual para evitar sobrescrita silenciosa do SDK em lote. */
    public String importLegacy(Usuario local) throws FirebaseAuthException {
        String uid = uidFor(local.getId());
        UserRecord byUid = findUser(uid);
        UserRecord byEmail = findByEmail(local.getEmail());
        if (byUid != null || byEmail != null) {
            if (byUid != null && byEmail != null && byUid.getUid().equals(byEmail.getUid())
                    && byUid.getEmail().equalsIgnoreCase(local.getEmail())) return uid;
            throw new IllegalStateException("colisao de UID ou e-mail Firebase para usuario " + local.getId());
        }
        boolean pending = local.getEstado() == EstadoConta.PENDENTE;
        boolean disabled = local.getEstado() == EstadoConta.BLOQUEADA
                || local.getEstado() == EstadoConta.DESATIVADA
                || (pending && local.getPerfil() != PerfilUsuario.CLIENTE);
        if (local.getSenhaHash() == null) {
            createUser(uid, local.getEmail(), local.getNome(), null, !pending, disabled);
            return uid;
        }
        ImportUserRecord record = ImportUserRecord.builder().setUid(uid)
                .setEmail(local.getEmail()).setDisplayName(local.getNome())
                .setEmailVerified(!pending).setDisabled(disabled)
                .setPasswordHash(local.getSenhaHash().getBytes(StandardCharsets.UTF_8)).build();
        var result = auth.importUsers(List.of(record), UserImportOptions.withHash(Bcrypt.getInstance()));
        if (result.getFailureCount() != 0) {
            throw new IllegalStateException("falha ao importar usuario " + local.getId());
        }
        return uid;
    }

    public UserRecord createUser(String uid, String email, String name, String password,
                                 boolean verified, boolean disabled) throws FirebaseAuthException {
        exigirHabilitado();
        UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                .setUid(uid).setEmail(email).setDisplayName(name)
                .setEmailVerified(verified).setDisabled(disabled);
        if (password != null) request.setPassword(password);
        return auth.createUser(request);
    }

    public void updateUser(String uid, String password, boolean verified, boolean disabled)
            throws FirebaseAuthException {
        UserRecord.UpdateRequest request = new UserRecord.UpdateRequest(uid)
                .setEmailVerified(verified).setDisabled(disabled);
        if (password != null) request.setPassword(password);
        auth.updateUser(request);
    }

    public void disable(String uid, boolean disabled) throws FirebaseAuthException {
        auth.updateUser(new UserRecord.UpdateRequest(uid).setDisabled(disabled));
        if (disabled) auth.revokeRefreshTokens(uid);
    }

    public void delete(String uid) throws FirebaseAuthException { auth.deleteUser(uid); }
    public void revoke(String uid) throws FirebaseAuthException { auth.revokeRefreshTokens(uid); }

    public Iterable<com.google.firebase.auth.ExportedUserRecord> allUsers() throws FirebaseAuthException {
        return auth.listUsers(null).iterateAll();
    }

    /** Compensa a conta remota caso a transação local não confirme. */
    public void deleteIfLocalRollback(String uid) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try { delete(uid); } catch (Exception ignored) { /* reconciliacao registra o orfao */ }
                }
            }
        });
    }

    public String verificationLink(String email) throws FirebaseAuthException {
        return auth.generateEmailVerificationLink(email);
    }

    public String resetLink(String email) throws FirebaseAuthException {
        return auth.generatePasswordResetLink(email);
    }

    public String resetPassword(String code, String newPassword) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = rest.post()
                .uri(resetEndpoint)
                .body(Map.of("oobCode", code, "newPassword", newPassword))
                .retrieve().body(Map.class);
        return response == null ? null : (String) response.get("email");
    }

    public String uidFor(Long localId) { return "usuario-" + localId; }

    public long authTimeMillis(FirebaseToken token) {
        Object value = token.getClaims().get("auth_time");
        if (!(value instanceof Number number)) throw new IllegalArgumentException("auth_time ausente");
        return number.longValue() * 1000L;
    }

    private void exigirHabilitado() {
        if (!enabled) throw new IllegalStateException("Firebase Authentication não está habilitado");
    }
}
