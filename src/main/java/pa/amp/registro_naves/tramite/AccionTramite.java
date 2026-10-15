package pa.amp.registro_naves.tramite;

/**
 * Lo que alguien hace sobre un tramite (HU-09).
 *
 * Las seis manuales son las transiciones de la maquina (D2). PRESENTAR y
 * MIGRACION solo aparecen en el historial: la primera la escribe HU-03 al
 * registrar la nave y la segunda la V6 al crear los tramites que faltaban.
 * Nadie las puede pedir por la API.
 */
public enum AccionTramite {

    PRESENTAR(Actor.SISTEMA),
    MIGRACION(Actor.SISTEMA),

    ADMITIR(Actor.DGMM),
    OBSERVAR(Actor.DGMM),
    APROBAR(Actor.DGMM),
    RECHAZAR(Actor.DGMM),

    SUBSANAR(Actor.AGENTE),
    DESISTIR(Actor.AGENTE);

    /** Quien puede ejecutar la accion (D1). */
    public enum Actor {
        /** Solo el sistema; no se pide por la API. */
        SISTEMA,
        /** El agente naviero que registro la nave. */
        AGENTE,
        /** Un funcionario de la DGMM, sobre cualquier tramite. */
        DGMM
    }

    private final Actor actor;

    AccionTramite(Actor actor) {
        this.actor = actor;
    }

    public Actor actor() {
        return actor;
    }

    /** Se puede pedir por POST /api/naves/{id}/tramite/estado. */
    public boolean esManual() {
        return actor != Actor.SISTEMA;
    }

    /** P5 (propuesta): observar y rechazar exigen decir por que. */
    public boolean exigeObservacion() {
        return this == OBSERVAR || this == RECHAZAR;
    }
}
