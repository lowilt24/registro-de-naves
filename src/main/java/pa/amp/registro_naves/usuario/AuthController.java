package pa.amp.registro_naves.usuario;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UsuarioService servicio;
    private final UsuarioRepository repositorio;

    public AuthController(UsuarioService servicio, UsuarioRepository repositorio) {
        this.servicio = servicio;
        this.repositorio = repositorio;
    }

    /** HU-01 */
    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> registrar(@Valid @RequestBody RegistroRequest peticion) {
        Usuario creado = servicio.registrar(
                peticion.correo(), peticion.password(), peticion.nombreCompleto(), peticion.rol());
        return Map.of(
                "id", creado.getId(),
                "correo", creado.getCorreo(),
                "rol", creado.getRol().name());
    }

    /** HU-02: devuelve quien esta autenticado en la sesion actual. */
    @GetMapping("/sesion")
    public Map<String, Object> sesion(Authentication autenticacion) {
        if (autenticacion == null || !autenticacion.isAuthenticated()) {
            return Map.of("autenticado", false);
        }
        return repositorio.findByCorreoIgnoreCase(autenticacion.getName())
                .<Map<String, Object>>map(u -> Map.of(
                        "autenticado", true,
                        "correo", u.getCorreo(),
                        "nombreCompleto", u.getNombreCompleto(),
                        "rol", u.getRol().name()))
                .orElse(Map.of("autenticado", false));
    }

    public record RegistroRequest(
            @NotBlank @Email @Size(max = 120) String correo,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 150) String nombreCompleto,
            Rol rol) {}
}
