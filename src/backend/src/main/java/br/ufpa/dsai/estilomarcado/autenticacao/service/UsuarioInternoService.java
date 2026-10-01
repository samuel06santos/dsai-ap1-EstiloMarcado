package br.ufpa.dsai.estilomarcado.autenticacao.service;

import java.time.Duration;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.ufpa.dsai.estilomarcado.autenticacao.api.dto.UsuarioInternoPatchRequest;
import br.ufpa.dsai.estilomarcado.autenticacao.api.dto.UsuarioInternoRequest;
import br.ufpa.dsai.estilomarcado.autenticacao.api.dto.UsuarioResponse;
import br.ufpa.dsai.estilomarcado.autenticacao.model.EstadoConta;
import br.ufpa.dsai.estilomarcado.autenticacao.model.FinalidadeToken;
import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;
import br.ufpa.dsai.estilomarcado.autenticacao.model.Usuario;
import br.ufpa.dsai.estilomarcado.autenticacao.repository.UsuarioRepository;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioAtual;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioPrincipal;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.ConflitoException;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.RecursoNaoEncontradoException;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.RegraDeNegocioException;
import br.ufpa.dsai.estilomarcado.catalogo.model.Profissional;
import br.ufpa.dsai.estilomarcado.catalogo.model.Unidade;
import br.ufpa.dsai.estilomarcado.catalogo.repository.ProfissionalRepository;
import br.ufpa.dsai.estilomarcado.catalogo.repository.UnidadeRepository;

@Service
public class UsuarioInternoService {

    private static final Duration VALIDADE_CONVITE = Duration.ofHours(24);

    private final UsuarioRepository usuarioRepository;
    private final UnidadeRepository unidadeRepository;
    private final ProfissionalRepository profissionalRepository;
    private final TokenUsuarioService tokenService;
    private final EmailAutenticacaoGateway emailGateway;
    private final UsuarioAtual usuarioAtual;
    private final SessaoService sessaoService;
    private final AuditoriaService auditoria;

