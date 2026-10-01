package br.ufpa.dsai.estilomarcado.disponibilidade.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.ufpa.dsai.estilomarcado.disponibilidade.model.BloqueioAgenda;

public interface BloqueioAgendaRepository extends JpaRepository<BloqueioAgenda, Long> {

    List<BloqueioAgenda> findByUnidadeIdAndDataBetweenOrderByDataAscHoraInicioAsc(
            Long unidadeId, LocalDate inicio, LocalDate fim);

    List<BloqueioAgenda> findByUnidadeIdAndDataAndProfissionalIsNull(Long unidadeId, LocalDate data);
    List<BloqueioAgenda> findByUnidadeIdAndData(Long unidadeId, LocalDate data);

    List<BloqueioAgenda> findByProfissionalIdAndData(Long profissionalId, LocalDate data);
}
