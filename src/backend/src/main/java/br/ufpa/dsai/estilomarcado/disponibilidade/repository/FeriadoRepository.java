package br.ufpa.dsai.estilomarcado.disponibilidade.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.ufpa.dsai.estilomarcado.disponibilidade.model.Feriado;

public interface FeriadoRepository extends JpaRepository<Feriado, Long> {

    List<Feriado> findByUnidadeIdAndDataBetweenOrderByDataAsc(Long unidadeId, LocalDate inicio, LocalDate fim);

    boolean existsByUnidadeIdAndData(Long unidadeId, LocalDate data);

    boolean existsByUnidadeIdAndDataAndIdNot(Long unidadeId, LocalDate data, Long id);
}
