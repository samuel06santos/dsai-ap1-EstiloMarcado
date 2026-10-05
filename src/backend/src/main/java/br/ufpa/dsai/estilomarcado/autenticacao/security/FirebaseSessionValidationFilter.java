package br.ufpa.dsai.estilomarcado.autenticacao.security;

import java.io.IOException;
import java.time.Instant;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import br.ufpa.dsai.estilomarcado.autenticacao.model.EstadoConta;
import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;
import br.ufpa.dsai.estilomarcado.autenticacao.repository.UsuarioRepository;
import br.ufpa.dsai.estilomarcado.autenticacao.service.FirebaseIdentityService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class FirebaseSessionValidationFilter extends OncePerRequestFilter {
    public static final String UID = "FIREBASE_UID";
    public static final String AUTH_TIME = "FIREBASE_AUTH_TIME";
    public static final String CHECKED_AT = "FIREBASE_CHECKED_AT";

    private final FirebaseIdentityService firebase;
    private final UsuarioRepository users;

    public FirebaseSessionValidationFilter(FirebaseIdentityService firebase, UsuarioRepository users) {
        this.firebase = firebase;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (firebase.enabled()) {
            HttpSession session = request.getSession(false);
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (session != null && authentication != null
                    && authentication.getPrincipal() instanceof UsuarioPrincipal principal) {
                String uid = session.getAttribute(UID) instanceof String value ? value : null;
                Long authTime = session.getAttribute(AUTH_TIME) instanceof Long value ? value : null;
                var local = users.findForSessionById(principal.id());
                boolean valid = uid != null && authTime != null && local.isPresent()
                        && local.get().getEstado() == EstadoConta.ATIVA
                        && uid.equals(local.get().getFirebaseUid())
                        && local.get().getPerfil() == principal.perfil()
                        && java.util.Objects.equals(principal.unidadeId(),
                                local.get().getUnidade() == null ? null : local.get().getUnidade().getId())
                        && java.util.Objects.equals(principal.profissionalId(),
                                local.get().getProfissional() == null ? null : local.get().getProfissional().getId());
                Instant checked = session.getAttribute(CHECKED_AT) instanceof Instant value ? value : null;
                boolean due = checked == null || checked.plusSeconds(300).isBefore(Instant.now());
                boolean sensitive = principal.perfil() == PerfilUsuario.ADMINISTRADOR
                        && !"GET".equals(request.getMethod())
                        && !"HEAD".equals(request.getMethod())
                        && !"OPTIONS".equals(request.getMethod());
                if (valid && (due || sensitive)) {
                    try {
                        var remote = firebase.getUser(uid);
                        valid = !remote.isDisabled() && remote.isEmailVerified()
                                && remote.getTokensValidAfterTimestamp() <= authTime
                                && remote.getEmail() != null
                                && remote.getEmail().equalsIgnoreCase(local.get().getEmail());
                        if (valid) session.setAttribute(CHECKED_AT, Instant.now());
                    } catch (Exception ex) { valid = false; }
                }
                if (!valid) {
                    session.invalidate();
                    SecurityContextHolder.clearContext();
                }
            }
        }
        chain.doFilter(request, response);
    }
}
