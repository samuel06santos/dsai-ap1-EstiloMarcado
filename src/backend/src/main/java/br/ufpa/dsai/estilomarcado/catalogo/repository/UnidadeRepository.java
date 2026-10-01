package br.ufpa.dsai.estilomarcado.catalogo.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.ufpa.dsai.estilomarcado.catalogo.model.Unidade;

public interface UnidadeRepository extends JpaRepository<Unidade, Long> {
    Optional<Unidade> findByNome(String nome);
    List<Unidade> findByEstabelecimentoIdOrderByNomeAsc(Long estabelecimentoId);
    List<Unidade> findByNomeAndPrincipalTrue(String nome);
    boolean existsByEstabelecimentoIdAndNomeNormalizado(Long estabelecimentoId, String nomeNormalizado);
    boolean existsByEstabelecimentoIdAndNomeNormalizadoAndIdNot(
            Long estabelecimentoId, String nomeNormalizado, Long id);
    boolean existsByEstabelecimentoIdAndPrincipalFalseAndAtivaTrue(Long estabelecimentoId);
}
