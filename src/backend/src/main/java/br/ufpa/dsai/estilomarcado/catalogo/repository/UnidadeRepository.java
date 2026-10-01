package br.ufpa.dsai.estilomarcado.catalogo.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.ufpa.dsai.estilomarcado.catalogo.model.Unidade;

public interface UnidadeRepository extends JpaRepository<Unidade, Long> {
    Optional<Unidade> findByNome(String nome);
}
