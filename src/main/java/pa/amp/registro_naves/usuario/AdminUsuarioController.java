package pa.amp.registro_naves.usuario;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * HU-14: alta de funcionarios DGMM y auditores.
 * SecurityConfig restringe /api/admin/** a ADMINISTRADOR.
 */
@RestController
@RequestMapping("/api/admin/usuarios")
public class AdminUsuarioController {

    private final UsuarioService servicio;

    public AdminUsuarioController(UsuarioService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> crear(
            @Valid @RequestBody AltaUsuarioRequest peticion) {

        Usuario creado = servicio.crearUsuarioInterno(
                peticion.correo(),
                peticion.password(),
                peticion.nombreCompleto(),
                peticion.rol());

        return Map.of(
                "id", creado.getId(),
                "correo", creado.getCorreo(),
                "nombreCompleto", creado.getNombreCompleto(),
                "rol", creado.getRol().name(),
                "correoValidado", creado.isCorreoValidado(),
                "mensaje",
                "Cuenta creada. El usuario debe validar su correo antes de iniciar sesion.");
    }

    public record AltaUsuarioRequest(
            @NotBlank
            @Email
            @Size(max = 120)
            String correo,

            @NotBlank
            @Size(min = 8, max = 72)
            String password,

            @NotBlank
            @Size(max = 150)
            String nombreCompleto,

            @NotNull(message = "Seleccione un rol.")
            Rol rol) {
    }
}