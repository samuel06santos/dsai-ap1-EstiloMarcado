package br.ufpa.dsai.estilomarcado.catalogo.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.ufpa.dsai.estilomarcado.catalogo.model.Profissional;

public interface ProfissionalRepository extends JpaRepository<Profissional, Long> {
}
