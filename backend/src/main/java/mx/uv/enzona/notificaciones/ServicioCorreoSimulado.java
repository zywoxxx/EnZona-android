package mx.uv.enzona.notificaciones;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * SIMULACIÓN de correo: no se envía nada. Deja constancia en el log para que
 * quede claro qué se habría enviado. Se sustituye por un adaptador SMTP o de un
 * proveedor transaccional sin tocar el resto del sistema.
 */
@Service
public class ServicioCorreoSimulado implements ServicioCorreo {

    private static final Logger log = LoggerFactory.getLogger(ServicioCorreoSimulado.class);

    @Override
    public void enviar(String destinatario, String asunto, String cuerpo) {
        // El cuerpo no se registra: puede contener el código de verificación o el QR del boleto
        log.info("SIMULACIÓN de correo (no enviado) · para {} · asunto «{}» · {} caracteres",
                destinatario, asunto, cuerpo == null ? 0 : cuerpo.length());
    }
}