    public UsuarioInternoService(UsuarioRepository usuarioRepository,
                                 UnidadeRepository unidadeRepository,
                                 ProfissionalRepository profissionalRepository,
                                 TokenUsuarioService tokenService,
                                 EmailAutenticacaoGateway emailGateway,
                                 UsuarioAtual usuarioAtual,
                                 SessaoService sessaoService,
                                 AuditoriaService auditoria) {
        this.usuarioRepository = usuarioRepository;
        this.unidadeRepository = unidadeRepository;
        this.profissionalRepository = profissionalRepository;
        this.tokenService = tokenService;
        this.emailGateway = emailGateway;
        this.usuarioAtual = usuarioAtual;
        this.sessaoService = sessaoService;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar(Long unidadeId) {
        usuarioAtual.exigirAdministradorDaUnidade(unidadeId);
        return usuarioRepository.findByUnidadeIdOrderByNomeAsc(unidadeId).stream()
                .map(UsuarioResponse::from)
                .toList();
    }

    @Transactional
    public UsuarioResponse criar(Long unidadeId, UsuarioInternoRequest request, String origem) {
        usuarioAtual.exigirAdministradorDaUnidade(unidadeId);
        exigirFilialAtiva(unidadeId);
        validarPerfilInterno(request.perfil());
        String normalizado = NormalizadorEmail.normalizar(request.email());
        if (usuarioRepository.existsByEmailNormalizado(normalizado)) {
            throw new ConflitoException("ja existe uma conta com este e-mail");
        }

        Unidade unidade = unidadeRepository.findById(unidadeId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("unidade nao encontrada"));
        Profissional profissional = resolverProfissional(unidadeId, request.perfil(), request.profissionalId(), null);
        Usuario usuario = usuarioRepository.save(new Usuario(
                request.nome().trim(), request.email().trim(), normalizado, null,
                request.perfil(), EstadoConta.PENDENTE, unidade, profissional));
        String token = tokenService.emitir(usuario, FinalidadeToken.CONVITE, VALIDADE_CONVITE);
        emailGateway.enviarConvite(usuario.getEmail(), usuario.getNome(), token);
        auditoria.registrar(usuario.getId(), "CONTA_INTERNA_CRIADA", true, origem,
                "perfil=" + usuario.getPerfil());
        return UsuarioResponse.from(usuario);
    }

    @Transactional
    public UsuarioResponse atualizar(Long unidadeId, Long id, UsuarioInternoPatchRequest request, String origem) {
        usuarioAtual.exigirAdministradorDaUnidade(unidadeId);
        exigirFilialAtiva(unidadeId);
        UsuarioPrincipal autor = usuarioAtual.get();
        Usuario usuario = usuarioRepository.findByIdAndUnidadeId(id, unidadeId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("usuario nao encontrado"));

        boolean alteraAcesso = request.perfil() != null || request.estado() != null
                || request.profissionalId() != null;
        if (autor.id().equals(usuario.getId()) && alteraAcesso) {
            throw new RegraDeNegocioException("um administrador nao pode alterar o proprio acesso");
        }

        if (request.nome() != null) {
            usuario.setNome(request.nome().trim());
        }

        PerfilUsuario novoPerfil = request.perfil() == null ? usuario.getPerfil() : request.perfil();
        validarPerfilInterno(novoPerfil);
        Long profissionalId = request.profissionalId();
        if (novoPerfil == PerfilUsuario.PROFISSIONAL && profissionalId == null
                && usuario.getProfissional() != null) {
            profissionalId = usuario.getProfissional().getId();
        }
        Profissional profissional = resolverProfissional(unidadeId, novoPerfil, profissionalId, usuario.getId());
        usuario.setPerfil(novoPerfil);
        usuario.setProfissional(profissional);

        if (request.estado() != null) {
            if (request.estado() == EstadoConta.ATIVA && usuario.getSenhaHash() == null) {
                throw new RegraDeNegocioException("a conta precisa concluir o convite antes de ser ativada");
            }
            usuario.setEstado(request.estado());
            if (request.estado() == EstadoConta.ATIVA) {
                usuario.limparFalhas();
            }
        }

        if (alteraAcesso) {
            sessaoService.invalidarTodas(usuario.getEmailNormalizado());
            auditoria.registrar(usuario.getId(), "ACESSO_ALTERADO", true, origem,
                    "perfil=" + usuario.getPerfil() + ",estado=" + usuario.getEstado());
        }
        return UsuarioResponse.from(usuarioRepository.save(usuario));
    }

    @Transactional
    public void reenviarConvite(Long unidadeId, Long id, String origem) {
        usuarioAtual.exigirAdministradorDaUnidade(unidadeId);
        exigirFilialAtiva(unidadeId);
        Usuario usuario = usuarioRepository.findByIdAndUnidadeId(id, unidadeId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("usuario nao encontrado"));
        if (usuario.getEstado() != EstadoConta.PENDENTE || usuario.getSenhaHash() != null) {
            throw new RegraDeNegocioException("somente contas pendentes podem receber novo convite");
        }
        String token = tokenService.emitir(usuario, FinalidadeToken.CONVITE, VALIDADE_CONVITE);
        emailGateway.enviarConvite(usuario.getEmail(), usuario.getNome(), token);
        auditoria.registrar(usuario.getId(), "CONVITE_REENVIADO", true, origem, null);
    }

    @Transactional
    public void provisionarPrimeiroAdministrador(Long unidadeId, String nome, String email) {
        if (unidadeId == null || unidadeId <= 0 || email == null || email.isBlank()) {
            throw new IllegalArgumentException("bootstrap administrativo exige unidade e e-mail validos");
        }
        if (usuarioRepository.existsByUnidadeIdAndPerfil(unidadeId, PerfilUsuario.ADMINISTRADOR)) {
            return;
        }
        String normalizado = NormalizadorEmail.normalizar(email);
        if (usuarioRepository.existsByEmailNormalizado(normalizado)) {
            throw new ConflitoException("o e-mail do bootstrap ja pertence a outra conta");
        }
        Unidade unidade = unidadeRepository.findById(unidadeId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("unidade do bootstrap nao encontrada"));
        Usuario usuario = usuarioRepository.save(new Usuario(
                nome.trim(), email.trim(), normalizado, null, PerfilUsuario.ADMINISTRADOR,
                EstadoConta.PENDENTE, unidade, null));
        String token = tokenService.emitir(usuario, FinalidadeToken.CONVITE, VALIDADE_CONVITE);
        emailGateway.enviarConvite(usuario.getEmail(), usuario.getNome(), token);
        auditoria.registrar(usuario.getId(), "PRIMEIRO_ADMIN_PROVISIONADO", true, "bootstrap", null);
    }

    @Transactional
    public void convidarPrimeiroAdministradorNovaFilial(Unidade unidade, String nome,
                                                         String email, String origem) {
        String normalizado = NormalizadorEmail.normalizar(email);
        if (usuarioRepository.existsByEmailNormalizado(normalizado)) {
            throw new ConflitoException("ja existe uma conta com este e-mail");
        }
        Usuario usuario = usuarioRepository.save(new Usuario(
                nome.trim(), email.trim(), normalizado, null, PerfilUsuario.ADMINISTRADOR,
                EstadoConta.PENDENTE, unidade, null));
        String token = tokenService.emitir(usuario, FinalidadeToken.CONVITE, VALIDADE_CONVITE);
        emailGateway.enviarConvite(usuario.getEmail(), usuario.getNome(), token);
        auditoria.registrar(usuario.getId(), "PRIMEIRO_ADMIN_PROVISIONADO", true, origem,
                "unidade=" + unidade.getId());
    }

    private void validarPerfilInterno(PerfilUsuario perfil) {
        if (perfil == null || perfil == PerfilUsuario.CLIENTE) {
            throw new RegraDeNegocioException("perfil de conta interna invalido");
        }
    }

    private void exigirFilialAtiva(Long unidadeId) {
        Unidade unidade = unidadeRepository.findById(unidadeId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("filial nao encontrada"));
        if (!unidade.isAtiva()) {
            throw new AccessDeniedException("filial inativa");
        }
    }

    private Profissional resolverProfissional(Long unidadeId, PerfilUsuario perfil,
                                               Long profissionalId, Long usuarioIgnorado) {
        if (perfil != PerfilUsuario.PROFISSIONAL) {
            return null;
        }
        if (profissionalId == null) {
            throw new RegraDeNegocioException("o vinculo profissional e obrigatorio");
        }
        Profissional profissional = profissionalRepository.findById(profissionalId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("profissional nao encontrado"));
        if (!profissional.getUnidade().getId().equals(unidadeId)) {
            throw new AccessDeniedException("profissional fora da unidade autorizada");
        }
        boolean vinculado = usuarioIgnorado == null
                ? usuarioRepository.existsByProfissionalId(profissionalId)
                : usuarioRepository.existsByProfissionalIdAndIdNot(profissionalId, usuarioIgnorado);
        if (vinculado) {
            throw new ConflitoException("profissional ja possui uma conta");
        }
        return profissional;
    }
}
