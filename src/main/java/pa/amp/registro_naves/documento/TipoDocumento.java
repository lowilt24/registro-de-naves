package pa.amp.registro_naves.documento;

/**
 * HU-07, criterio 1: lista cerrada de documentos del expediente. La base
 * la refuerza con ck_documentos_tipo (V5).
 */
public enum TipoDocumento {
    /** Certificado de propiedad de la nave. */
    CERTIFICADO_PROPIEDAD,
    /** Poder notarial que acredita al agente residente (HU-06). */
    PODER_NOTARIAL,
    /** International Tonnage Certificate: certificado internacional de arqueo. */
    ITC,
    /** Safety Management Certificate: certificado de gestion de la seguridad (Codigo IGS). */
    SMC
}
