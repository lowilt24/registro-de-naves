package pa.amp.registro_naves.documento;

/**
 * Estado de revision de un documento. FALTANTE no esta aqui a proposito:
 * no es un estado guardado sino la ausencia de documento, y lo calcula la
 * pantalla (HU-08, criterio 2).
 */
public enum EstadoDocumento {
    PENDIENTE,
    APROBADO,
    OBSERVADO
}
