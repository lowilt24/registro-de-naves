package pa.amp.registro_naves.aviso;

/** Datos capturados antes de cerrar la transaccion; nunca transporta entidades JPA. */
public record AvisoAlAgente(String referencia, Long naveId, String nave,
        String destinatario, String accion, String detalle, String observacion) {}
