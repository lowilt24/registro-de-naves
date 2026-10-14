package pa.amp.registro_naves.documento;

import java.util.List;

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
    SMC;

    /**
     * Sprint 5 (PO, 13-10-2026): documentos sin los que el tramite no pasa a
     * "En revision". Unico lugar donde se declaran, en el orden oficial (el
     * de los "faltantes" del 409).
     *
     * Hoy son los cuatro. Deuda del backlog: el ITC solo aplica a naves de
     * 24 m o mas y el SMC a carga de 500 GT o mas o pasajeros, en viaje
     * internacional; volverlos condicionales es cambiar esta lista por una
     * regla, sin tocar a quien la usa.
     */
    public static final List<TipoDocumento> OBLIGATORIOS =
            List.of(CERTIFICADO_PROPIEDAD, PODER_NOTARIAL, ITC, SMC);
}
