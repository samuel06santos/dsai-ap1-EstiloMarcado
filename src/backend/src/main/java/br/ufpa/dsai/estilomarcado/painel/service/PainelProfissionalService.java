package br.ufpa.dsai.estilomarcado.painel.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.ufpa.dsai.estilomarcado.agendamento.repository.AtendimentoRepository;
import br.ufpa.dsai.estilomarcado.painel.api.dto.AgendaAtendimentoResponse;
import br.ufpa.dsai.estilomarcado.painel.api.exception.PerfilNaoIdentificadoException;

@Service
public class PainelProfissionalService {

    private final AtendimentoRepository atendimentoRepository;

    public PainelProfissionalService(AtendimentoRepository atendimentoRepository) {
        this.atendimentoRepository = atendimentoRepository;
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
