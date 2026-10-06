package br.ufpa.dsai.estilomarcado.autenticacao;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import br.ufpa.dsai.estilomarcado.autenticacao.repository.UsuarioRepository;
import br.ufpa.dsai.estilomarcado.autenticacao.service.FirebaseIdentityService;
import br.ufpa.dsai.estilomarcado.autenticacao.service.FirebaseReconciliation;
import br.ufpa.dsai.estilomarcado.autenticacao.service.SessaoService;

class FirebaseReconciliationTest {
    private final FirebaseIdentityService firebase = mock(FirebaseIdentityService.class);
    private final UsuarioRepository users = mock(UsuarioRepository.class);
    private final SessaoService sessoes = mock(SessaoService.class);

    @Test
    void ambienteDockerAceitaSomenteContasFicticiasSemUid() {
        when(firebase.enabled()).thenReturn(true);
        when(users.countByFirebaseUidIsNull()).thenReturn(16L);
        when(users.countByFirebaseUidIsNullAndEmailNormalizadoEndingWith("@estilomarcado.dev"))
                .thenReturn(16L);

        assertDoesNotThrow(() -> reconciliacao("docker").run(null));
    }

    @Test
    void ambienteDockerBloqueiaContaRealSemUid() {
        when(firebase.enabled()).thenReturn(true);
        when(users.countByFirebaseUidIsNull()).thenReturn(17L);
        when(users.countByFirebaseUidIsNullAndEmailNormalizadoEndingWith("@estilomarcado.dev"))
                .thenReturn(16L);

        assertThrows(IllegalStateException.class, () -> reconciliacao("docker").run(null));
    }

    @Test
    void producaoBloqueiaTambemContasFicticiasSemUid() {
        when(firebase.enabled()).thenReturn(true);
        when(users.countByFirebaseUidIsNull()).thenReturn(16L);

        assertThrows(IllegalStateException.class, () -> reconciliacao("prod").run(null));
    }

    private FirebaseReconciliation reconciliacao(String perfil) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles(perfil);
        return new FirebaseReconciliation(firebase, users, sessoes, environment);
    }
}
