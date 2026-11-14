package pa.amp.registro_naves.patente;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/** Consulta pública limitada: no permite descargar el expediente ni sus adjuntos. */
@RestController
public class VerificacionPatenteController {
    private final PatenteService servicio;
    public VerificacionPatenteController(PatenteService servicio) { this.servicio = servicio; }

    @GetMapping("/api/verificacion/patentes/{identificador}")
    public ResponseEntity<PatenteService.Verificacion> consultar(@PathVariable UUID identificador) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .header("Referrer-Policy", "no-referrer")
            .body(servicio.verificar(identificador));
    }
}
