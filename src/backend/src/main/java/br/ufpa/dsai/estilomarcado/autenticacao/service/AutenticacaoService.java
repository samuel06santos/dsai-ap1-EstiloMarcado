package br.ufpa.dsai.estilomarcado.autenticacao.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final String hashFicticio;

    public AutenticacaoService(UsuarioRepository usuarioRepository,
                               PasswordEncoder passwordEncoder,
                               TokenUsuarioService tokenService,
                               EmailAutenticacaoGateway emailGateway,
                               AuditoriaService auditoria,
                               SessaoService sessaoService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.emailGateway = emailGateway;
        this.auditoria = auditoria;
        this.sessaoService = sessaoService;
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
    public void solicitarRecuperacao(String email, String origem) {
        String normalizado = NormalizadorEmail.normalizar(email);
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
        usuario.trocarSenha(passwordEncoder.encode(request.senha()));
        usuario.setEstado(EstadoConta.ATIVA);
        tokenService.consumir(token);
        auditoria.registrar(usuario.getId(), "CONVITE_ACEITO", true, origem, null);
    }

    private void validarConfirmacao(String senha, String confirmacao) {
        if (!Objects.equals(senha, confirmacao)) {
            throw new RegraDeNegocioException("a confirmacao da senha nao confere");
        }
    }

    public record ResultadoLogin(boolean sucesso, UsuarioPrincipal principal) {
        static ResultadoLogin sucesso(UsuarioPrincipal principal) {
            return new ResultadoLogin(true, principal);
        }

        static ResultadoLogin falha() {
            return new ResultadoLogin(false, null);
        }
    }
}
