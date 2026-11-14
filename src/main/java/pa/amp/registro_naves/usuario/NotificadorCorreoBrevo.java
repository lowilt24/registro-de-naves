package pa.amp.registro_naves.usuario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.util.List;
import java.util.Map;

/** Correo por HTTPS para alojamientos que bloquean SMTP. No registra claves ni enlaces. */
public class NotificadorCorreoBrevo implements NotificadorCorreo {
    private static final Logger log = LoggerFactory.getLogger(NotificadorCorreoBrevo.class);
    private final RestClient cliente;
    private final String remitente;

    public NotificadorCorreoBrevo(RestClient cliente, String remitente) {
        this.cliente = cliente;
        this.remitente = remitente;
        if (remitente.isBlank()) throw new IllegalArgumentException("Configure CORREO_REMITENTE con el remitente verificado.");
    }

    @Override
    public void enviarValidacion(String destinatario, String nombre, String enlace) {
        try {
            enviarAviso(destinatario, "Navesitas: confirme su correo", """
                Hola %s:

                Su cuenta fue registrada en Registro de Naves. Para activarla, abra:
                %s

                El enlace vence en 24 horas. Si no creó esta cuenta, ignore este mensaje.
                """.formatted(nombre, enlace));
        } catch (MailSendException e) {
            // Conserva el contrato del registro: la cuenta queda pendiente y admite reenvío.
            log.warn("No se pudo enviar el correo de validación por HTTPS. Puede solicitarse un reenvío.");
        }
    }

    @Override
    public void enviarAviso(String destinatario, String asunto, String texto) {
        try {
            cliente.post().uri("/v3/smtp/email").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("sender", Map.of("name", "Registro de Naves", "email", remitente),
                    "to", List.of(Map.of("email", destinatario)), "subject", asunto, "textContent", texto))
                .retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            // No propagar el cuerpo HTTP del proveedor: puede contener información sensible.
            throw new MailSendException("El proveedor de correo HTTPS no aceptó el envío. Compruebe la configuración o cuota.");
        }
    }
}
