package mx.uv.enzona.usuarios;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RolRepositorio extends JpaRepository<RolEntidad, Short> {

    Optional<RolEntidad> findByNombre(String nombre);
}
