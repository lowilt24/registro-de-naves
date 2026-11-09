package pa.amp.registro_naves.reporte;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.util.MultiValueMap;
import pa.amp.registro_naves.common.*;
import pa.amp.registro_naves.documento.TipoDocumento;
import pa.amp.registro_naves.nave.*;
import pa.amp.registro_naves.tramite.EstadoTramite;
import pa.amp.registro_naves.usuario.*;
import java.math.*;
import java.sql.Date;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class ReportesService {
    private static final ZoneId ZONA=ZoneId.of("America/Panama");
    private final JdbcTemplate jdbc;
    private final UsuarioRepository usuarios;
    public ReportesService(JdbcTemplate jdbc,UsuarioRepository usuarios) { this.jdbc=jdbc; this.usuarios=usuarios; }
    public record Conteo(String codigo,long cantidad) {}
    public record Espera(String codigo,long cantidad,BigDecimal diasPromedioEspera) {}
    public record Reporte(LocalDate desde,LocalDate hasta,LocalDate calculadoAl,
            List<Conteo> navesPorTipo,List<Conteo> navesPorEstado,List<Espera> tramitesPorEstado,
            List<Conteo> documentosPendientes) {}

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Reporte consultar(String correo,MultiValueMap<String,String> filtros) {
        Rol rol=usuarios.findByCorreoIgnoreCase(correo).map(Usuario::getRol).orElse(null);
        if(rol!=Rol.FUNCIONARIO_DGMM && rol!=Rol.AUDITOR)
            throw new AccesoDenegadoException("Los reportes están disponibles solo para la DGMM y el auditor.");
        for(var filtro:filtros.entrySet())
            if(!Set.of("desde","hasta").contains(filtro.getKey()) || filtro.getValue().size()!=1)
                throw new ValidacionCampoException("fechas","El filtro no se reconoce o está repetido.");
        LocalDate desde=fecha(filtros.getFirst("desde")),hasta=fecha(filtros.getFirst("hasta"));
        if(desde!=null && hasta!=null && desde.isAfter(hasta))
            throw new ValidacionCampoException("fechas","La fecha inicial no puede ser posterior a la final.");
        var parametros=new ArrayList<Object>();
        String rango="";
        if(desde!=null) { rango+=" AND %s >= ?"; parametros.add(desde.atStartOfDay(ZONA).toOffsetDateTime()); }
        if(hasta!=null) { rango+=" AND %s < ?"; parametros.add(hasta.plusDays(1).atStartOfDay(ZONA).toOffsetDateTime()); }
        String navesRango=rango(rango,"creado_en");
        var tipos=contar("SELECT tipo codigo,count(*) cantidad FROM nave WHERE 1=1"+navesRango+" GROUP BY tipo",parametros);
        var estados=contar("SELECT CASE WHEN estado='ABANDERADA' THEN 'REGISTRADA' ELSE estado END codigo, count(*) cantidad "
                +"FROM nave WHERE 1=1"+navesRango+" GROUP BY 1",parametros);
        LocalDate hoy=LocalDate.now(ZONA);
        var argumentos=new ArrayList<Object>(); argumentos.add(Date.valueOf(hoy)); argumentos.addAll(parametros);
        var esperas=new LinkedHashMap<String,Espera>();
        jdbc.query("SELECT estado, count(*) cantidad, avg(GREATEST(0,CAST(? AS date)-"
                +"CAST(actualizado_en AT TIME ZONE 'America/Panama' AS date))) promedio FROM tramites WHERE 1=1"
                +rango(rango,"actualizado_en")+" GROUP BY estado", rs -> {
                    var estado=EstadoTramite.valueOf(rs.getString("estado"));
                    esperas.put(estado.name(),new Espera(estado.name(),rs.getLong("cantidad"),estado.esFinal()?null:
                            rs.getBigDecimal("promedio").setScale(2,RoundingMode.HALF_UP)));
                },argumentos.toArray());
        var pendientes=contar("SELECT tipo codigo,count(*) cantidad FROM documentos WHERE vigente=true AND estado='PENDIENTE'"
                +rango(rango,"creado_en")+" GROUP BY tipo",parametros);
        return new Reporte(desde,hasta,hoy,grupos(TipoNave.values(),tipos),
                Arrays.stream(EstadoNave.values()).filter(e->e!=EstadoNave.ABANDERADA)
                        .map(e->new Conteo(e.name(),estados.getOrDefault(e.name(),0L))).toList(),
                Arrays.stream(EstadoTramite.values()).map(e->esperas.getOrDefault(e.name(),new Espera(e.name(),0,null))).toList(),
                grupos(TipoDocumento.values(),pendientes));
    }
    private static String rango(String plantilla,String columna) {
        // La columna es constante interna, nunca procede del filtro del usuario.
        return plantilla.replace("%s",columna);
    }
    private Map<String,Long> contar(String sql,List<Object> parametros) {
        var resultado=new HashMap<String,Long>();
        jdbc.query(sql,rs->{resultado.put(rs.getString("codigo"),rs.getLong("cantidad"));},parametros.toArray());
        return resultado;
    }
    private static List<Conteo> grupos(Enum<?>[] valores,Map<String,Long> cantidades) {
        return Arrays.stream(valores).map(e->new Conteo(e.name(),cantidades.getOrDefault(e.name(),0L))).toList();
    }
    private static LocalDate fecha(String valor) {
        if(valor==null) return null;
        try {
            LocalDate fecha=LocalDate.parse(valor);
            if(fecha.getYear()<1 || fecha.getYear()>9999) throw new DateTimeParseException("año",valor,0);
            return fecha;
        } catch(DateTimeParseException e) {
            throw new ValidacionCampoException("fechas","Use fechas válidas con formato AAAA-MM-DD (años 0001–9999).");
        }
    }
}
