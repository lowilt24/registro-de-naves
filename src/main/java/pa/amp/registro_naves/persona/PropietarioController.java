package pa.amp.registro_naves.persona;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HU-05 */
@RestController
@RequestMapping("/api")
public class PropietarioController {

    private final PropietarioService servicio;

    public PropietarioController(PropietarioService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/propietarios")
    @ResponseStatus(HttpStatus.CREATED)
    public PropietarioResponse registrar(@Valid @RequestBody PropietarioRequest peticion,
                                         Authentication autenticacion) {
        return servicio.registrar(peticion, autenticacion.getName());
    }

    @GetMapping("/naves/{naveId}/propietarios")
    public List<PropietarioResponse> listarPorNave(@PathVariable Long naveId,
                                                   Authentication autenticacion) {
        return servicio.listarPorNave(naveId, autenticacion.getName());
    }
}
