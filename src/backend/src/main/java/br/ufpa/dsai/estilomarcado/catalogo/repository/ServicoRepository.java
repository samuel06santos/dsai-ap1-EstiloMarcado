package br.ufpa.dsai.estilomarcado.catalogo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.ufpa.dsai.estilomarcado.catalogo.model.Servico;

public interface ServicoRepository extends JpaRepository<Servico, Long> {

    List<Servico> findByUnidadeIdOrderByNomeAscIdAsc(Long unidadeId);

    @Query("""
            select count(s) > 0 from Servico s
            where s.unidade.id = :unidadeId and lower(trim(s.nome)) = lower(:nome)
              and (:idIgnorado is null or s.id <> :idIgnorado)
            """)
    boolean existeNomeEquivalente(@Param("unidadeId") Long unidadeId,
                                  @Param("nome") String nome,
                                  @Param("idIgnorado") Long idIgnorado);

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
               and s.unidade.ativa = true
               and exists (select p.id from s.profissionais p where p.ativo = true)
             order by s.nome, s.id
            """)
    List<Servico> findDisponiveisPorUnidade(@Param("unidadeId") Long unidadeId);
}
