package pa.amp.registro_naves.patente;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;
import static pa.amp.registro_naves.patente.PatentePeticiones.*;

@RestController
public class PatenteController {
    private final PatenteService servicio;
    private static final String BASE="/api/naves/{naveId}/patente-provisional";
    public PatenteController(PatenteService servicio) {this.servicio=servicio;}
    @GetMapping("/api/patentes-provisionales")
    public List<Map<String,Object>> listar(@RequestParam(required=false) String estado,Principal usuario) {
        return servicio.listar(usuario.getName(),estado);
    }
    @GetMapping(BASE)
    public Map<String,Object> consultar(@PathVariable Long naveId,Principal u) {return servicio.consultar(naveId,u.getName());}
    @PostMapping(BASE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> crear(@PathVariable Long naveId,@Valid @RequestBody Crear p,Principal u) {return servicio.crear(naveId,p,u.getName());}
    @PostMapping(BASE+"/requisitos")
    public Map<String,Object> requisito(@PathVariable Long naveId,@Valid @RequestBody Requisito p,Principal u) {return servicio.agregarRequisito(naveId,p,u.getName());}
    @PostMapping(BASE+"/requisitos/{requisitoId}/reutilizacion")
    public Map<String,Object> reutilizacion(@PathVariable Long naveId,@PathVariable Long requisitoId,@Valid @RequestBody Reutilizacion p,Principal u) {
        return servicio.permitirReutilizacion(naveId,requisitoId,p,u.getName());
    }
    @PostMapping(BASE+"/requisitos/confirmar")
    public Map<String,Object> confirmar(@PathVariable Long naveId,@Valid @RequestBody Confirmacion p,Principal u) {return servicio.confirmarRequisitos(naveId,p,u.getName());}
    @PostMapping(value=BASE+"/requisitos/{requisitoId}/archivo",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> cargar(@PathVariable Long naveId,@PathVariable Long requisitoId,@RequestParam MultipartFile archivo,Principal u) {
        return servicio.cargar(naveId,requisitoId,archivo,u.getName());
    }
    @PostMapping(BASE+"/requisitos/{requisitoId}/referenciar")
    public Map<String,Object> referenciar(@PathVariable Long naveId,@PathVariable Long requisitoId,@Valid @RequestBody Referencia p,Principal u) {
        return servicio.referenciar(naveId,requisitoId,p,u.getName());
    }
    @PostMapping(BASE+"/enviar")
    public Map<String,Object> enviar(@PathVariable Long naveId,Principal u) {return servicio.enviar(naveId,u.getName());}
    @PostMapping(BASE+"/evidencias/{evidenciaId}/revision")
    public Map<String,Object> revisar(@PathVariable Long naveId,@PathVariable Long evidenciaId,@Valid @RequestBody Revision p,Principal u) {
        return servicio.revisar(naveId,evidenciaId,p,u.getName());
    }
    @PostMapping(BASE+"/devolver")
    public Map<String,Object> devolver(@PathVariable Long naveId,@Valid @RequestBody Devolucion p,Principal u) {return servicio.devolver(naveId,p,u.getName());}
    @PostMapping(BASE+"/aprobar")
    public Map<String,Object> aprobar(@PathVariable Long naveId,@Valid @RequestBody Contexto p,Principal u) {return servicio.aprobar(naveId,p,u.getName());}
    @GetMapping(BASE+"/archivo")
    public ResponseEntity<byte[]> pdf(@PathVariable Long naveId,Principal u) {return descarga(servicio.descargarPdf(naveId,u.getName()));}
    @GetMapping(BASE+"/evidencias/{evidenciaId}/archivo")
    public ResponseEntity<byte[]> evidencia(@PathVariable Long naveId,@PathVariable Long evidenciaId,Principal u) {
        return descarga(servicio.descargarEvidencia(naveId,evidenciaId,u.getName()));
    }
    private ResponseEntity<byte[]> descarga(PatenteService.Archivo archivo) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).contentLength(archivo.contenido().length)
            .cacheControl(CacheControl.noStore()).header("X-Content-SHA256",archivo.hashSha256())
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(archivo.nombre(),StandardCharsets.UTF_8).build().toString())
            .body(archivo.contenido());
    }
}
