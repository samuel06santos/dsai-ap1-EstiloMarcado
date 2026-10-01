package br.ufpa.dsai.estilomarcado.catalogo.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.ufpa.dsai.estilomarcado.catalogo.api.dto.ServicoRequest;
import br.ufpa.dsai.estilomarcado.catalogo.api.dto.ServicoResponse;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.ConflitoException;
import org.springframework.security.access.AccessDeniedException;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.RecursoNaoEncontradoException;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.RegraDeNegocioException;
import br.ufpa.dsai.estilomarcado.catalogo.model.Profissional;
import br.ufpa.dsai.estilomarcado.catalogo.model.Servico;
import br.ufpa.dsai.estilomarcado.catalogo.model.Unidade;
import br.ufpa.dsai.estilomarcado.catalogo.repository.ProfissionalRepository;
import br.ufpa.dsai.estilomarcado.catalogo.repository.ServicoRepository;
import br.ufpa.dsai.estilomarcado.catalogo.repository.UnidadeRepository;

@Service
public class ServicoService {

    private final ServicoRepository servicoRepository;
    private final UnidadeRepository unidadeRepository;
    private final ProfissionalRepository profissionalRepository;

    public ServicoService(ServicoRepository servicoRepository,
                          UnidadeRepository unidadeRepository,
                          ProfissionalRepository profissionalRepository) {
        this.servicoRepository = servicoRepository;
        this.unidadeRepository = unidadeRepository;
        this.profissionalRepository = profissionalRepository;
    }

    @Transactional
    public ServicoResponse criar(Long unidadeId, ServicoRequest request) {
        Unidade unidade = unidadeRepository.findById(unidadeId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("unidade " + unidadeId + " nao encontrada"));
        if (!unidade.isAtiva()) { throw new AccessDeniedException("filial inativa"); }

        String nome = normalizarNome(request.getNome());
        garantirNomeUnico(unidadeId, nome, null);

        Servico servico = new Servico(unidade, nome, request.getDuracaoMinutos(), request.getPreco());
        travarProfissionais(request.getProfissionalIds());
        aplicar(servico, request);
        servico.setAtivo(true);
        servico.substituirProfissionais(resolverProfissionais(unidadeId, request.getProfissionalIds()));

        return ServicoResponse.from(servicoRepository.save(servico));
    }

    @Transactional
    public ServicoResponse atualizar(Long id, ServicoRequest request) {
        Servico servico = buscarEntidade(id);
        travarUnidade(servico.getUnidade().getId());
        exigirUnidadeAtiva(servico);
        String nome = normalizarNome(request.getNome());
        garantirNomeUnico(servico.getUnidade().getId(), nome, id);

        servico.setNome(nome);
        aplicar(servico, request);
        servico.substituirProfissionais(resolverProfissionais(servico.getUnidade().getId(), request.getProfissionalIds()));

        return ServicoResponse.from(servicoRepository.save(servico));
    }

    @Transactional
    public ServicoResponse ativar(Long id) {
        Servico servico = buscarEntidade(id);
        travarUnidade(servico.getUnidade().getId());
        exigirUnidadeAtiva(servico);
        servico.setAtivo(true);
        return ServicoResponse.from(servicoRepository.save(servico));
    }

    @Transactional
    public ServicoResponse desativar(Long id) {
        Servico servico = buscarEntidade(id);
        travarUnidade(servico.getUnidade().getId());
        exigirUnidadeAtiva(servico);
        servico.setAtivo(false);
        return ServicoResponse.from(servicoRepository.save(servico));
    }

    @Transactional(readOnly = true)
    public ServicoResponse buscar(Long id) {
        Servico servico = buscarEntidade(id);
        return ServicoResponse.from(servico);
    }

    @Transactional(readOnly = true)
    public ServicoResponse buscarPublico(Long id) {
        Servico servico = buscarEntidade(id);
        if (!agendavel(servico)) {
            throw new RecursoNaoEncontradoException("servico nao encontrado");
        }
        return ServicoResponse.fromPublico(servico);
    }

    @Transactional(readOnly = true)
    public List<ServicoResponse> listar(Long unidadeId) {
        unidadeRepository.findById(unidadeId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("filial nao encontrada"));
        return servicoRepository.findByUnidadeIdOrderByNomeAscIdAsc(unidadeId).stream()
                .map(ServicoResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServicoResponse> listarDisponiveis(Long unidadeId) {
        exigirUnidadePublica(unidadeRepository.findById(unidadeId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("filial nao encontrada")));
        return servicoRepository.findDisponiveisPorUnidade(unidadeId).stream()
                .map(ServicoResponse::fromPublico)
                .toList();
    }

    private Servico buscarEntidade(Long id) {
        return servicoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("servico " + id + " nao encontrado"));
    }

    private void exigirUnidadeAtiva(Servico servico) {
        if (!servico.getUnidade().isAtiva()) {
            throw new AccessDeniedException("filial inativa");
        }
    }

    private void exigirUnidadePublica(Unidade unidade) {
        if (!unidade.isAtiva()) {
            throw new RecursoNaoEncontradoException("filial nao encontrada");
        }
    }

    private void aplicar(Servico servico, ServicoRequest request) {
        servico.setDescricao(request.getDescricao());
        servico.setDuracaoMinutos(request.getDuracaoMinutos());
        servico.setPreco(request.getPreco());
        servico.setIntervaloMinutos(request.getIntervaloMinutos());
    }

    private void garantirNomeUnico(Long unidadeId, String nome, Long idIgnorado) {
        if (servicoRepository.existeNomeEquivalente(unidadeId, nome, idIgnorado)) {
            throw new ConflitoException("ja existe um servico com o nome '" + nome + "' nesta unidade");
        }
    }

    private String normalizarNome(String nome) {
        if (nome == null || nome.trim().length() < 2 || nome.trim().length() > 120) {
            throw new IllegalArgumentException("nome deve ter entre 2 e 120 caracteres");
        }
        return nome.trim();
    }

    private boolean agendavel(Servico servico) {
        return servico.isAtivo() && servico.getUnidade().isAtiva()
                && servico.getProfissionais().stream().anyMatch(Profissional::isAtivo);
    }

    private Set<Profissional> resolverProfissionais(Long unidadeId, Set<Long> profissionalIds) {
        if (profissionalIds == null || profissionalIds.isEmpty()) {
            return new LinkedHashSet<>();
        }

        List<Profissional> profissionais = profissionalRepository.findAllById(profissionalIds);
        if (profissionais.size() != profissionalIds.size()) {
            throw new RecursoNaoEncontradoException("um ou mais profissionais informados nao existem");
        }

        boolean mesmaUnidade = profissionais.stream()
                .allMatch(profissional -> profissional.getUnidade().getId().equals(unidadeId));
        if (!mesmaUnidade) {
            throw new RegraDeNegocioException(
                    "todos os profissionais habilitados devem pertencer a mesma unidade do servico");
        }

        return new LinkedHashSet<>(profissionais);
    }

    private void travarProfissionais(Set<Long> ids) {
        if (ids == null) return;
        ids.stream().sorted().forEach(id -> profissionalRepository.bloquear(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("profissional nao encontrado")));
    }

    private void travarUnidade(Long unidadeId) {
        for (Profissional profissional : profissionalRepository.findByUnidadeIdOrderByIdAsc(unidadeId)) {
            profissionalRepository.bloquear(profissional.getId());
        }
    }
}
