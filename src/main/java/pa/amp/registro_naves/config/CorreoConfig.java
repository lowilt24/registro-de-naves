package pa.amp.registro_naves.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import pa.amp.registro_naves.usuario.NotificadorCorreo;
import pa.amp.registro_naves.usuario.NotificadorCorreoConsola;
import pa.amp.registro_naves.usuario.NotificadorCorreoSmtp;

/**
 * Elige como se envia el enlace de validacion de correo.
 *
 * Spring Boot crea el JavaMailSender solo si spring.mail.host esta
 * configurado. Si esta, se envia por SMTP; si no, el enlace se escribe en
 * la consola.
 */
@Configuration
public class CorreoConfig {

    @Bean
    public NotificadorCorreo notificadorCorreo(
            ObjectProvider<JavaMailSender> cartero,
            @Value("${navesitas.correo.remitente:no-responder@navesitas.pa}") String remitente) {
        JavaMailSender disponible = cartero.getIfAvailable();
        return disponible != null
                ? new NotificadorCorreoSmtp(disponible, remitente)
                : new NotificadorCorreoConsola();
    }
}
