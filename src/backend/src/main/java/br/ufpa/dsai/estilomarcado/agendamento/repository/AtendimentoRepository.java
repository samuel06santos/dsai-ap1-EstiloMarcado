package br.ufpa.dsai.estilomarcado.agendamento.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.ufpa.dsai.estilomarcado.agendamento.model.Atendimento;

public interface AtendimentoRepository extends JpaRepository<Atendimento, Long> {

    /**
     * Atendimentos de um profissional dentro de um intervalo, em ordem crescente
     * de horario. O intervalo e semiaberto: inclui o inicio e exclui o fim.
     */
    List<Atendimento> findByProfissionalIdAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioAsc(
            Long profissionalId, LocalDateTime inicio, LocalDateTime fim);

    /**
     * Atendimentos de um profissional a partir de um instante, em ordem
     * crescente. Usado para verificar conflitos com atendimentos futuros.
     */
    List<Atendimento> findByProfissionalIdAndInicioGreaterThanEqualOrderByInicioAsc(
            Long profissionalId, LocalDateTime inicio);

    /**
     * Atendimentos de todos os profissionais de uma filial dentro de um
     * intervalo, em ordem crescente de horario.
     */
    List<Atendimento> findByProfissionalUnidadeIdAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioAsc(
            Long unidadeId, LocalDateTime inicio, LocalDateTime fim);
}
