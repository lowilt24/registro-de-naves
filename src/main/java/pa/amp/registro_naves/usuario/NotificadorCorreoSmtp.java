package pa.amp.registro_naves.usuario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Envio real por SMTP. En desarrollo apunta a Mailpit (docker-compose.yml),
 * que recibe el correo sin mandarlo a nadie y lo muestra en
 * http://localhost:8025.
 */
public class NotificadorCorreoSmtp implements NotificadorCorreo {

    private static final Logger log = LoggerFactory.getLogger(NotificadorCorreoSmtp.class);

    private final JavaMailSender cartero;
    private final String remitente;

    public NotificadorCorreoSmtp(JavaMailSender cartero, String remitente) {
        this.cartero = cartero;
        this.remitente = remitente;
    }

    @Override
    public void enviarValidacion(String destinatario, String nombre, String enlace) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente);
        mensaje.setTo(destinatario);
        mensaje.setSubject("Navesitas: confirme su correo");
        mensaje.setText("""
                Hola %s:

                Se creo una cuenta en Navesitas con este correo. Para activarla,
                abra este enlace:

                %s

                El enlace vence en 24 horas. Si usted no creo la cuenta, ignore
                este mensaje y la cuenta no se activara.
                """.formatted(nombre, enlace));
        try {
            cartero.send(mensaje);
        } catch (MailException e) {
            // El registro no se pierde por esto: el usuario puede pedir el
            // reenvio. El enlace queda en la consola para no trabar la demo.
            log.warn("No se pudo enviar el correo de validacion a {} ({}). Enlace: {}",
                    destinatario, e.getMessage(), enlace);
        }
    }
}
