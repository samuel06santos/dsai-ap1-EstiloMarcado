package br.ufpa.dsai.estilomarcado.autenticacao.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Map;

import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import br.ufpa.dsai.estilomarcado.autenticacao.api.dto.CadastroRequest;
import br.ufpa.dsai.estilomarcado.autenticacao.api.dto.NovaSenhaRequest;
import br.ufpa.dsai.estilomarcado.autenticacao.model.EstadoConta;
import br.ufpa.dsai.estilomarcado.autenticacao.model.FinalidadeToken;
import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;
import br.ufpa.dsai.estilomarcado.autenticacao.model.TokenUsuario;
import br.ufpa.dsai.estilomarcado.autenticacao.model.Usuario;
import br.ufpa.dsai.estilomarcado.autenticacao.repository.UsuarioRepository;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioPrincipal;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.RegraDeNegocioException;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.ConflitoException;

@Service
public class AutenticacaoService {

    private static final Duration VALIDADE_ATIVACAO = Duration.ofHours(24);
    private static final Duration VALIDADE_RECUPERACAO = Duration.ofMinutes(30);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenUsuarioService tokenService;
    private final EmailAutenticacaoGateway emailGateway;
    private final AuditoriaService auditoria;
    private final SessaoService sessaoService;
    private final FirebaseIdentityService firebase;
    private final String hashFicticio;

