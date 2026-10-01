package br.ufpa.dsai.estilomarcado.estabelecimento.service;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;
import br.ufpa.dsai.estilomarcado.autenticacao.repository.UsuarioRepository;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioAtual;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioPrincipal;
import br.ufpa.dsai.estilomarcado.autenticacao.service.AuditoriaService;
import br.ufpa.dsai.estilomarcado.autenticacao.service.SessaoService;
import br.ufpa.dsai.estilomarcado.autenticacao.service.UsuarioInternoService;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.ConflitoException;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.RecursoNaoEncontradoException;
import br.ufpa.dsai.estilomarcado.catalogo.model.Estabelecimento;
import br.ufpa.dsai.estilomarcado.catalogo.model.Profissional;
import br.ufpa.dsai.estilomarcado.catalogo.model.Unidade;
import br.ufpa.dsai.estilomarcado.catalogo.repository.EstabelecimentoRepository;
import br.ufpa.dsai.estilomarcado.catalogo.repository.ProfissionalRepository;
import br.ufpa.dsai.estilomarcado.catalogo.repository.UnidadeRepository;
import br.ufpa.dsai.estilomarcado.estabelecimento.api.dto.CriarFilialRequest;
import br.ufpa.dsai.estilomarcado.estabelecimento.api.dto.EstabelecimentoRequest;
import br.ufpa.dsai.estilomarcado.estabelecimento.api.dto.EstabelecimentoResponse;
import br.ufpa.dsai.estilomarcado.estabelecimento.api.dto.FilialRequest;
import br.ufpa.dsai.estilomarcado.estabelecimento.api.dto.FilialResponse;
import br.ufpa.dsai.estilomarcado.estabelecimento.api.dto.ProfissionalRequest;
import br.ufpa.dsai.estilomarcado.estabelecimento.api.dto.ProfissionalResponse;

@Service
public class EstabelecimentoService {
    private final EstabelecimentoRepository estabelecimentos;
    private final UnidadeRepository unidades;
    private final ProfissionalRepository profissionais;
    private final UsuarioRepository usuarios;
    private final UsuarioInternoService contas;
    private final UsuarioAtual usuarioAtual;
    private final SessaoService sessoes;
    private final AuditoriaService auditoria;

    public EstabelecimentoService(EstabelecimentoRepository estabelecimentos,
                                 UnidadeRepository unidades, ProfissionalRepository profissionais,
                                 UsuarioRepository usuarios, UsuarioInternoService contas,
                                 UsuarioAtual usuarioAtual, SessaoService sessoes,
                                 AuditoriaService auditoria) {
        this.estabelecimentos = estabelecimentos;
        this.unidades = unidades;
        this.profissionais = profissionais;
        this.usuarios = usuarios;
        this.contas = contas;
        this.usuarioAtual = usuarioAtual;
        this.sessoes = sessoes;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public EstabelecimentoResponse consultarEstabelecimento(Long id) {
        UsuarioPrincipal autor = usuarioAtual.get();
        if (autor.perfil() == PerfilUsuario.CLIENTE || autor.unidadeId() == null) {
            throw new AccessDeniedException("acesso negado");
        }
        Unidade propria = buscarUnidade(autor.unidadeId());
        if (!propria.getEstabelecimento().getId().equals(id)) {
            throw new RecursoNaoEncontradoException("estabelecimento nao encontrado");
        }
        boolean principal = propria.isPrincipal() && autor.perfil() == PerfilUsuario.ADMINISTRADOR;
        List<EstabelecimentoResponse.FilialResumo> filiais = principal
                ? unidades.findByEstabelecimentoIdOrderByNomeAsc(id).stream()
                    .map(u -> new EstabelecimentoResponse.FilialResumo(u.getId(), u.getNome(), u.isAtiva()))
                    .toList()
                : List.of();
        return EstabelecimentoResponse.from(propria.getEstabelecimento(), principal, filiais);
    }

    @Transactional
    public EstabelecimentoResponse atualizarEstabelecimento(Long id, EstabelecimentoRequest request,
                                                             String origem) {
        exigirAdministradorPrincipal(id);
        Estabelecimento estabelecimento = estabelecimentos.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("estabelecimento nao encontrado"));
        estabelecimento.setNome(request.nome());
        estabelecimentos.save(estabelecimento);
        auditar("ESTABELECIMENTO_ALTERADO", origem, "estabelecimento=" + id);
        return consultarEstabelecimento(id);
    }

