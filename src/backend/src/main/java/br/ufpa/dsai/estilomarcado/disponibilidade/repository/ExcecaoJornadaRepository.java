package br.ufpa.dsai.estilomarcado.disponibilidade.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.ufpa.dsai.estilomarcado.disponibilidade.model.ExcecaoJornada;

public interface ExcecaoJornadaRepository extends JpaRepository<ExcecaoJornada, Long> {

    Optional<ExcecaoJornada> findByProfissionalIdAndData(Long profissionalId, LocalDate data);

    @Query("select distinct e from ExcecaoJornada e left join fetch e.intervalos "
            + "where e.profissional.id in :profissionalIds and e.data = :data")
    List<ExcecaoJornada> findDoDiaComIntervalos(@Param("profissionalIds") List<Long> profissionalIds,
                                                @Param("data") LocalDate data);

    List<ExcecaoJornada> findByProfissionalIdAndDataBetweenOrderByDataAsc(
            Long profissionalId, LocalDate inicio, LocalDate fim);

    boolean existsByProfissionalIdAndData(Long profissionalId, LocalDate data);

    boolean existsByProfissionalIdAndDataAndIdNot(Long profissionalId, LocalDate data, Long id);
}
