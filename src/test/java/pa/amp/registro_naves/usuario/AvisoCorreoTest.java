package pa.amp.registro_naves.usuario;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import pa.amp.registro_naves.aviso.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AvisoCorreoTest {
    @Test void smtpReutilizaRemitenteYEnviaTextoSinAdjuntos() {
        var sender=mock(JavaMailSender.class);
        var correo=new NotificadorCorreoSmtp(sender,"origen@example.com");
        correo.enviarAviso("dueno@example.com","Asunto","Texto con acentos: aprobación");
        var mensaje=ArgumentCaptor.forClass(SimpleMailMessage.class); verify(sender).send(mensaje.capture());
        assertThat(mensaje.getValue().getFrom()).isEqualTo("origen@example.com");
        assertThat(mensaje.getValue().getTo()).containsExactly("dueno@example.com");
        assertThat(mensaje.getValue().getSubject()).isEqualTo("Asunto");
        assertThat(mensaje.getValue().getText()).isEqualTo("Texto con acentos: aprobación");
    }
    @Test void smtpPropagaFalloParaRegistrarloDespuesDelCommit() {
        var sender=mock(JavaMailSender.class);
        doThrow(new MailSendException("Sin SMTP")).when(sender).send(any(SimpleMailMessage.class));
        assertThatThrownBy(()->new NotificadorCorreoSmtp(sender,"origen@example.com")
                .enviarAviso("dueno@example.com","Asunto","Texto")).isInstanceOf(MailSendException.class);
    }
    @Test void sinSmtpNoSimulaEntrega() {
        assertThatThrownBy(()->new NotificadorCorreoConsola().enviarAviso("dueno@example.com","Asunto","Texto"))
                .isInstanceOf(MailSendException.class);
    }
    @Test void enlaceUsaLaBaseConfiguradaSinDuplicarBarras() {
        var correo=mock(NotificadorCorreo.class);
        new AvisosAlAgente(correo,"https://registro.example.com///").enviar(new AvisoAlAgente(
                "tramite-1",42L,"Nave Pacífico","dueno@example.com","APROBAR","Estado del trámite: Aprobada",null));
        var texto=ArgumentCaptor.forClass(String.class);
        verify(correo).enviarAviso(eq("dueno@example.com"),anyString(),texto.capture());
        assertThat(texto.getValue()).contains("Acción: Aprobar trámite", "https://registro.example.com/expediente.html?naveId=42")
                .doesNotContain("Observación:", "null");
    }
}