    @Transactional
    public FilialResponse criarFilial(Long estabelecimentoId, CriarFilialRequest request, String origem) {
        exigirAdministradorPrincipal(estabelecimentoId);
        if (request.estabelecimentoId() != null || request.unidadeId() != null) {
            throw new IllegalArgumentException("vinculos da filial nao podem ser informados");
        }
        String nomeNormalizado = normalizar(request.nome());
        if (unidades.existsByEstabelecimentoIdAndNomeNormalizado(estabelecimentoId, nomeNormalizado)) {
            throw new ConflitoException("ja existe uma filial com este nome no estabelecimento");
        }
        Estabelecimento estabelecimento = estabelecimentos.findById(estabelecimentoId).orElseThrow();
        Unidade unidade = new Unidade(request.nome(), estabelecimento, false);
        unidade.setEndereco(opcional(request.endereco()));
        unidade.setTelefone(opcional(request.telefone()));
        unidade.setFusoHorario(validarFuso(request.fusoHorario()));
        unidades.saveAndFlush(unidade);
        contas.convidarPrimeiroAdministradorNovaFilial(unidade,
                request.primeiroAdministrador().nome(), request.primeiroAdministrador().email(), origem);
        auditar("FILIAL_CRIADA", origem, "unidade=" + unidade.getId());
        return FilialResponse.from(unidade);
    }

    @Transactional(readOnly = true)
    public FilialResponse consultarFilial(Long unidadeId) {
        return FilialResponse.from(exigirAdministradorDaFilial(unidadeId, false));
    }

    @Transactional(readOnly = true)
    public FilialResponse consultarMinhaFilial() {
        var usuario = usuarios.findById(usuarioAtual.get().id())
                .orElseThrow(() -> new AccessDeniedException("acesso negado"));
        if (usuario.getPerfil() == PerfilUsuario.CLIENTE || usuario.getUnidade() == null) {
            throw new AccessDeniedException("acesso negado");
        }
        return FilialResponse.from(usuario.getUnidade());
    }

    @Transactional
    public FilialResponse atualizarFilial(Long unidadeId, FilialRequest request, String origem) {
        Unidade unidade = exigirAdministradorDaFilial(unidadeId, false);
        if (request.estabelecimentoId() != null || request.unidadeId() != null) {
            throw new IllegalArgumentException("vinculos da filial nao podem ser alterados");
        }
        String nomeNormalizado = normalizar(request.nome());
        if (unidades.existsByEstabelecimentoIdAndNomeNormalizadoAndIdNot(
                unidade.getEstabelecimento().getId(), nomeNormalizado, unidadeId)) {
            throw new ConflitoException("ja existe uma filial com este nome no estabelecimento");
        }
        if (Boolean.FALSE.equals(request.ativa()) && unidade.isPrincipal()
                && unidades.existsByEstabelecimentoIdAndPrincipalFalseAndAtivaTrue(
                        unidade.getEstabelecimento().getId())) {
            throw new ConflitoException("a filial principal possui outras filiais ativas");
        }
        unidade.setNome(request.nome());
        unidade.setEndereco(opcional(request.endereco()));
        unidade.setTelefone(opcional(request.telefone()));
        unidade.setFusoHorario(validarFuso(request.fusoHorario()));
        if (request.ativa() != null) { unidade.setAtiva(request.ativa()); }
        unidades.save(unidade);
        auditar("FILIAL_ALTERADA", origem, "unidade=" + unidadeId);
        return FilialResponse.from(unidade);
    }

    @Transactional(readOnly = true)
    public FilialResponse filialPublica(Long unidadeId) {
        return FilialResponse.from(exigirFilialAtiva(unidadeId));
    }

