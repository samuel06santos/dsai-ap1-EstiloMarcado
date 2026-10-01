package br.ufpa.dsai.estilomarcado.autenticacao.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.ufpa.dsai.estilomarcado.autenticacao.model.FinalidadeToken;
import br.ufpa.dsai.estilomarcado.autenticacao.model.TokenUsuario;

public interface TokenUsuarioRepository extends JpaRepository<TokenUsuario, Long> {
    Optional<TokenUsuario> findByTokenHashAndFinalidade(String tokenHash, FinalidadeToken finalidade);
    List<TokenUsuario> findByUsuarioIdAndFinalidadeAndConsumidoEmIsNull(Long usuarioId, FinalidadeToken finalidade);
}
