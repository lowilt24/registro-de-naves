package pa.amp.registro_naves.nave;

/** Respuesta de HU-04. */
public record DisponibilidadResponse(
        String nombreConsultado,
        boolean disponible,
        String mensaje
) {}