    @Transactional(readOnly = true)
    public List<ProfissionalResponse> listarProfissionais(Long unidadeId, boolean incluirInativos) {
        if (incluirInativos) {
            exigirAdministradorDaFilial(unidadeId, false);
            return profissionais.findByUnidadeIdOrderByNomeAsc(unidadeId).stream()
                    .map(ProfissionalResponse::from).toList();
        }
        exigirFilialAtiva(unidadeId);
        return profissionais.findByUnidadeIdAndAtivoTrueOrderByNomeAsc(unidadeId).stream()
                .map(ProfissionalResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProfissionalResponse profissionalPublico(Long unidadeId, Long id) {
        exigirFilialAtiva(unidadeId);
        Profissional profissional = buscarProfissional(unidadeId, id);
        if (!profissional.isAtivo()) {
            throw new RecursoNaoEncontradoException("profissional nao encontrado");
        }
        return ProfissionalResponse.from(profissional);
    }

    @Transactional
    public ProfissionalResponse criarProfissional(Long unidadeId, ProfissionalRequest request, String origem) {
        Unidade unidade = exigirAdministradorDaFilial(unidadeId, true);
        if (request.unidadeId() != null || request.profissionalId() != null || request.ativo() != null) {
            throw new IllegalArgumentException("vinculos e estado inicial nao podem ser informados");
        }
        Profissional profissional = new Profissional(request.nome().trim(), unidade);
        profissional.setApresentacao(opcional(request.apresentacao()));
        profissionais.save(profissional);
        auditar("PROFISSIONAL_CRIADO", origem, "profissional=" + profissional.getId());
        return ProfissionalResponse.from(profissional);
    }

    @Transactional
    public ProfissionalResponse atualizarProfissional(Long unidadeId, Long id,
                                                       ProfissionalRequest request, String origem) {
        exigirAdministradorDaFilial(unidadeId, true);
        if (request.unidadeId() != null || request.profissionalId() != null) {
            throw new IllegalArgumentException("vinculo do profissional nao pode ser alterado");
        }
        Profissional profissional = buscarProfissional(unidadeId, id);
        profissional.setNome(request.nome().trim());
        profissional.setApresentacao(opcional(request.apresentacao()));
        if (request.ativo() != null && profissional.isAtivo() != request.ativo()) {
            profissional.setAtivo(request.ativo());
            if (!request.ativo()) {
                usuarios.findByProfissionalId(id)
                        .ifPresent(usuario -> sessoes.invalidarTodas(usuario.getEmailNormalizado()));
            }
        }
        profissionais.save(profissional);
        auditar("PROFISSIONAL_ALTERADO", origem, "profissional=" + id);
        return ProfissionalResponse.from(profissional);
    }

    private Unidade exigirAdministradorPrincipal(Long estabelecimentoId) {
        UsuarioPrincipal autor = usuarioAtual.get();
        if (autor.perfil() != PerfilUsuario.ADMINISTRADOR || autor.unidadeId() == null) {
            throw new AccessDeniedException("acesso negado");
        }
        Unidade propria = buscarUnidade(autor.unidadeId());
        if (!propria.isPrincipal() || !propria.isAtiva()
                || !propria.getEstabelecimento().getId().equals(estabelecimentoId)) {
            throw new AccessDeniedException("acesso negado");
        }
        return propria;
    }

    private Unidade exigirAdministradorDaFilial(Long unidadeId, boolean ativa) {
        usuarioAtual.exigirAdministradorDaUnidade(unidadeId);
        Unidade unidade = buscarUnidade(unidadeId);
        if (ativa && !unidade.isAtiva()) {
            throw new AccessDeniedException("filial inativa");
        }
        return unidade;
    }

    private Unidade exigirFilialAtiva(Long id) {
        Unidade unidade = buscarUnidade(id);
        if (!unidade.isAtiva()) {
            throw new RecursoNaoEncontradoException("filial nao encontrada");
        }
        return unidade;
    }

    private Unidade buscarUnidade(Long id) {
        return unidades.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("filial nao encontrada"));
    }

    private Profissional buscarProfissional(Long unidadeId, Long id) {
        Profissional profissional = profissionais.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("profissional nao encontrado"));
        if (!profissional.getUnidade().getId().equals(unidadeId)) {
            throw new RecursoNaoEncontradoException("profissional nao encontrado");
        }
        return profissional;
    }

    private String validarFuso(String fuso) {
        try {
            ZoneId.of(fuso);
            return fuso;
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("fuso horario invalido");
        }
    }

    private String normalizar(String nome) { return nome.trim().toLowerCase(Locale.ROOT); }
    private String opcional(String valor) { return valor == null || valor.isBlank() ? null : valor.trim(); }
    private void auditar(String tipo, String origem, String detalhes) {
        auditoria.registrar(usuarioAtual.get().id(), tipo, true, origem, detalhes);
    }
}
