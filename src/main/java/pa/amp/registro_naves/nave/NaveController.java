package pa.amp.registro_naves.nave;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import pa.amp.registro_naves.tramite.EstadoTramite;
import pa.amp.registro_naves.tramite.TramiteDtos.ConteoPorEstadoResponse;
import pa.amp.registro_naves.tramite.TramiteService;

import java.util.List;

@RestController
@RequestMapping("/api/naves")
@Validated
public class NaveController {

    private final NaveService servicio;
    private final TramiteService tramites;

    public NaveController(NaveService servicio, TramiteService tramites) {
        this.servicio = servicio;
        this.tramites = tramites;
    }

    /** HU-04 — GET /api/naves/disponibilidad?nombre=... */
    @GetMapping("/disponibilidad")
    public DisponibilidadResponse disponibilidad(
            @RequestParam(name = "nombre", required = false, defaultValue = "")
            @Size(max = 120, message = "El nombre no puede exceder 120 caracteres.")
            String nombre) {
        return servicio.consultarDisponibilidad(nombre);
    }

    /** HU-03 — POST /api/naves */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NaveResponse registrar(@Valid @RequestBody NaveRequest peticion,
                                  Authentication autenticacion) {
        return servicio.registrar(peticion, autenticacion.getName());
    }

    /**
     * Naves del usuario. HU-10: ?estado=EN_REVISION filtra por el estado
     * del tramite; es opcional y sin el la respuesta no cambia. Un estado
     * que no existe es 400 (MethodArgumentTypeMismatchException), nunca 500.
     */
    @GetMapping
    public List<NaveResponse> listar(@RequestParam(name = "estado", required = false) EstadoTramite estado,
                                     Authentication autenticacion) {
        return servicio.listarPropias(autenticacion.getName(), estado);
    }

    /** HU-10 — cuantas naves del usuario hay en cada estado del tramite. */
    @GetMapping("/conteo-por-estado")
    public ConteoPorEstadoResponse conteoPorEstado(Authentication autenticacion) {
        return tramites.contarPorEstado(autenticacion.getName());
    }

    @GetMapping("/{id}")
    public NaveResponse obtener(@PathVariable Long id, Authentication autenticacion) {
        return servicio.obtener(id, autenticacion.getName());
    }
}
