package pa.amp.registro_naves.usuario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Respaldo cuando no hay servidor de correo configurado: escribe el
 * enlace en la consola de la aplicacion. Sirve para desarrollo y para
 * las pruebas; en una instalacion real se configura spring.mail.host.
 */
public class NotificadorCorreoConsola implements NotificadorCorreo {

    private static final Logger log = LoggerFactory.getLogger(NotificadorCorreoConsola.class);

    @Override
    public void enviarValidacion(String destinatario, String nombre, String enlace) {
        log.warn("No hay servidor de correo configurado (spring.mail.host). "
                + "Enlace de validacion para {}: {}", destinatario, enlace);
    }
}
