package pa.amp.registro_naves.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import pa.amp.registro_naves.usuario.NotificadorCorreo;
import pa.amp.registro_naves.usuario.NotificadorCorreoConsola;
import pa.amp.registro_naves.usuario.NotificadorCorreoSmtp;
import pa.amp.registro_naves.usuario.NotificadorCorreoBrevo;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.Duration;

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
            @Value("${navesitas.correo.remitente:no-responder@navesitas.pa}") String remitente,
            @Value("${navesitas.correo.enlaces-en-log:true}") boolean enlacesEnLog,
            @Value("${navesitas.correo.transporte:smtp}") String transporte,
            @Value("${navesitas.correo.api-key:}") String apiKey) {
        if (transporte.equals("brevo")) {
            if (apiKey.isBlank()) throw new IllegalArgumentException("Configure BREVO_API_KEY en el alojamiento.");
            var transporteHttp = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());
            transporteHttp.setReadTimeout(Duration.ofSeconds(8));
            var cliente = RestClient.builder().baseUrl("https://api.brevo.com")
                .requestFactory(transporteHttp).defaultHeader("api-key", apiKey).build();
            return new NotificadorCorreoBrevo(cliente, remitente);
        }
        if (!transporte.equals("smtp")) throw new IllegalArgumentException("Transporte de correo no válido.");
        JavaMailSender disponible = cartero.getIfAvailable();
        return disponible != null
                ? new NotificadorCorreoSmtp(disponible, remitente, enlacesEnLog)
                : new NotificadorCorreoConsola();
    }
}
