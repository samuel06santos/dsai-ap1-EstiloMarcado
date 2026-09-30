package br.ufpa.dsai.estilomarcado.catalogo.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.ufpa.dsai.estilomarcado.catalogo.api.dto.ServicoRequest;
import br.ufpa.dsai.estilomarcado.catalogo.api.dto.ServicoResponse;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.ConflitoException;
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

        garantirNomeUnico(unidadeId, request.getNome(), null);

        Servico servico = new Servico(unidade, request.getNome(), request.getDuracaoMinutos(), request.getPreco());
        aplicar(servico, request);
        servico.setAtivo(true);
        servico.substituirProfissionais(resolverProfissionais(unidadeId, request.getProfissionalIds()));

        return ServicoResponse.from(servicoRepository.save(servico));
    }

    @Transactional
    public ServicoResponse atualizar(Long id, ServicoRequest request) {
        Servico servico = buscarEntidade(id);
        garantirNomeUnico(servico.getUnidade().getId(), request.getNome(), id);

        servico.setNome(request.getNome());
        aplicar(servico, request);
        servico.substituirProfissionais(resolverProfissionais(servico.getUnidade().getId(), request.getProfissionalIds()));

        return ServicoResponse.from(servicoRepository.save(servico));
    }

    @Transactional
    public ServicoResponse ativar(Long id) {
        Servico servico = buscarEntidade(id);
        servico.setAtivo(true);
        return ServicoResponse.from(servicoRepository.save(servico));
    }

    @Transactional
    public ServicoResponse desativar(Long id) {
        Servico servico = buscarEntidade(id);
        servico.setAtivo(false);
        return ServicoResponse.from(servicoRepository.save(servico));
    }

    @Transactional(readOnly = true)
    public ServicoResponse buscar(Long id) {
        return ServicoResponse.from(buscarEntidade(id));
    }

    @Transactional(readOnly = true)
    public List<ServicoResponse> listar(Long unidadeId) {
        return servicoRepository.findByUnidadeIdOrderByNomeAsc(unidadeId).stream()
                .map(ServicoResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServicoResponse> listarDisponiveis(Long unidadeId) {
        return servicoRepository.findDisponiveisPorUnidade(unidadeId).stream()
                .map(ServicoResponse::from)
                .toList();
    }

    private Servico buscarEntidade(Long id) {
        return servicoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("servico " + id + " nao encontrado"));
    }

    private void aplicar(Servico servico, ServicoRequest request) {
        servico.setDescricao(request.getDescricao());
        servico.setDuracaoMinutos(request.getDuracaoMinutos());
        servico.setPreco(request.getPreco());
        servico.setIntervaloMinutos(request.getIntervaloMinutos());
    }

    private void garantirNomeUnico(Long unidadeId, String nome, Long idIgnorado) {
        boolean existe;
        if (idIgnorado == null) {
            existe = servicoRepository.existsByUnidadeIdAndNome(unidadeId, nome);
        } else {
            existe = servicoRepository.existsByUnidadeIdAndNomeAndIdNot(unidadeId, nome, idIgnorado);
        }
        if (existe) {
            throw new ConflitoException("ja existe um servico com o nome '" + nome + "' nesta unidade");
        }
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
}
