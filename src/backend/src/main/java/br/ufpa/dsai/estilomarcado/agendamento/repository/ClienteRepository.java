package br.ufpa.dsai.estilomarcado.agendamento.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.ufpa.dsai.estilomarcado.agendamento.model.Cliente;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    Optional<Cliente> findByUsuarioId(Long usuarioId);

    /**
     * Clientes que ja possuem ao menos um atendimento na filial, em ordem
     * alfabetica. Alimenta o seletor de "cliente ja atendido" da recepcao, sem
     * expor clientes de outra filial.
     */
    @Query("""
            select distinct c from Cliente c
             where exists (select 1 from Atendimento a
                            where a.cliente = c and a.profissional.unidade.id = :unidadeId)
             order by c.nome
            """)
    List<Cliente> buscarDaUnidade(@Param("unidadeId") Long unidadeId);
}
