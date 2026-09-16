package pa.amp.registro_naves.nave;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/naves")
@Validated
public class NaveController {

    private final NaveService servicio;

    public NaveController(NaveService servicio) {
        this.servicio = servicio;
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
        String correo = autenticacion == null ? null : autenticacion.getName();
        return servicio.registrar(peticion, correo);
    }

    @GetMapping
    public List<NaveResponse> listar() {
        return servicio.listar();
    }

    @GetMapping("/{id}")
    public NaveResponse obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }
}
