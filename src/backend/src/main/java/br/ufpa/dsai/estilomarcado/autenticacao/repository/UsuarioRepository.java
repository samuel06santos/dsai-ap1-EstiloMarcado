package br.ufpa.dsai.estilomarcado.autenticacao.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.ufpa.dsai.estilomarcado.autenticacao.model.EstadoConta;
import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;
import br.ufpa.dsai.estilomarcado.autenticacao.model.Usuario;
import jakarta.persistence.LockModeType;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.id = :id")
    Optional<Usuario> bloquearPorId(@Param("id") Long id);

    @Query("select u from Usuario u left join fetch u.unidade left join fetch u.profissional where u.id = :id")
    Optional<Usuario> findForSessionById(@Param("id") Long id);

    Optional<Usuario> findByEmailNormalizado(String emailNormalizado);
    Optional<Usuario> findByFirebaseUid(String firebaseUid);
    boolean existsByFirebaseUid(String firebaseUid);
    long countByFirebaseUidIsNull();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Usuario> findWithLockByEmailNormalizado(String emailNormalizado);

    Optional<Usuario> findByIdAndUnidadeId(Long id, Long unidadeId);

    List<Usuario> findByUnidadeIdOrderByNomeAsc(Long unidadeId);

    boolean existsByEmailNormalizado(String emailNormalizado);

    boolean existsByProfissionalId(Long profissionalId);

    Optional<Usuario> findByProfissionalId(Long profissionalId);

    boolean existsByProfissionalIdAndIdNot(Long profissionalId, Long id);

    boolean existsByUnidadeIdAndPerfil(Long unidadeId, PerfilUsuario perfil);

    long countByUnidadeIdAndPerfilAndEstado(Long unidadeId, PerfilUsuario perfil, EstadoConta estado);
}
