package mx.uv.enzona.notificaciones;

/**
 * Envío de correo (RF-12: el boleto llega también por correo; avisos de
 * cancelación). El sistema aún no tiene proveedor contratado: la implementación
 * disponible solo registra el mensaje en el log. El boleto siempre queda
 * disponible dentro de la aplicación; el correo es un canal adicional.
 */
public interface ServicioCorreo {

    void enviar(String destinatario, String asunto, String cuerpo);
}
