package br.ufpa.dsai.estilomarcado.autenticacao.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import br.ufpa.dsai.estilomarcado.autenticacao.model.EstadoConta;
import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;
import br.ufpa.dsai.estilomarcado.autenticacao.model.Usuario;
import jakarta.persistence.LockModeType;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailNormalizado(String emailNormalizado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Usuario> findWithLockByEmailNormalizado(String emailNormalizado);

    Optional<Usuario> findByIdAndUnidadeId(Long id, Long unidadeId);

    List<Usuario> findByUnidadeIdOrderByNomeAsc(Long unidadeId);

    boolean existsByEmailNormalizado(String emailNormalizado);

    boolean existsByProfissionalId(Long profissionalId);

    boolean existsByProfissionalIdAndIdNot(Long profissionalId, Long id);

    boolean existsByUnidadeIdAndPerfil(Long unidadeId, PerfilUsuario perfil);

    long countByUnidadeIdAndPerfilAndEstado(Long unidadeId, PerfilUsuario perfil, EstadoConta estado);
}
