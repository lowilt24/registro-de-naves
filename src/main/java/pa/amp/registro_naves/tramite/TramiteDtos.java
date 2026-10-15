package pa.amp.registro_naves.tramite;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** Cuerpos de entrada y salida de HU-09 y HU-10. */
public final class TramiteDtos {

    private TramiteDtos() {
    }

    /** POST /api/naves/{id}/tramite/estado */
    public record CambioEstadoRequest(
            @NotNull(message = "Indique la acción.")
            AccionTramite accion,

            @Size(max = 1000, message = "La observación no puede exceder 1000 caracteres.")
            String observacion) {
    }

    /**
     * GET /api/naves/{id}/tramite. "acciones" son las que puede pedir quien
     * consulta, en este estado: la pantalla pinta un boton por cada una y
     * ninguno mas.
     */
    public record TramiteResponse(
            Long naveId,
            String nave,
            EstadoTramite estado,
            boolean esFinal,
            List<AccionTramite> acciones,
            OffsetDateTime creadoEn,
            OffsetDateTime actualizadoEn) {

        static TramiteResponse de(Tramite t, List<AccionTramite> acciones) {
            return new TramiteResponse(
                    t.getNave().getId(),
                    t.getNave().getNombre(),
                    t.getEstado(),
                    t.getEstado().esFinal(),
                    acciones,
                    t.getCreadoEn(),
                    t.getActualizadoEn());
        }
    }

    /** Una entrada de GET /api/naves/{id}/tramite/historial. */
    public record HistorialResponse(
            OffsetDateTime fecha,
            String usuario,
            String rol,
            AccionTramite accion,
            EstadoTramite estadoAnterior,
            EstadoTramite estadoNuevo,
            String observacion) {

        static HistorialResponse de(HistorialEstado h) {
            return new HistorialResponse(
                    h.getFecha(),
                    h.getUsuario().getNombreCompleto(),
                    h.getUsuario().getRol().name(),
                    h.getAccion(),
                    h.getEstadoAnterior(),
                    h.getEstadoNuevo(),
                    h.getObservacion());
        }
    }

    /**
     * GET /api/naves/conteo-por-estado (HU-10). "estados" trae los seis, en
     * el orden de la maquina, con cero los que no tienen naves.
     */
    public record ConteoPorEstadoResponse(Map<EstadoTramite, Long> estados, long total) {
    }
}
