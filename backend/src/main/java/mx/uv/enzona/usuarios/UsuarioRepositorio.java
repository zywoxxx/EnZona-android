package mx.uv.enzona.usuarios;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepositorio extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByCorreoIgnoreCase(String correo);

    Optional<Usuario> findByCurpHash(String curpHash);

    Optional<Usuario> findByTelefono(String telefono);

    boolean existsByCorreoIgnoreCase(String correo);

    boolean existsByCurpHash(String curpHash);

    boolean existsByTelefono(String telefono);
}
