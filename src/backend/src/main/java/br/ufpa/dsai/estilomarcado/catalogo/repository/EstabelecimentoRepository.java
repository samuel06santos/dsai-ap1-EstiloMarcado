package br.ufpa.dsai.estilomarcado.catalogo.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.ufpa.dsai.estilomarcado.catalogo.model.Estabelecimento;

public interface EstabelecimentoRepository extends JpaRepository<Estabelecimento, Long> {}
