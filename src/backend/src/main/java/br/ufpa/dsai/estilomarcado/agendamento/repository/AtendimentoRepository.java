package br.ufpa.dsai.estilomarcado.agendamento.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

import br.ufpa.dsai.estilomarcado.agendamento.model.Atendimento;
import br.ufpa.dsai.estilomarcado.agendamento.model.AtendimentoStatus;

public interface AtendimentoRepository extends JpaRepository<Atendimento, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Atendimento a where a.id = :id")
    Optional<Atendimento> bloquear(@Param("id") Long id);

    @Query(value = """
            select a.id from atendimento a where a.profissional_id in (:ids)
            and a.status in ('AGENDADO','CONFIRMADO')
            and tsrange(a.inicio, a.inicio + (a.duracao_minutos + a.intervalo_minutos) * interval '1 minute', '[)')
                && tsrange(:inicio, :fim, '[)')
            order by a.id
            """, nativeQuery = true)
    List<Long> buscarIdsOcupacao(@Param("ids") List<Long> ids,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    boolean existsByClienteIdAndProfissionalUnidadeId(Long clienteId, Long unidadeId);

    boolean existsByProfissionalUnidadeIdAndStatusInAndInicioGreaterThanEqual(
            Long unidadeId, List<AtendimentoStatus> statuses, LocalDateTime inicio);

    @Query("""
            select a from Atendimento a where a.cliente.id = :clienteId
            and a.inicio >= :inicio and a.inicio < :fim
            and (:status is null or a.status = :status)
            order by a.inicio, a.id
            """)
    List<Atendimento> listarCliente(@Param("clienteId") Long clienteId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim,
            @Param("status") AtendimentoStatus status, Pageable pageable);

    @Query("""
            select a from Atendimento a where a.profissional.unidade.id = :unidadeId
            and a.inicio >= :inicio and a.inicio < :fim
            and (:status is null or a.status = :status)
            and (:profissionalId is null or a.profissional.id = :profissionalId)
            order by a.inicio, a.id
            """)
    List<Atendimento> listarUnidade(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim,
            @Param("status") AtendimentoStatus status,
            @Param("profissionalId") Long profissionalId, Pageable pageable);

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

    List<Atendimento> findByClienteIdAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioAscIdAsc(
            Long clienteId, LocalDateTime inicio, LocalDateTime fim);

    @Query("""
            select a from Atendimento a
             where a.profissional.id in :profissionalIds
               and a.inicio >= :inicio and a.inicio < :fim
               and a.status in :statuses
             order by a.inicio asc
            """)
    List<Atendimento> buscarOcupacaoDoDia(@Param("profissionalIds") List<Long> profissionalIds,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim,
            @Param("statuses") List<AtendimentoStatus> statuses);
}
