package pa.amp.registro_naves.reporte;

import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/reportes")
public class ReportesController {
    private final ReportesService servicio;
    public ReportesController(ReportesService servicio) { this.servicio=servicio; }
    @GetMapping("/registro")
    public ResponseEntity<ReportesService.Reporte> consultar(@RequestParam MultiValueMap<String,String> filtros,Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.consultar(auth.getName(),filtros));
    }
    @GetMapping("/registro.csv")
    public ResponseEntity<byte[]> csv(@RequestParam MultiValueMap<String,String> filtros,Authentication auth) {
        var reporte=servicio.consultar(auth.getName(),filtros);
        var texto=new StringBuilder("\uFEFFDesde,Hasta,Seccion,Categoria,Cantidad,Dias promedio de espera\r\n");
        for(var c:reporte.navesPorTipo()) fila(texto,reporte,"Naves por tipo",c.codigo(),c.cantidad(),null);
        for(var c:reporte.navesPorEstado()) fila(texto,reporte,"Naves por estado",c.codigo(),c.cantidad(),null);
        for(var c:reporte.tramitesPorEstado()) fila(texto,reporte,"Trámites por estado",c.codigo(),c.cantidad(),c.diasPromedioEspera());
        for(var c:reporte.documentosPendientes()) fila(texto,reporte,"Documentos pendientes",c.codigo(),c.cantidad(),null);
        return ResponseEntity.ok().contentType(new MediaType("text","csv",StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("reporte-registro.csv").build().toString())
                .body(texto.toString().getBytes(StandardCharsets.UTF_8));
    }
    private static void fila(StringBuilder texto,ReportesService.Reporte r,String seccion,String categoria,long cantidad,Object promedio) {
        Object[] valores={r.desde(),r.hasta(),seccion,categoria,cantidad,promedio};
        for(int i=0;i<valores.length;i++) {
            if(i>0) texto.append(',');
            texto.append('"').append(valores[i]==null?"":valores[i].toString().replace("\"","\"\"")).append('"');
        }
        texto.append("\r\n");
    }
}
