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
import br.ufpa.dsai.estilomarcado.catalogo.model.Unidade;

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
            and (:unidadeId is null or a.profissional.unidade.id = :unidadeId)
            and (:servicoId is null or a.servico.id = :servicoId)
            order by a.inicio, a.id
            """)
    List<Atendimento> listarCliente(@Param("clienteId") Long clienteId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim,
            @Param("status") AtendimentoStatus status,
            @Param("unidadeId") Long unidadeId, @Param("servicoId") Long servicoId,
            Pageable pageable);

    /**
     * Todos os atendimentos de um cliente, com filial, profissional e servico ja
     * carregados, para compor o painel do cliente sem consultas por item.
     */
    @Query("""
            select a from Atendimento a
              join fetch a.profissional p
              join fetch p.unidade
              join fetch a.servico
              join fetch a.cliente
             where a.cliente.id = :clienteId
            """)
    List<Atendimento> buscarDoCliente(@Param("clienteId") Long clienteId);

    /**
     * Filiais distintas em que o cliente autenticado ja teve algum atendimento.
     * Alimenta o seletor de filial da lista de espera, sem expor filiais com que
     * o cliente nao tem relacao.
     */
    @Query("""
            select distinct a.profissional.unidade from Atendimento a
             where a.cliente.usuario.id = :usuarioId
            """)
    List<Unidade> filiaisDoCliente(@Param("usuarioId") Long usuarioId);

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
     * Atendimentos de um profissional dentro de um intervalo, em ordem crescente
     * de horario e de identificador para desempate. Alimenta a agenda em
     * calendario (dia, semana e mes).
     */
    List<Atendimento> findByProfissionalIdAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioAscIdAsc(
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
