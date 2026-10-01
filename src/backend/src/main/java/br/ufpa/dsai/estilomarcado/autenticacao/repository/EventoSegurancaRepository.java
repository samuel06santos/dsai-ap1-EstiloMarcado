package br.ufpa.dsai.estilomarcado.autenticacao.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.ufpa.dsai.estilomarcado.autenticacao.model.EventoSeguranca;

public interface EventoSegurancaRepository extends JpaRepository<EventoSeguranca, Long> {
}
