package pa.amp.registro_naves.usuario;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
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
                "rol", creado.getRol().name(),
                "correoValidado", creado.isCorreoValidado());
    }

    /**
     * Sprint 4 — el enlace del correo de validacion. Se abre desde el
     * correo, asi que responde con una redireccion al login y el aviso
     * que corresponde, no con JSON.
     */
    @GetMapping("/validar")
    public ResponseEntity<Void> validar(@RequestParam(name = "token", required = false) String token) {
        String destino = switch (servicio.validar(token)) {
            case VALIDADO -> "/login.html?validado=1";
            case VENCIDO  -> "/login.html?enlace=vencido";
            case INVALIDO -> "/login.html?enlace=invalido";
        };
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(destino).toString())
                .build();
    }

    /** Sprint 4 — pedir un enlace nuevo. Responde igual exista o no la cuenta. */
    @PostMapping("/reenviar-validacion")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> reenviar(@Valid @RequestBody ReenvioRequest peticion) {
        servicio.reenviarValidacion(peticion.correo());
        return Map.of("mensaje",
                "Si hay una cuenta pendiente de validar con ese correo, le enviamos un enlace nuevo.");
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

    public record ReenvioRequest(@NotBlank @Email @Size(max = 120) String correo) {}
}
