package br.ufpa.dsai.estilomarcado.catalogo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.ufpa.dsai.estilomarcado.catalogo.model.Servico;

public interface ServicoRepository extends JpaRepository<Servico, Long> {

    List<Servico> findByUnidadeIdOrderByNomeAsc(Long unidadeId);

    boolean existsByUnidadeIdAndNome(Long unidadeId, String nome);

    boolean existsByUnidadeIdAndNomeAndIdNot(Long unidadeId, String nome, Long id);

    /**
     * Servicos ativos de uma unidade que possuem ao menos um profissional
     * habilitado. Servicos inativos ou sem profissionais nao aparecem: essa e a
     * visao "disponivel para agendamento" do catalogo.
     */
    @Query("""
            select s
              from Servico s
             where s.unidade.id = :unidadeId
               and s.ativo = true
               and size(s.profissionais) > 0
            """)
    List<Servico> findDisponiveisPorUnidade(@Param("unidadeId") Long unidadeId);
}