    public AutenticacaoService(UsuarioRepository usuarioRepository,
                               PasswordEncoder passwordEncoder,
                               TokenUsuarioService tokenService,
                               EmailAutenticacaoGateway emailGateway,
                               AuditoriaService auditoria,
                               SessaoService sessaoService,
                               FirebaseIdentityService firebase) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.emailGateway = emailGateway;
        this.auditoria = auditoria;
        this.sessaoService = sessaoService;
        this.firebase = firebase;
        this.hashFicticio = passwordEncoder.encode("senha-ficticia-2026");
    }

    @Transactional
    public void cadastrarCliente(CadastroRequest request, String origem) {
        validarConfirmacao(request.senha(), request.confirmacaoSenha());
        String emailNormalizado = NormalizadorEmail.normalizar(request.email());
        if (usuarioRepository.existsByEmailNormalizado(emailNormalizado)) {
            auditoria.registrar(null, "CADASTRO_DUPLICADO", false, origem, "solicitacao ignorada");
            return;
        }

        if (firebase.enabled()) {
            try {
                if (firebase.findByEmail(request.email().trim()) != null) {
                    auditoria.registrar(null, "CADASTRO_DUPLICADO", false, origem, "solicitacao ignorada");
                    return;
                }
            } catch (FirebaseAuthException ex) { throw indisponivel(ex); }
            Usuario usuario = usuarioRepository.saveAndFlush(new Usuario(
                    request.nome().trim(), request.email().trim(), emailNormalizado,
                    null, PerfilUsuario.CLIENTE, EstadoConta.PENDENTE, null, null));
            String uid = firebase.uidFor(usuario.getId());
            try {
                firebase.createUser(uid, usuario.getEmail(), usuario.getNome(), request.senha(), false, false);
                firebase.deleteIfLocalRollback(uid);
                usuario.setFirebaseUid(uid);
                usuario.setSenhaFirebase(true);
                emailGateway.enviarLinkAtivacao(usuario.getEmail(), usuario.getNome(),
                        firebase.verificationLink(usuario.getEmail()));
                auditoria.registrar(usuario.getId(), "CADASTRO", true, origem, "firebase");
                return;
            } catch (FirebaseAuthException ex) {
                if (ex.getAuthErrorCode() == AuthErrorCode.EMAIL_ALREADY_EXISTS) {
                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                    return;
                }
                try { firebase.delete(uid); } catch (Exception ignored) { /* reconciliacao operacional */ }
                throw indisponivel(ex);
            } catch (Exception ex) {
                try { firebase.delete(uid); } catch (Exception ignored) { /* reconciliacao operacional */ }
                throw indisponivel(ex);
            }
        }

        Usuario usuario = new Usuario(
                request.nome().trim(), request.email().trim(), emailNormalizado,
                passwordEncoder.encode(request.senha()), PerfilUsuario.CLIENTE,
                EstadoConta.PENDENTE, null, null);
        usuarioRepository.save(usuario);
        String token = tokenService.emitir(usuario, FinalidadeToken.ATIVACAO, VALIDADE_ATIVACAO);
        emailGateway.enviarAtivacao(usuario.getEmail(), usuario.getNome(), token);
        auditoria.registrar(usuario.getId(), "CADASTRO", true, origem, null);
    }

    @Transactional
    public void ativar(String valorToken, String origem) {
        if (firebase.enabled()) throw new RegraDeNegocioException("use o link de verificacao do Firebase");
        TokenUsuario token = tokenService.validar(valorToken, FinalidadeToken.ATIVACAO);
        Usuario usuario = token.getUsuario();
        if (usuario.getEstado() != EstadoConta.PENDENTE || usuario.getPerfil() != PerfilUsuario.CLIENTE) {
            throw new br.ufpa.dsai.estilomarcado.autenticacao.exception.TokenInvalidoException();
        }
        usuario.setEstado(EstadoConta.ATIVA);
        tokenService.consumir(token);
        auditoria.registrar(usuario.getId(), "ATIVACAO", true, origem, null);
    }

    @Transactional
    public void reenviarAtivacao(String email, String origem) {
        String normalizado = NormalizadorEmail.normalizar(email);
        if (firebase.enabled()) {
            usuarioRepository.findByEmailNormalizado(normalizado)
                    .filter(usuario -> usuario.getEstado() == EstadoConta.PENDENTE)
                    .filter(usuario -> usuario.getPerfil() == PerfilUsuario.CLIENTE)
                    .ifPresent(usuario -> {
                        try {
                            emailGateway.enviarLinkAtivacao(usuario.getEmail(), usuario.getNome(),
                                    firebase.verificationLink(usuario.getEmail()));
                        } catch (FirebaseAuthException ex) { throw indisponivel(ex); }
                    });
            return;
        }
        usuarioRepository.findByEmailNormalizado(normalizado)
                .filter(usuario -> usuario.getEstado() == EstadoConta.PENDENTE)
                .filter(usuario -> usuario.getPerfil() == PerfilUsuario.CLIENTE)
                .ifPresent(usuario -> {
                    String token = tokenService.emitir(usuario, FinalidadeToken.ATIVACAO, VALIDADE_ATIVACAO);
                    emailGateway.enviarAtivacao(usuario.getEmail(), usuario.getNome(), token);
                    auditoria.registrar(usuario.getId(), "REENVIO_ATIVACAO", true, origem, null);
                });
    }

    @Transactional
    public ResultadoLogin autenticar(String email, String senha, String origem) {
        if (firebase.enabled()) throw new RegraDeNegocioException("login local desativado");
        String normalizado = NormalizadorEmail.normalizar(email);
        Usuario usuario = usuarioRepository.findWithLockByEmailNormalizado(normalizado).orElse(null);
        if (usuario == null) {
            passwordEncoder.matches(senha, hashFicticio);
            auditoria.registrar(null, "LOGIN", false, origem, "credenciais invalidas");
            return ResultadoLogin.falha();
        }

        Instant agora = Instant.now();
        usuario.desbloquearSeExpirado(agora);
        boolean senhaValida = usuario.getSenhaHash() != null
                && passwordEncoder.matches(senha, usuario.getSenhaHash());
        if (usuario.getEstado() != EstadoConta.ATIVA || !senhaValida) {
            if (usuario.getEstado() == EstadoConta.ATIVA && !senhaValida) {
                usuario.registrarFalha(agora);
            }
            auditoria.registrar(usuario.getId(), "LOGIN", false, origem, "credenciais ou estado invalidos");
            return ResultadoLogin.falha();
        }

        usuario.limparFalhas();
        auditoria.registrar(usuario.getId(), "LOGIN", true, origem, null);
        return ResultadoLogin.sucesso(UsuarioPrincipal.from(usuario));
    }

    @Transactional
    public ResultadoLogin autenticarFirebase(String idToken, String origem) {
        if (!firebase.enabled() || idToken == null || idToken.isBlank()) return ResultadoLogin.falha();
        try {
            FirebaseToken token = firebase.verify(idToken);
            UserRecord remoto = firebase.getUser(token.getUid());
            if (remoto.isDisabled() || !token.isEmailVerified() || !remoto.isEmailVerified()
                    || token.getEmail() == null || !token.getEmail().equalsIgnoreCase(remoto.getEmail())) {
                return ResultadoLogin.falha();
            }
            Usuario usuario = usuarioRepository.findByFirebaseUid(token.getUid()).orElse(null);
            if (usuario == null) {
                String normalizado = NormalizadorEmail.normalizar(token.getEmail());
                if (usuarioRepository.existsByEmailNormalizado(normalizado)) {
                    throw new ConflitoException("Ja existe uma conta para este e-mail. Entre com senha e vincule Google no perfil.");
                }
                if (!provedorGoogle(token)) {
                    return ResultadoLogin.falha();
                }
                String nome = token.getName() == null || token.getName().isBlank()
                        ? "Cliente" : token.getName().trim();
                usuario = new Usuario(
                        nome.substring(0, Math.min(nome.length(), 120)),
                        token.getEmail(), normalizado, null, PerfilUsuario.CLIENTE,
                        EstadoConta.ATIVA, null, null);
                usuario.setFirebaseUid(token.getUid());
                usuario = usuarioRepository.saveAndFlush(usuario);
            }
            if (!usuario.getEmailNormalizado().equals(NormalizadorEmail.normalizar(token.getEmail()))) {
                return ResultadoLogin.falha();
            }
            if (usuario.getEstado() == EstadoConta.PENDENTE && usuario.getPerfil() == PerfilUsuario.CLIENTE) {
                usuario.setEstado(EstadoConta.ATIVA);
            }
            if (usuario.getEstado() != EstadoConta.ATIVA) return ResultadoLogin.falha();
            auditoria.registrar(usuario.getId(), "LOGIN", true, origem,
                    provedorGoogle(token) ? "firebase:google" : "firebase:password");
            return ResultadoLogin.sucessoFirebase(UsuarioPrincipal.from(usuario),
                    token.getUid(), firebase.authTimeMillis(token));
        } catch (FirebaseAuthException | IllegalArgumentException ex) {
            auditoria.registrar(null, "LOGIN", false, origem, "firebase:token-invalido");
            return ResultadoLogin.falha();
        }
    }

    private boolean provedorGoogle(FirebaseToken token) {
        Object firebaseClaim = token.getClaims().get("firebase");
        return firebaseClaim instanceof Map<?, ?> dados
                && "google.com".equals(dados.get("sign_in_provider"));
    }

    @Transactional
    public void solicitarRecuperacao(String email, String origem) {
        String normalizado = NormalizadorEmail.normalizar(email);
        if (firebase.enabled()) {
            usuarioRepository.findByEmailNormalizado(normalizado)
                    .filter(usuario -> usuario.getEstado() == EstadoConta.ATIVA)
                    .filter(usuario -> usuario.getFirebaseUid() != null)
                    .ifPresent(usuario -> {
                        try {
                            emailGateway.enviarLinkRecuperacao(usuario.getEmail(), usuario.getNome(),
                                    firebase.resetLink(usuario.getEmail()));
                            auditoria.registrar(usuario.getId(), "PEDIDO_RECUPERACAO", true, origem, "firebase");
                        } catch (FirebaseAuthException ex) { throw indisponivel(ex); }
                    });
            return;
        }
        usuarioRepository.findByEmailNormalizado(normalizado)
                .filter(usuario -> usuario.getEstado() == EstadoConta.ATIVA)
                .ifPresent(usuario -> {
                    String token = tokenService.emitir(usuario, FinalidadeToken.RECUPERACAO, VALIDADE_RECUPERACAO);
                    emailGateway.enviarRecuperacao(usuario.getEmail(), usuario.getNome(), token);
                    auditoria.registrar(usuario.getId(), "PEDIDO_RECUPERACAO", true, origem, null);
                });
    }

    @Transactional
    public void redefinirSenha(NovaSenhaRequest request, String origem) {
        validarConfirmacao(request.senha(), request.confirmacaoSenha());
        if (firebase.enabled()) {
            String email;
            try { email = firebase.resetPassword(request.token(), request.senha()); }
            catch (Exception ex) { throw new RegraDeNegocioException("link invalido ou expirado"); }
            if (email == null) throw new RegraDeNegocioException("link invalido ou expirado");
            String uid;
            try { uid = firebase.getUserByEmail(email).getUid(); }
            catch (FirebaseAuthException ex) { throw indisponivel(ex); }
            usuarioRepository.findByFirebaseUid(uid)
                    .filter(usuario -> usuario.getEmailNormalizado().equals(NormalizadorEmail.normalizar(email)))
                    .ifPresent(usuario -> {
                        usuario.setSenhaFirebase(true);
                        sessaoService.invalidarTodas(usuario.getEmailNormalizado());
                        try { firebase.revoke(usuario.getFirebaseUid()); }
                        catch (FirebaseAuthException ex) { throw indisponivel(ex); }
                        emailGateway.enviarSenhaAlterada(usuario.getEmail(), usuario.getNome());
                        auditoria.registrar(usuario.getId(), "REDEFINICAO_SENHA", true, origem, "firebase");
                    });
            return;
        }
        TokenUsuario token = tokenService.validar(request.token(), FinalidadeToken.RECUPERACAO);
        Usuario usuario = token.getUsuario();
        if (usuario.getEstado() != EstadoConta.ATIVA
                || passwordEncoder.matches(request.senha(), usuario.getSenhaHash())) {
            throw new RegraDeNegocioException("a nova senha deve ser diferente da senha atual");
        }
        usuario.trocarSenha(passwordEncoder.encode(request.senha()));
        tokenService.consumir(token);
        sessaoService.invalidarTodas(usuario.getEmailNormalizado());
        emailGateway.enviarSenhaAlterada(usuario.getEmail(), usuario.getNome());
        auditoria.registrar(usuario.getId(), "REDEFINICAO_SENHA", true, origem, null);
    }

    @Transactional
    public void aceitarConvite(NovaSenhaRequest request, String origem) {
        validarConfirmacao(request.senha(), request.confirmacaoSenha());
        TokenUsuario token = tokenService.validar(request.token(), FinalidadeToken.CONVITE);
        Usuario usuario = token.getUsuario();
        if (usuario.getEstado() != EstadoConta.PENDENTE || usuario.getPerfil() == PerfilUsuario.CLIENTE) {
            throw new br.ufpa.dsai.estilomarcado.autenticacao.exception.TokenInvalidoException();
        }
        if (firebase.enabled()) {
            try { firebase.updateUser(usuario.getFirebaseUid(), request.senha(), true, false); }
            catch (FirebaseAuthException ex) { throw indisponivel(ex); }
            usuario.setSenhaFirebase(true);
        } else {
            usuario.trocarSenha(passwordEncoder.encode(request.senha()));
        }
        usuario.setEstado(EstadoConta.ATIVA);
        tokenService.consumir(token);
        auditoria.registrar(usuario.getId(), "CONVITE_ACEITO", true, origem, null);
    }

    private ResponseStatusException indisponivel(Exception ex) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "servico de identidade indisponivel", ex);
    }

    private void validarConfirmacao(String senha, String confirmacao) {
        if (!Objects.equals(senha, confirmacao)) {
            throw new RegraDeNegocioException("a confirmacao da senha nao confere");
        }
    }

    public record ResultadoLogin(boolean sucesso, UsuarioPrincipal principal,
                                  String firebaseUid, long authTimeMillis) {
        static ResultadoLogin sucesso(UsuarioPrincipal principal) {
            return new ResultadoLogin(true, principal, null, 0);
        }

        static ResultadoLogin sucessoFirebase(UsuarioPrincipal principal, String uid, long authTimeMillis) {
            return new ResultadoLogin(true, principal, uid, authTimeMillis);
        }

        static ResultadoLogin falha() {
            return new ResultadoLogin(false, null, null, 0);
        }
    }
}
