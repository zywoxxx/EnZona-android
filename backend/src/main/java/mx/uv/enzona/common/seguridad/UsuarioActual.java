package mx.uv.enzona.common.seguridad;

import java.util.Set;

/** Cuenta autenticada de la petición en curso (principal de Spring Security). */
public record UsuarioActual(Long id, String nombre, String correo, Set<String> roles) {

    public boolean tiene(String rol) {
        return roles.contains(rol);
    }

    public boolean esAdmin() {
        return tiene("ADMIN");
    }
}
