package br.ufpa.dsai.estilomarcado.agendamento.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.ufpa.dsai.estilomarcado.agendamento.model.Cliente;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
}
