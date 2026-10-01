package br.ufpa.dsai.estilomarcado.disponibilidade.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.ufpa.dsai.estilomarcado.disponibilidade.model.Afastamento;

public interface AfastamentoRepository extends JpaRepository<Afastamento, Long> {

    List<Afastamento> findByProfissionalIdAndDataFimGreaterThanEqualAndDataInicioLessThanEqualOrderByDataInicioAsc(
            Long profissionalId, LocalDate inicio, LocalDate fim);

    List<Afastamento> findByProfissionalIdInAndDataInicioLessThanEqualAndDataFimGreaterThanEqual(
            List<Long> profissionalIds, LocalDate dataFim, LocalDate dataInicio);

    boolean existsByProfissionalIdAndDataInicioLessThanEqualAndDataFimGreaterThanEqual(
            Long profissionalId, LocalDate dataFim, LocalDate dataInicio);

    boolean existsByProfissionalIdAndDataInicioLessThanEqualAndDataFimGreaterThanEqualAndIdNot(
            Long profissionalId, LocalDate dataFim, LocalDate dataInicio, Long id);
}
