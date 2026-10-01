package br.ufpa.dsai.estilomarcado.catalogo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import br.ufpa.dsai.estilomarcado.catalogo.model.Profissional;

public interface ProfissionalRepository extends JpaRepository<Profissional, Long> {
    List<Profissional> findByUnidadeIdAndAtivoTrueOrderByNomeAsc(Long unidadeId);
    List<Profissional> findByUnidadeIdOrderByNomeAsc(Long unidadeId);
    List<Profissional> findByUnidadeIdOrderByIdAsc(Long unidadeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Profissional p where p.id = :id")
    Optional<Profissional> bloquear(@Param("id") Long id);
}
