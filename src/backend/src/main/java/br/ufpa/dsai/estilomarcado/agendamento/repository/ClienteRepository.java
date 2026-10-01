package br.ufpa.dsai.estilomarcado.agendamento.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

import br.ufpa.dsai.estilomarcado.agendamento.model.Cliente;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    Optional<Cliente> findByUsuarioId(Long usuarioId);
}
