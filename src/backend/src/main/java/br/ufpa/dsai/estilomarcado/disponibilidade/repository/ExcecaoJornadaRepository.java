package br.ufpa.dsai.estilomarcado.disponibilidade.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.ufpa.dsai.estilomarcado.disponibilidade.model.ExcecaoJornada;

public interface ExcecaoJornadaRepository extends JpaRepository<ExcecaoJornada, Long> {

    Optional<ExcecaoJornada> findByProfissionalIdAndData(Long profissionalId, LocalDate data);

    List<ExcecaoJornada> findByProfissionalIdAndDataBetweenOrderByDataAsc(
            Long profissionalId, LocalDate inicio, LocalDate fim);

    boolean existsByProfissionalIdAndData(Long profissionalId, LocalDate data);

    boolean existsByProfissionalIdAndDataAndIdNot(Long profissionalId, LocalDate data, Long id);
}
