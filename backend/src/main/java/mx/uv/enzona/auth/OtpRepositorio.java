package mx.uv.enzona.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OtpRepositorio extends JpaRepository<OtpVerificacion, Long> {

    Optional<OtpVerificacion> findFirstByUsuarioIdAndUsadoFalseOrderByCreadoDesc(Long usuarioId);

    @Modifying
    @Query("update OtpVerificacion o set o.usado = true where o.usuarioId = :usuarioId and o.usado = false")
    int invalidarVigentes(@Param("usuarioId") Long usuarioId);
}
