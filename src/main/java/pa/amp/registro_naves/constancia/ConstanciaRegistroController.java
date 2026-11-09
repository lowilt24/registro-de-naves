package pa.amp.registro_naves.constancia;

import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/naves/{naveId}/constancia")
public class ConstanciaRegistroController {
    private final ConstanciaRegistroService servicio;
    public ConstanciaRegistroController(ConstanciaRegistroService servicio) { this.servicio = servicio; }

    @GetMapping
    public ResponseEntity<byte[]> descargar(@PathVariable Long naveId, Authentication autenticacion) {
        var archivo = servicio.descargar(naveId, autenticacion.getName());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .contentLength(archivo.contenido().length).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(archivo.nombre()).build().toString())
                .header("X-Content-SHA256", archivo.hashSha256()).body(archivo.contenido());
    }
}
