package br.ufpa.dsai.estilomarcado.autenticacao.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.ufpa.dsai.estilomarcado.autenticacao.model.EstadoConta;
import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;
import br.ufpa.dsai.estilomarcado.autenticacao.repository.UsuarioRepository;

@Component
@Order(1)
public class FirebaseReconciliation implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(FirebaseReconciliation.class);
    private final FirebaseIdentityService firebase;
    private final UsuarioRepository users;
    private final SessaoService sessoes;
    private final Environment environment;

    public FirebaseReconciliation(FirebaseIdentityService firebase, UsuarioRepository users,
                                  SessaoService sessoes, Environment environment) {
        this.firebase = firebase;
        this.users = users;
        this.sessoes = sessoes;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!firebase.enabled()) return;
        long semUid = users.countByFirebaseUidIsNull();
        if (semUid == 0) return;
        if (environment.acceptsProfiles(Profiles.of("docker"))
                && !environment.acceptsProfiles(Profiles.of("prod"))
                && users.countByFirebaseUidIsNullAndEmailNormalizadoEndingWith("@estilomarcado.dev") == semUid) {
            log.warn("{} contas mock sem UID Firebase no ambiente de desenvolvimento; "
                    + "elas nao poderao entrar ate serem importadas", semUid);
            return;
        }
        throw new IllegalStateException("existem contas sem UID Firebase; execute a importacao antes do corte");
    }

    @Scheduled(initialDelay = 600_000, fixedDelay = 86_400_000)
    public void verificar() {
        if (!firebase.enabled()) return;
        try {
            for (var local : users.findAll()) {
                if (local.getFirebaseUid() == null) {
                    log.warn("Conta local sem UID Firebase: id={}", local.getId());
                    continue;
                }
                var remote = firebase.findUser(local.getFirebaseUid());
                boolean disabled = local.getEstado() == EstadoConta.DESATIVADA
                        || local.getEstado() == EstadoConta.BLOQUEADA
                        || (local.getEstado() == EstadoConta.PENDENTE
                            && local.getPerfil() != PerfilUsuario.CLIENTE);
                if (remote == null || !local.getEmail().equalsIgnoreCase(remote.getEmail())
                        || disabled != remote.isDisabled()
                        || (local.getEstado() == EstadoConta.ATIVA && !remote.isEmailVerified())) {
                    log.warn("Divergencia de identidade Firebase: id={}", local.getId());
                }
                if (remote != null) {
                    sessoes.invalidarRevogadas(local.getEmailNormalizado(),
                            remote.getTokensValidAfterTimestamp());
                    if (remote.isDisabled()) sessoes.invalidarTodas(local.getEmailNormalizado());
                }
            }
            for (var remote : firebase.allUsers()) {
                if (!users.existsByFirebaseUid(remote.getUid())) {
                    log.warn("Conta remota sem vinculo local: uid={}", remote.getUid());
                }
            }
        } catch (Exception ex) {
            log.error("Falha na reconciliacao Firebase: {}", ex.getClass().getSimpleName());
        }
    }
}
