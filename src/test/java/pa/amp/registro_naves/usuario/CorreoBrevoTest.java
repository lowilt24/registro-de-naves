package pa.amp.registro_naves.usuario;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.mail.MailSendException;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import pa.amp.registro_naves.config.CorreoConfig;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CorreoBrevoTest {
    @Test void enviaValidacionPorHttpsConClaveEnCabeceraYTextoPlano() {
        var builder=RestClient.builder().baseUrl("https://api.brevo.com").defaultHeader("api-key","CLAVE_SOLO_PRUEBA");
        var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email")).andExpect(method(HttpMethod.POST))
            .andExpect(header("api-key","CLAVE_SOLO_PRUEBA"))
            .andExpect(jsonPath("$.sender.email").value("remitente@example.test"))
            .andExpect(jsonPath("$.to[0].email").value("destino@example.test"))
            .andExpect(jsonPath("$.textContent").value(org.hamcrest.Matchers.containsString("https://app.example.test/validar?token=PRUEBA")))
            .andExpect(jsonPath("$.htmlContent").doesNotExist())
            .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("{\"messageId\":\"prueba\"}"));
        new NotificadorCorreoBrevo(builder.build(),"remitente@example.test")
            .enviarValidacion("destino@example.test","Persona","https://app.example.test/validar?token=PRUEBA");
        server.verify();
    }
    @Test void falloDeCuotaNoRompeRegistroYNoFiltraRespuestaDelProveedor() {
        var builder=RestClient.builder().baseUrl("https://api.brevo.com");
        var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("información privada del proveedor"));
        var correo=new NotificadorCorreoBrevo(builder.build(),"remitente@example.test");
        assertThatCode(()->correo.enviarValidacion("destino@example.test","Persona","https://app.example.test/validar?token=PRUEBA"))
            .doesNotThrowAnyException();
        server.verify();
        var builder2=RestClient.builder().baseUrl("https://api.brevo.com");
        var server2=MockRestServiceServer.bindTo(builder2).build();
        server2.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("TOKEN_PRIVADO_DEL_PROVEEDOR"));
        assertThatThrownBy(()->new NotificadorCorreoBrevo(builder2.build(),"remitente@example.test")
            .enviarAviso("destino@example.test","Aviso","Texto"))
            .isInstanceOf(MailSendException.class).hasMessageNotContaining("TOKEN_PRIVADO_DEL_PROVEEDOR");
        server2.verify();
    }
    @Test void configuracionGratisNoUsaSmtpNiAceptaClaveAusente() {
        @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> smtp=mock(ObjectProvider.class);
        var config=new CorreoConfig();
        assertThat(config.notificadorCorreo(smtp,"remitente@example.test",false,"brevo","CLAVE_PRUEBA"))
            .isInstanceOf(NotificadorCorreoBrevo.class);
        verifyNoInteractions(smtp);
        assertThatThrownBy(()->config.notificadorCorreo(smtp,"remitente@example.test",false,"brevo",""))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
