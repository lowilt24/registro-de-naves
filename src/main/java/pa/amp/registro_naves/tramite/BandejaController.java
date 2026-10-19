package pa.amp.registro_naves.tramite;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pa.amp.registro_naves.tramite.TramiteDtos.BandejaItem;
import pa.amp.registro_naves.tramite.TramiteDtos.ConteoPorEstadoResponse;

import java.util.List;

/**
 * HU-11 — bandeja de tramites de la DGMM.
 *
 * Orden de los errores (regla del equipo, Sprint 6): 401 -> 400 -> 403.
 * Un estado que no existe falla al convertir el parametro, antes de entrar
 * al metodo, asi que tambien el agente recibe 400 y no 403 en ese caso.
 */
@RestController
@RequestMapping("/api/tramites")
public class BandejaController {

    private final TramiteService servicio;

    public BandejaController(TramiteService servicio) {
        this.servicio = servicio;
    }

    /** ?estado=EN_REVISION filtra; sin el, Solicitud presentada y En revision. */
    @GetMapping
    public List<BandejaItem> bandeja(@RequestParam(name = "estado", required = false) EstadoTramite estado,
                                     Authentication autenticacion) {
        return servicio.bandeja(autenticacion.getName(), estado);
    }

    @GetMapping("/conteo-por-estado")
    public ConteoPorEstadoResponse conteo(Authentication autenticacion) {
        return servicio.contarBandeja(autenticacion.getName());
    }
}
