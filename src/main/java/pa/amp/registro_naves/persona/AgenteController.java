package pa.amp.registro_naves.persona;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HU-06 */
@RestController
@RequestMapping("/api/naves/{naveId}/agente-residente")
public class AgenteController {

    private final AgenteService servicio;

    public AgenteController(AgenteService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgenteResponse designar(@PathVariable Long naveId,
                                   @Valid @RequestBody AgenteRequest peticion,
                                   Authentication autenticacion) {
        return servicio.designar(naveId, peticion, autenticacion.getName());
    }

    @GetMapping
    public AgenteResponse consultar(@PathVariable Long naveId,
                                    Authentication autenticacion) {
        return servicio.consultarVigente(naveId, autenticacion.getName());
    }

    @GetMapping("/historial")
    public List<AgenteResponse> historial(@PathVariable Long naveId,
                                          Authentication autenticacion) {
        return servicio.historial(naveId, autenticacion.getName());
    }
}
