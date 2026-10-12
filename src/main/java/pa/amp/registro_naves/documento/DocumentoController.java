package pa.amp.registro_naves.documento;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** HU-07 y HU-08. Contrato: CONTRATO-DOCUMENTOS.md */
@RestController
@RequestMapping("/api/naves/{naveId}/documentos")
public class DocumentoController {

    private final DocumentoService servicio;

    public DocumentoController(DocumentoService servicio) {
        this.servicio = servicio;
    }

    /** HU-07 — carga. El tipo llega como texto para poder responder 400 con un mensaje propio. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentoResponse cargar(@PathVariable Long naveId,
                                    @RequestParam(name = "tipo", required = false) String tipo,
                                    @RequestParam(name = "archivo", required = false) MultipartFile archivo,
                                    Authentication autenticacion) {
        return servicio.cargar(naveId, tipo, archivo, autenticacion.getName());
    }

    /** HU-08 — consulta, con filtros opcionales. */
    @GetMapping
    public List<DocumentoResponse> listar(@PathVariable Long naveId,
                                          @RequestParam(name = "tipo", required = false) String tipo,
                                          @RequestParam(name = "estado", required = false) String estado,
                                          @RequestParam(name = "historial", defaultValue = "false") boolean historial,
                                          Authentication autenticacion) {
        return servicio.listar(naveId, tipo, estado, historial, autenticacion.getName());
    }

    /** HU-08 — descarga, con la propiedad verificada otra vez. */
    @GetMapping("/{documentoId}/archivo")
    public ResponseEntity<Resource> descargar(@PathVariable Long naveId,
                                              @PathVariable Long documentoId,
                                              Authentication autenticacion) {
        DocumentoService.Descarga d = servicio.descargar(naveId, documentoId, autenticacion.getName());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(d.nombre(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(new FileSystemResource(d.archivo()));
    }
}
