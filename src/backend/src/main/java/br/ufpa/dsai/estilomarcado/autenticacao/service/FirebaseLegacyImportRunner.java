package br.ufpa.dsai.estilomarcado.autenticacao.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

import br.ufpa.dsai.estilomarcado.autenticacao.repository.UsuarioRepository;

@Component
@ConditionalOnProperty(prefix = "app.auth.firebase", name = "import-existing", havingValue = "true")
@Order(0)
public class FirebaseLegacyImportRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(FirebaseLegacyImportRunner.class);
    private final FirebaseIdentityService firebase;
    private final UsuarioRepository users;
    private final JdbcTemplate jdbc;

    public FirebaseLegacyImportRunner(FirebaseIdentityService firebase, UsuarioRepository users,
                                      JdbcTemplate jdbc) {
        this.firebase = firebase;
        this.users = users;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!firebase.enabled()) throw new IllegalStateException("habilite Firebase antes da importacao");
        int imported = 0;
        for (var user : users.findAll()) {
            if (user.getFirebaseUid() != null) {
                var remote = firebase.getUser(user.getFirebaseUid());
                if (!remote.getEmail().equalsIgnoreCase(user.getEmail())) {
                    throw new IllegalStateException("e-mail divergente para usuario " + user.getId());
                }
                continue;
            }
            String uid = firebase.importLegacy(user);
            user.setFirebaseUid(uid);
            users.saveAndFlush(user);
            log.info("Conta vinculada ao Firebase: usuarioId={}, uid={}", user.getId(), uid);
            imported++;
        }
        jdbc.update("delete from spring_session_attributes");
        jdbc.update("delete from spring_session");
        log.info("Importacao Firebase concluida: {} contas vinculadas", imported);
    }
}
