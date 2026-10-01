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
        return agendaDoDia(profissional.getId(), data);
    }

    /**
     * Agenda de um dia do profissional identificado: somente os atendimentos
     * atribuidos a ele, ordenados por horario crescente.
     */
    @Transactional(readOnly = true)
    public List<AgendaAtendimentoResponse> agendaDoDia(Long profissionalId, LocalDate data) {
        if (profissionalId == null) {
            throw new PerfilNaoIdentificadoException("perfil profissional nao identificado");
        }

        LocalDateTime inicioDoDia = data.atStartOfDay();
        LocalDateTime inicioDoDiaSeguinte = data.plusDays(1).atStartOfDay();

        return atendimentoRepository
                .findByProfissionalIdAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioAsc(
                        profissionalId, inicioDoDia, inicioDoDiaSeguinte)
                .stream()
                .map(AgendaAtendimentoResponse::from)
                .toList();
    }
}
