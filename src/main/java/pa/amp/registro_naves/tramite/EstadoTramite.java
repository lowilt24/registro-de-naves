package pa.amp.registro_naves.tramite;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Maquina de estados del tramite (D2, aprobada por la PO el 13-10-2026).
 *
 * Seis estados y siete transiciones, solo las definidas:
 *
 *   SOLICITUD_PRESENTADA --admitir-->  EN_REVISION
 *   SOLICITUD_PRESENTADA --desistir--> DESISTIDA
 *   EN_REVISION          --aprobar-->  APROBADA
 *   EN_REVISION          --rechazar--> RECHAZADA
 *   EN_REVISION          --observar--> OBSERVADA
 *   OBSERVADA            --subsanar--> EN_REVISION
 *   OBSERVADA            --desistir--> DESISTIDA
 *
 * APROBADA, RECHAZADA y DESISTIDA son finales. Cualquier otra combinacion
 * no tiene destino, y el servicio la convierte en 409.
 *
 * La maquina no sabe quien actua: eso lo decide AutorizacionTramite. Si
 * la PO cambia el actor, cambia esa clase y no esta.
 *
 * Los nombres coinciden con el CHECK de la V6. Agregar o quitar un estado
 * exige una V7.
 */
public enum EstadoTramite {

    SOLICITUD_PRESENTADA,
    EN_REVISION,
    OBSERVADA,
    APROBADA,
    RECHAZADA,
    DESISTIDA;

    /** P1 (propuesta): el tramite nace aqui al registrar la nave. */
    public static final EstadoTramite INICIAL = SOLICITUD_PRESENTADA;

    public boolean esFinal() {
        return this == APROBADA || this == RECHAZADA || this == DESISTIDA;
    }

    /** Estado al que lleva la accion, o vacio si la transicion no existe. */
    public Optional<EstadoTramite> destino(AccionTramite accion) {
        if (accion == null) {
            return Optional.empty();
        }
        EstadoTramite siguiente = switch (this) {
            case SOLICITUD_PRESENTADA -> switch (accion) {
                case ADMITIR  -> EN_REVISION;
                case DESISTIR -> DESISTIDA;
                default       -> null;
            };
            case EN_REVISION -> switch (accion) {
                case APROBAR  -> APROBADA;
                case RECHAZAR -> RECHAZADA;
                case OBSERVAR -> OBSERVADA;
                default       -> null;
            };
            case OBSERVADA -> switch (accion) {
                case SUBSANAR -> EN_REVISION;
                case DESISTIR -> DESISTIDA;
                default       -> null;
            };
            case APROBADA, RECHAZADA, DESISTIDA -> null;
        };
        return Optional.ofNullable(siguiente);
    }

    /** Acciones con destino desde este estado, sin mirar quien las pide. */
    public Set<AccionTramite> accionesPosibles() {
        Set<AccionTramite> posibles = EnumSet.noneOf(AccionTramite.class);
        for (AccionTramite accion : AccionTramite.values()) {
            if (destino(accion).isPresent()) {
                posibles.add(accion);
            }
        }
        return posibles;
    }
}
