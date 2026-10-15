package pa.amp.registro_naves.tramite;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pa.amp.registro_naves.tramite.TramiteDtos.CambioEstadoRequest;
import pa.amp.registro_naves.tramite.TramiteDtos.HistorialResponse;
import pa.amp.registro_naves.tramite.TramiteDtos.TramiteResponse;

import java.util.List;

/** HU-09 — estado del tramite de una nave. */
@RestController
@RequestMapping("/api/naves/{naveId}/tramite")
public class TramiteController {

    private final TramiteService servicio;

    public TramiteController(TramiteService servicio) {
        this.servicio = servicio;
    }

    /** Estado actual y las acciones que puede pedir quien consulta. */
    @GetMapping
    public TramiteResponse consultar(@PathVariable Long naveId, Authentication autenticacion) {
        return servicio.consultar(naveId, autenticacion.getName());
    }

    /** Ejecuta una accion: { "accion": "ADMITIR", "observacion": "..." } */
    @PostMapping("/estado")
    public TramiteResponse cambiarEstado(@PathVariable Long naveId,
                                         @Valid @RequestBody CambioEstadoRequest peticion,
                                         Authentication autenticacion) {
        return servicio.cambiarEstado(naveId, peticion, autenticacion.getName());
    }

    /** Linea de tiempo: quien, cuando, la accion y la observacion. */
    @GetMapping("/historial")
    public List<HistorialResponse> historial(@PathVariable Long naveId, Authentication autenticacion) {
        return servicio.historial(naveId, autenticacion.getName());
    }
}
