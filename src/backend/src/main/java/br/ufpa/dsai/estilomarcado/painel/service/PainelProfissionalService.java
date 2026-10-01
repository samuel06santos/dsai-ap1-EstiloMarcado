package br.ufpa.dsai.estilomarcado.painel.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

import br.ufpa.dsai.estilomarcado.agendamento.repository.AtendimentoRepository;
import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;
import br.ufpa.dsai.estilomarcado.autenticacao.security.UsuarioAtual;
import br.ufpa.dsai.estilomarcado.catalogo.model.Profissional;
import br.ufpa.dsai.estilomarcado.catalogo.repository.ProfissionalRepository;
import br.ufpa.dsai.estilomarcado.painel.api.dto.AgendaAtendimentoResponse;
import br.ufpa.dsai.estilomarcado.painel.api.exception.PerfilNaoIdentificadoException;

@Service
public class PainelProfissionalService {

    private final AtendimentoRepository atendimentoRepository;
    private final ProfissionalRepository profissionalRepository;
    private final UsuarioAtual usuarioAtual;

    public PainelProfissionalService(AtendimentoRepository atendimentoRepository,
                                    ProfissionalRepository profissionalRepository,
                                    UsuarioAtual usuarioAtual) {
        this.atendimentoRepository = atendimentoRepository;
        this.profissionalRepository = profissionalRepository;
        this.usuarioAtual = usuarioAtual;
    }

    @Transactional(readOnly = true)
    public List<AgendaAtendimentoResponse> agendaAutenticada(LocalDate data) {
        return agendaDoDia(profissionalDaSessao().getId(), data);
    }

    @Transactional(readOnly = true)
    public List<AgendaAtendimentoResponse> agendaAutenticada(LocalDate de, LocalDate ate) {
        if (de == null || ate == null || de.isAfter(ate) || ate.isAfter(de.plusDays(41))) {
            throw new IllegalArgumentException("intervalo de agenda invalido (maximo de 42 dias)");
        }
        return agendaDoIntervalo(profissionalDaSessao().getId(), de, ate);
    }

    private Profissional profissionalDaSessao() {
        var principal = usuarioAtual.get();
        if (principal.perfil() != PerfilUsuario.PROFISSIONAL || principal.profissionalId() == null
                || principal.unidadeId() == null) {
            throw new AccessDeniedException("acesso negado");
        }
        var profissional = profissionalRepository.findById(principal.profissionalId())
                .orElseThrow(() -> new AccessDeniedException("acesso negado"));
        if (!profissional.isAtivo() || !profissional.getUnidade().isAtiva()
                || !principal.unidadeId().equals(profissional.getUnidade().getId())) {
            throw new AccessDeniedException("acesso negado");
        }
        return profissional;
    }

    /**
     * Agenda de um dia do profissional identificado: somente os atendimentos
     * atribuidos a ele, ordenados por horario crescente.
     */
    @Transactional(readOnly = true)
    public List<AgendaAtendimentoResponse> agendaDoDia(Long profissionalId, LocalDate data) {
        return agendaDoIntervalo(profissionalId, data, data);
    }

    /**
     * Agenda de um intervalo fechado de datas do profissional identificado, em
     * ordem crescente de horario e identificador.
     */
    @Transactional(readOnly = true)
    public List<AgendaAtendimentoResponse> agendaDoIntervalo(Long profissionalId, LocalDate de, LocalDate ate) {
        if (profissionalId == null) {
            throw new PerfilNaoIdentificadoException("perfil profissional nao identificado");
        }
        return atendimentoRepository
                .findByProfissionalIdAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioAscIdAsc(
                        profissionalId, de.atStartOfDay(), ate.plusDays(1).atStartOfDay())
                .stream()
                .map(AgendaAtendimentoResponse::from)
                .toList();
    }
}
