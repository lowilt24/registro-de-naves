package pa.amp.registro_naves.aviso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pa.amp.registro_naves.usuario.NotificadorCorreo;

/** HU-16: el correo nunca participa en la transaccion de la decision. */
@Component
public class AvisosAlAgente {
    private static final Logger log = LoggerFactory.getLogger(AvisosAlAgente.class);
    private final NotificadorCorreo correo;
    private final String urlBase;

    public AvisosAlAgente(NotificadorCorreo correo,
            @Value("${navesitas.url-base:http://localhost:8080}") String urlBase) {
        this.correo = correo;
        this.urlBase = urlBase.replaceAll("/+$", "");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enviar(AvisoAlAgente aviso) {
        try {
            String observacion = aviso.observacion() == null || aviso.observacion().isBlank()
                    ? "" : "\nObservación: " + aviso.observacion() + "\n";
            String texto = """
                    Se registró una decisión de la DGMM en el expediente de su nave.

                    Nave: %s
                    Número de registro: %d
                    Acción: %s
                    %s
                    %s
                    Consulte el expediente e inicie sesión con su cuenta:
                    %s/expediente.html?naveId=%d

                    Registro de Naves
                    """.formatted(aviso.nave(), aviso.naveId(), accion(aviso.accion()),
                            aviso.detalle(), observacion, urlBase, aviso.naveId());
            correo.enviarAviso(aviso.destinatario(), "Registro de Naves: actualización de su trámite", texto);
        } catch (RuntimeException e) {
            // El commit ya termino. No filtrar destinatarios, observaciones ni errores SMTP con PII.
            log.warn("HU-16: no se pudo enviar el aviso {} de nave {} ({}).",
                    aviso.referencia(), aviso.naveId(), e.getClass().getSimpleName());
        }
    }

    private static String accion(String accion) {
        return switch (accion) {
            case "ADMITIR" -> "Admitir trámite";
            case "OBSERVAR" -> "Observar trámite";
            case "APROBAR" -> "Aprobar trámite";
            case "RECHAZAR" -> "Rechazar trámite";
            case "OBSERVAR_DOCUMENTO" -> "Observar documento";
            default -> accion;
        };
    }
}
