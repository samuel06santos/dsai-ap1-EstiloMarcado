package br.ufpa.dsai.estilomarcado.disponibilidade.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.ufpa.dsai.estilomarcado.disponibilidade.model.JornadaIntervalo;

public interface JornadaIntervaloRepository extends JpaRepository<JornadaIntervalo, Long> {

    List<JornadaIntervalo> findByProfissionalIdOrderByDiaSemanaAscHoraInicioAsc(Long profissionalId);
    List<JornadaIntervalo> findByProfissionalIdInAndDiaSemanaOrderByHoraInicioAsc(
            List<Long> profissionalIds, int diaSemana);

    void deleteByProfissionalId(Long profissionalId);
}
