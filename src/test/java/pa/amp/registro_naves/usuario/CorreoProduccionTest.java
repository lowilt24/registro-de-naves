package pa.amp.registro_naves.usuario;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class CorreoProduccionTest {
    @Test void falloSmtpEnProduccionNoPublicaEnlaceNiDestinatarioEnLogs() {
        var sender=mock(JavaMailSender.class);
        doThrow(new MailSendException("SMTP no disponible")).when(sender).send(any(SimpleMailMessage.class));
        var logger=(Logger)LoggerFactory.getLogger(NotificadorCorreoSmtp.class);
        var eventos=new ListAppender<ILoggingEvent>();eventos.start();logger.addAppender(eventos);
        try {
            new NotificadorCorreoSmtp(sender,"origen@example.test",false)
                .enviarValidacion("persona@example.test","Persona","https://app.example.test/validar?token=SECRETO_PRUEBA");
            assertThat(eventos.list).isNotEmpty();
            assertThat(eventos.list).allSatisfy(e->assertThat(e.getFormattedMessage())
                .doesNotContain("SECRETO_PRUEBA","persona@example.test","https://app.example.test"));
        } finally {logger.detachAppender(eventos);eventos.stop();}
    }
    @Test void validacionEntregaElEnlaceAlProveedorSinAfirmarEntregaAlBuzon() {
        var sender=mock(JavaMailSender.class);
        var mensaje=org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        new NotificadorCorreoSmtp(sender,"origen@example.test",false)
            .enviarValidacion("persona@example.test","Persona","https://app.example.test/validar?token=PRUEBA");
        verify(sender).send(mensaje.capture());
        assertThat(mensaje.getValue().getTo()).containsExactly("persona@example.test");
        assertThat(mensaje.getValue().getText()).contains("https://app.example.test/validar?token=PRUEBA","24 horas");
    }
}
