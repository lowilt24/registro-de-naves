package pa.amp.registro_naves.patente;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pa.amp.registro_naves.common.*;
import pa.amp.registro_naves.documento.*;
import pa.amp.registro_naves.nave.*;
import pa.amp.registro_naves.persona.*;
import pa.amp.registro_naves.tramite.*;
import pa.amp.registro_naves.usuario.*;
import java.io.*;
import java.nio.file.Files;
import java.security.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static pa.amp.registro_naves.patente.PatentePeticiones.*;

/** Etapa independiente: cada envío, revisión y emisión conserva su propia instantánea. */
@Service
@Transactional
public class PatenteService {
    private final JdbcTemplate db;
    private final NaveRepository naves;
    private final UsuarioRepository usuarios;
    private final TramiteRepository tramites;
    private final HistorialEstadoRepository historial;
    private final NavePropietarioRepository propietarios;
    private final AgenteResidenteRepository agentes;
    private final AlmacenArchivos almacen;
    private final PatentePdf pdf;
    private static final int LIMITE = 10 * 1024 * 1024;
    private static final Set<String> ESTADOS = Set.of("BORRADOR", "EN_REVISION", "SUBSANACION", "APROBADA");
    private record Acceso(Nave nave, Usuario usuario) {}
    public record Archivo(String nombre, byte[] contenido, String hashSha256) {}

    public PatenteService(JdbcTemplate db, NaveRepository naves, UsuarioRepository usuarios,
            TramiteRepository tramites, HistorialEstadoRepository historial,
            NavePropietarioRepository propietarios, AgenteResidenteRepository agentes,
            AlmacenArchivos almacen, PatentePdf pdf) {
        this.db=db; this.naves=naves; this.usuarios=usuarios; this.tramites=tramites;
        this.historial=historial; this.propietarios=propietarios; this.agentes=agentes;
        this.almacen=almacen; this.pdf=pdf;
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> listar(String correo, String estado) {
        Usuario u=usuario(correo);
        if (u.getRol()!=Rol.AGENTE_NAVIERO && u.getRol()!=Rol.FUNCIONARIO_DGMM && u.getRol()!=Rol.AUDITOR)
            throw new AccesoDenegadoException("El rol no puede consultar patentes provisionales.");
        if (estado!=null && !ESTADOS.contains(estado)) throw new ValidacionCampoException("estado","Estado no válido.");
        String sql="""
            SELECT s.id,s.nave_id AS "naveId",n.nombre,s.estado,s.actualizado_en AS "actualizadoEn",e.numero
            FROM pp_solicitudes s JOIN nave n ON n.id=s.nave_id
            LEFT JOIN pp_emisiones e ON e.solicitud_id=s.id WHERE 1=1
            """;
        List<Object> args=new ArrayList<>();
        if(u.getRol()==Rol.AGENTE_NAVIERO) {sql+=" AND s.solicitante_id=?";args.add(u.getId());}
        if(estado!=null) {sql+=" AND s.estado=?";args.add(estado);}
        return filas(sql+" ORDER BY s.actualizado_en,s.id",args.toArray());
    }

    @Transactional(readOnly=true)
    public Map<String,Object> consultar(Long naveId,String correo) {return detalle(acceso(naveId,correo));}

    public Map<String,Object> crear(Long naveId,Crear peticion,String correo) {
        Acceso a=acceso(naveId,correo); rol(a,Rol.AGENTE_NAVIERO);
        db.queryForObject("SELECT id FROM nave WHERE id=? FOR UPDATE",Long.class,naveId);
        exigirRegistro(a.nave());
        if(!filas("SELECT id FROM pp_solicitudes WHERE nave_id=?",naveId).isEmpty())
            throw new ReglaDeNegocioException("Esta nave ya tiene una solicitud de patente provisional.");
        long sid=insertar("""
            INSERT INTO pp_solicitudes(nave_id,registro_aprobacion_id,solicitante_id,procedencia)
            VALUES(?,?,?,?) RETURNING id
            """,naveId,aprobacion(a.nave()).getId(),a.usuario().getId(),peticion.procedencia().name());
        base(sid,"DESIGNACION_RESIDENTE","Instrumento de designación del agente residente");
        base(sid,"PROCEDENCIA",switch(peticion.procedencia()) {
            case REGISTRO_ANTERIOR -> "Certificado de cancelación del registro anterior";
            case CONSTRUCCION -> "Certificado de construcción";
            case VENTA_JUDICIAL -> "Documento que acredita la venta judicial";
        });
        base(sid,"CUMPLIMIENTO","Evidencia de cumplimiento de requisitos DGMM");
        base(sid,"PAGO_SIMULADO","Comprobante de pago simulado (sin cobro real)");
        evento(a,sid,"CREAR",null,"BORRADOR",null,null,null,"Solicitud académica para servicio internacional.");
        return detalle(a);
    }

    public Map<String,Object> agregarRequisito(Long naveId,Requisito p,String correo) {
        Acceso a=acceso(naveId,correo);rol(a,Rol.FUNCIONARIO_DGMM);
        var s=bloquear(naveId);editable(s);
        long rid=insertar("""
            INSERT INTO pp_requisitos(solicitud_id,codigo,nombre,clase,motivo,tipo_origen)
            VALUES(?,?,?,?,?,?) RETURNING id
            """,id(s),"REQ_"+UUID.randomUUID(),limpio(p.nombre()),p.clase().name(),limpio(p.motivo()),
            p.tipoOrigen()==null?null:p.tipoOrigen().name());
        invalidar(id(s));evento(a,id(s),"DEFINIR_REQUISITO",null,null,rid,null,null,p.motivo());
        return detalle(a);
    }

    public Map<String,Object> permitirReutilizacion(Long naveId,Long rid,Reutilizacion p,String correo) {
        Acceso a=acceso(naveId,correo);rol(a,Rol.FUNCIONARIO_DGMM);
        var s=bloquear(naveId);editable(s);requisito(id(s),rid);corregible(s,rid);
        db.update("UPDATE pp_requisitos SET tipo_origen=? WHERE id=?",p.tipoOrigen().name(),rid);
        invalidar(id(s));evento(a,id(s),"PERMITIR_REUTILIZACION",null,null,rid,null,null,p.motivo());
        return detalle(a);
    }

    public Map<String,Object> confirmarRequisitos(Long naveId,Confirmacion p,String correo) {
        Acceso a=acceso(naveId,correo);rol(a,Rol.FUNCIONARIO_DGMM);var s=bloquear(naveId);editable(s);
        int tecnicos=db.queryForObject("SELECT count(*) FROM pp_requisitos WHERE solicitud_id=? AND clase='TECNICO'",Integer.class,id(s));
        if((tecnicos==0)!=p.sinTecnicosAdicionales())
            throw new ReglaDeNegocioException("Defina los requisitos técnicos o justifique expresamente que este caso académico no requiere técnicos adicionales.");
        db.update("""
            UPDATE pp_solicitudes SET requisitos_confirmados=true,requisitos_version=requisitos_version+1,
            confirmacion_motivo=?,actualizado_en=now() WHERE id=?
            """,limpio(p.motivo()),id(s));
        evento(a,id(s),"CONFIRMAR_REQUISITOS",null,null,null,null,null,p.motivo());return detalle(a);
    }

    public Map<String,Object> cargar(Long naveId,Long rid,MultipartFile archivo,String correo) {
        Acceso a=acceso(naveId,correo);rol(a,Rol.AGENTE_NAVIERO);var s=bloquear(naveId);
        editable(s);requisito(id(s),rid);corregible(s,rid);
        if(archivo.isEmpty())throw new ValidacionCampoException("archivo","Seleccione un PDF.");
        if(archivo.getSize()>LIMITE)throw new ArchivoDemasiadoGrandeException("Máximo 10 MB por PDF.");
        try(var entrada=archivo.getInputStream()) {
            byte[] bytes=entrada.readNBytes(LIMITE+1);validarPdf(bytes);
            guardarEvidencia(a,s,rid,null,DocumentoService.limpiarNombre(archivo.getOriginalFilename()),bytes);
        } catch(IOException e) {throw new UncheckedIOException(e);}
        return detalle(a);
    }

    public Map<String,Object> referenciar(Long naveId,Long rid,Referencia p,String correo) {
        Acceso a=acceso(naveId,correo);rol(a,Rol.AGENTE_NAVIERO);var s=bloquear(naveId);
        editable(s);var r=requisito(id(s),rid);corregible(s,rid);
        var d=uno("SELECT * FROM documentos WHERE id=? AND nave_id=?",p.documentoId(),naveId);
        if(r.get("tipo_origen")==null || !Objects.equals(r.get("tipo_origen"),d.get("tipo")))
            throw new ReglaDeNegocioException("La DGMM debe autorizar el tipo de documento adecuado para este requisito.");
        try {
            var ruta=almacen.ubicar((String)d.get("ruta"));
            if(!Files.isRegularFile(ruta))throw new ReglaDeNegocioException("La versión original no está disponible.");
            if(Files.size(ruta)>LIMITE)throw new ArchivoDemasiadoGrandeException("Máximo 10 MB por PDF.");
            byte[] bytes=Files.readAllBytes(ruta);validarPdf(bytes);integridad(bytes,(String)d.get("hash_sha256"));
            guardarEvidencia(a,s,rid,p.documentoId(),(String)d.get("nombre_archivo"),bytes);
        } catch(IOException e) {throw new UncheckedIOException(e);}
        return detalle(a);
    }

    public Map<String,Object> enviar(Long naveId,String correo) {
        Acceso a=acceso(naveId,correo);rol(a,Rol.AGENTE_NAVIERO);var s=bloquear(naveId);editable(s);exigirRegistro(a.nave());
        if(!Boolean.TRUE.equals(s.get("requisitos_confirmados")))
            throw new ReglaDeNegocioException("La DGMM debe definir y confirmar los requisitos antes del envío.");
        var requisitos=requisitosActuales(s);
        for(var r:requisitos) {
            if(r.get("evidenciaId")==null)throw new ReglaDeNegocioException("Falta el PDF de: "+r.get("nombre"));
            if(r.get("origenDocumentoId")!=null && !Objects.equals(r.get("tipoOrigen"),
                    uno("SELECT tipo FROM documentos WHERE id=?",r.get("origenDocumentoId")).get("tipo")))
                throw new ReglaDeNegocioException("La versión referenciada ya no corresponde al tipo autorizado: "+r.get("nombre"));
            if("SUBSANACION".equals(s.get("estado"))) {
                var anteriores=filas("""
                    SELECT i.evidencia_id FROM pp_envio_items i JOIN pp_revisiones v
                    ON v.envio_id=i.envio_id AND v.evidencia_id=i.evidencia_id
                    WHERE i.envio_id=? AND i.requisito_id=? AND v.decision='OBSERVADA'
                    """,s.get("ultimo_envio_id"),id(r));
                if(!anteriores.isEmpty() && Objects.equals(anteriores.getFirst().get("evidencia_id"),r.get("evidenciaId")))
                    throw new ReglaDeNegocioException("Debe corregir el archivo observado: "+r.get("nombre"));
            }
        }
        long eid=insertar("""
            INSERT INTO pp_envios(solicitud_id,numero,requisitos_version,confirmacion_motivo,datos_nave,enviado_por)
            VALUES(?,(SELECT count(*)+1 FROM pp_envios WHERE solicitud_id=?),?,?,?,?) RETURNING id
            """,id(s),id(s),s.get("requisitos_version"),s.get("confirmacion_motivo"),datosNave(a.nave()),a.usuario().getId());
        for(var r:requisitos) db.update("""
            INSERT INTO pp_envio_items(envio_id,requisito_id,evidencia_id,nombre,clase,motivo,tipo_origen)
            VALUES(?,?,?,?,?,?,?)
            """,eid,id(r),r.get("evidenciaId"),r.get("nombre"),r.get("clase"),r.get("motivo"),r.get("tipoOrigen"));
        db.update("UPDATE pp_solicitudes SET estado='EN_REVISION',ultimo_envio_id=?,actualizado_en=now() WHERE id=?",eid,id(s));
        evento(a,id(s),"ENVIAR",(String)s.get("estado"),"EN_REVISION",null,null,eid,null);return detalle(a);
    }

    public Map<String,Object> revisar(Long naveId,Long evidenciaId,Revision p,String correo) {
        Acceso a=acceso(naveId,correo);rol(a,Rol.FUNCIONARIO_DGMM);var s=bloquear(naveId);exigirEstado(s,"EN_REVISION");
        envioActual(s,p.envioId());
        uno("SELECT evidencia_id FROM pp_envio_items WHERE envio_id=? AND evidencia_id=?",s.get("ultimo_envio_id"),evidenciaId);
        if(!filas("SELECT id FROM pp_revisiones WHERE envio_id=? AND evidencia_id=?",s.get("ultimo_envio_id"),evidenciaId).isEmpty())
            throw new ReglaDeNegocioException("Esta evidencia ya fue revisada en el envío actual.");
        String decision=p.decision().trim().toUpperCase(Locale.ROOT);
        if(!Set.of("APROBAR","OBSERVAR").contains(decision))throw new ValidacionCampoException("decision","Use APROBAR u OBSERVAR.");
        String observacion=limpio(p.observacion());
        if("OBSERVAR".equals(decision) && observacion.isBlank())throw new ValidacionCampoException("observacion","Explique la corrección requerida.");
        db.update("""
            INSERT INTO pp_revisiones(envio_id,evidencia_id,decision,observacion,funcionario_id,funcionario_nombre)
            VALUES(?,?,?,?,?,?)
            """,s.get("ultimo_envio_id"),evidenciaId,"APROBAR".equals(decision)?"APROBADA":"OBSERVADA",observacion,
            a.usuario().getId(),a.usuario().getNombreCompleto());
        evento(a,id(s),"REVISAR_"+decision,null,null,null,evidenciaId,s.get("ultimo_envio_id"),observacion);return detalle(a);
    }

    public Map<String,Object> devolver(Long naveId,Devolucion p,String correo) {
        Acceso a=acceso(naveId,correo);rol(a,Rol.FUNCIONARIO_DGMM);var s=bloquear(naveId);exigirEstado(s,"EN_REVISION");
        envioActual(s,p.envioId());
        if(db.queryForObject("SELECT count(*) FROM pp_revisiones WHERE envio_id=? AND decision='OBSERVADA'",Integer.class,s.get("ultimo_envio_id"))==0)
            throw new ReglaDeNegocioException("Observe al menos una evidencia e indique qué se debe corregir.");
        db.update("UPDATE pp_solicitudes SET estado='SUBSANACION',actualizado_en=now() WHERE id=?",id(s));
        evento(a,id(s),"DEVOLVER","EN_REVISION","SUBSANACION",null,null,s.get("ultimo_envio_id"),p.observacion());return detalle(a);
    }

    public Map<String,Object> aprobar(Long naveId,Contexto p,String correo) {
        Acceso a=acceso(naveId,correo);rol(a,Rol.FUNCIONARIO_DGMM);var s=bloquear(naveId);
        envioActual(s,p.envioId());
        if("APROBADA".equals(s.get("estado")))return detalle(a);
        exigirEstado(s,"EN_REVISION");exigirRegistro(a.nave());
        if(db.queryForObject("""
            SELECT count(*) FROM pp_envio_items i LEFT JOIN pp_revisiones v
            ON v.envio_id=i.envio_id AND v.evidencia_id=i.evidencia_id
            WHERE i.envio_id=? AND v.decision IS DISTINCT FROM 'APROBADA'
            """,Integer.class,s.get("ultimo_envio_id"))>0)
            throw new ReglaDeNegocioException("Debe aprobar todas las evidencias del envío actual.");
        OffsetDateTime fecha=OffsetDateTime.now(ZoneId.of("America/Panama"));
        String numero="SIM-PP-"+fecha.getYear()+"-"+String.format(Locale.ROOT,"%06d",id(s));
        String datos="Simulación académica de patente provisional — sin validez oficial\nNúmero: "+numero
            +"\nSolicitud: "+id(s)+" | Nave registrada: "+naveId+"\nFecha de emisión: "+fecha
            +"\nFuncionario: "+limpio(a.usuario().getNombreCompleto())+"\nActo de aprobación del registro: "+s.get("registro_aprobacion_id")
            +"\nEnvío aprobado: "+s.get("ultimo_envio_id")+"\n"
            +uno("SELECT datos_nave FROM pp_envios WHERE id=?",s.get("ultimo_envio_id")).get("datos_nave")
            +"\nSin firma oficial. Sin vencimiento definido. No habilita navegación real.";
        db.update("""
            INSERT INTO pp_emisiones(solicitud_id,nave_id,envio_id,numero,emitido_en,funcionario_id,funcionario_nombre,datos_emitidos)
            VALUES(?,?,?,?,?,?,?,?)
            """,id(s),naveId,s.get("ultimo_envio_id"),numero,fecha,a.usuario().getId(),a.usuario().getNombreCompleto(),datos);
        db.update("UPDATE pp_solicitudes SET estado='APROBADA',actualizado_en=now() WHERE id=?",id(s));
        evento(a,id(s),"APROBAR_EMITIR","EN_REVISION","APROBADA",null,null,s.get("ultimo_envio_id"),numero);return detalle(a);
    }

    public Archivo descargarPdf(Long naveId,String correo) {
        Acceso a=acceso(naveId,correo);var s=bloquear(naveId);exigirEstado(s,"APROBADA");
        var e=uno("SELECT * FROM pp_emisiones WHERE solicitud_id=?",id(s));
        byte[] contenido=(byte[])e.get("contenido");String hash=(String)e.get("hash_sha256");
        if(contenido==null) {
            contenido=pdf.generar((String)e.get("numero"),(String)e.get("datos_emitidos"));hash=hash(contenido);
            db.update("UPDATE pp_emisiones SET contenido=?,hash_sha256=? WHERE id=?",contenido,hash,id(e));
        }
        integridad(contenido,hash);return new Archivo(e.get("numero")+".pdf",contenido,hash);
    }

    @Transactional(readOnly=true)
    public Archivo descargarEvidencia(Long naveId,Long evidenciaId,String correo) {
        acceso(naveId,correo);
        var e=uno("""
            SELECT e.* FROM pp_evidencias e JOIN pp_solicitudes s ON s.id=e.solicitud_id
            WHERE s.nave_id=? AND e.id=?
            """,naveId,evidenciaId);
        byte[] contenido=(byte[])e.get("contenido");String hash=(String)e.get("hash_sha256");
        integridad(contenido,hash);return new Archivo((String)e.get("nombre_archivo"),contenido,hash);
    }

    private Map<String,Object> detalle(Acceso a) {
        Map<String,Object> d=new LinkedHashMap<>();String motivo=motivoInhabilitada(a.nave());
        d.put("naveId",a.nave().getId());d.put("nombreNave",a.nave().getNombre());d.put("rol",a.usuario().getRol());
        d.put("habilitada",motivo==null);d.put("motivo",motivo);
        var solicitudes=filas("""
            SELECT id,estado,procedencia,requisitos_version AS "requisitosVersion",requisitos_confirmados AS "requisitosConfirmados",
            confirmacion_motivo AS "confirmacionMotivo",ultimo_envio_id AS "ultimoEnvioId",creado_en AS "creadoEn",actualizado_en AS "actualizadoEn"
            FROM pp_solicitudes WHERE nave_id=?
            """,a.nave().getId());
        if(solicitudes.isEmpty()) {
            d.put("solicitud",null);d.put("acciones",a.usuario().getRol()==Rol.AGENTE_NAVIERO && motivo==null?List.of("CREAR"):List.of());return d;
        }
        var s=uno("SELECT * FROM pp_solicitudes WHERE nave_id=?",a.nave().getId());
        d.put("solicitud",solicitudes.getFirst());d.put("requisitos",requisitosActuales(s));
        var envios=filas("""
            SELECT id,numero,requisitos_version AS "requisitosVersion",confirmacion_motivo AS "confirmacionMotivo",
            datos_nave AS "datosNave",enviado_en AS fecha FROM pp_envios WHERE solicitud_id=? ORDER BY numero
            """,id(s));
        for(var e:envios) e.put("evidencias",filas("""
            SELECT i.requisito_id AS "requisitoId",i.evidencia_id AS "evidenciaId",i.nombre,i.clase,i.motivo,i.tipo_origen AS "tipoOrigen",
            e.version,e.nombre_archivo AS "nombreArchivo",e.hash_sha256 AS "hashSha256",e.origen_documento_id AS "origenDocumentoId",
            v.decision,v.observacion,v.funcionario_nombre AS funcionario,v.revisado_en AS "revisadoEn"
            FROM pp_envio_items i JOIN pp_evidencias e ON e.id=i.evidencia_id
            LEFT JOIN pp_revisiones v ON v.envio_id=i.envio_id AND v.evidencia_id=i.evidencia_id
            WHERE i.envio_id=? ORDER BY i.requisito_id
            """,id(e)));
        d.put("envios",envios);
        d.put("historial",filas("""
            SELECT id,actor_nombre AS actor,actor_rol AS rol,accion,fecha,estado_anterior AS "estadoAnterior",estado_nuevo AS "estadoNuevo",
            requisito_id AS "requisitoId",evidencia_id AS "evidenciaId",envio_id AS "envioId",observacion
            FROM pp_eventos WHERE solicitud_id=? ORDER BY fecha,id
            """,id(s)));
        var emisiones=filas("""
            SELECT numero,emitido_en AS fecha,funcionario_nombre AS funcionario,datos_emitidos AS "datosEmitidos",hash_sha256 AS "hashSha256"
            FROM pp_emisiones WHERE solicitud_id=?
            """,id(s));
        d.put("emision",emisiones.isEmpty()?null:emisiones.getFirst());
        d.put("documentosReutilizables",filas("""
            SELECT id,tipo,version,nombre_archivo AS "nombreArchivo",hash_sha256 AS "hashSha256",estado
            FROM documentos WHERE nave_id=? ORDER BY tipo,version DESC
            """,a.nave().getId()));
        List<String> acciones=new ArrayList<>();String estado=(String)s.get("estado");
        boolean editable=Set.of("BORRADOR","SUBSANACION").contains(estado);
        if(a.usuario().getRol()==Rol.AGENTE_NAVIERO && editable)acciones.addAll(List.of("CARGAR","REFERENCIAR","ENVIAR"));
        if(a.usuario().getRol()==Rol.FUNCIONARIO_DGMM) {
            if(editable)acciones.addAll(List.of("CONFIGURAR","CONFIRMAR_REQUISITOS"));
            if("EN_REVISION".equals(estado))acciones.addAll(List.of("REVISAR","DEVOLVER","APROBAR"));
        }
        if("APROBADA".equals(estado))acciones.add("DESCARGAR_PDF");d.put("acciones",acciones);return d;
    }

    private List<Map<String,Object>> requisitosActuales(Map<String,Object> s) {
        return filas("""
            SELECT r.id,r.codigo,r.nombre,r.clase,r.motivo,r.tipo_origen AS "tipoOrigen",
            e.id AS "evidenciaId",e.version,e.nombre_archivo AS "nombreArchivo",e.hash_sha256 AS "hashSha256",
            e.origen_documento_id AS "origenDocumentoId",v.decision,v.observacion
            FROM pp_requisitos r LEFT JOIN LATERAL
            (SELECT * FROM pp_evidencias WHERE requisito_id=r.id ORDER BY version DESC LIMIT 1) e ON true
            LEFT JOIN pp_revisiones v ON v.envio_id=? AND v.evidencia_id=e.id
            WHERE r.solicitud_id=? ORDER BY r.id
            """,s.get("ultimo_envio_id"),id(s));
    }
    private void guardarEvidencia(Acceso a,Map<String,Object> s,long rid,Long origen,String nombre,byte[] bytes) {
        long eid=insertar("""
            INSERT INTO pp_evidencias(solicitud_id,requisito_id,version,origen_documento_id,nombre_archivo,contenido,hash_sha256,tamano_bytes,creado_por)
            VALUES(?,?,(SELECT coalesce(max(version),0)+1 FROM pp_evidencias WHERE requisito_id=?),?,?,?,?,?,?) RETURNING id
            """,id(s),rid,rid,origen,DocumentoService.limpiarNombre(nombre),bytes,hash(bytes),bytes.length,a.usuario().getId());
        db.update("UPDATE pp_solicitudes SET actualizado_en=now() WHERE id=?",id(s));
        evento(a,id(s),origen==null?"CARGAR_ARCHIVO":"REFERENCIAR_VERSION",null,null,rid,eid,null,
            origen==null?null:"Versión exacta de documento HU-07: "+origen+"; requiere nueva revisión.");
    }
    private void corregible(Map<String,Object> s,long rid) {
        if(!"SUBSANACION".equals(s.get("estado")))return;
        var prev=filas("""
            SELECT v.decision FROM pp_envio_items i LEFT JOIN pp_revisiones v
            ON v.envio_id=i.envio_id AND v.evidencia_id=i.evidencia_id WHERE i.envio_id=? AND i.requisito_id=?
            """,s.get("ultimo_envio_id"),rid);
        if(!prev.isEmpty() && !"OBSERVADA".equals(prev.getFirst().get("decision")))
            throw new ReglaDeNegocioException("Solo puede sustituir evidencias observadas o cargar nuevos requisitos.");
    }
    private void base(long sid,String codigo,String nombre) {
        db.update("INSERT INTO pp_requisitos(solicitud_id,codigo,nombre,clase,motivo) VALUES(?,?,?,'BASE',?)",
            sid,codigo,nombre,"Requisito base de la simulación académica internacional. Autenticación y suficiencia sujetas a revisión DGMM.");
    }
    private void invalidar(long sid) {
        db.update("UPDATE pp_solicitudes SET requisitos_confirmados=false,requisitos_version=requisitos_version+1,actualizado_en=now() WHERE id=?",sid);
    }
    private Map<String,Object> bloquear(long naveId) {
        var lista=filas("SELECT * FROM pp_solicitudes WHERE nave_id=? FOR UPDATE",naveId);
        if(lista.isEmpty())throw new ReglaDeNegocioException("La nave no tiene una solicitud de patente provisional.");return lista.getFirst();
    }
    private Map<String,Object> requisito(long sid,long rid) {return uno("SELECT * FROM pp_requisitos WHERE solicitud_id=? AND id=?",sid,rid);}
    private Usuario usuario(String correo) {return usuarios.findByCorreoIgnoreCase(correo).orElseThrow(()->new AccesoDenegadoException("Usuario no autorizado."));}
    private Acceso acceso(Long naveId,String correo) {
        Nave n=naves.findById(naveId).orElseThrow(()->new RecursoNoEncontradoException("Nave no encontrada."));Usuario u=usuario(correo);
        if(u.getRol()!=Rol.FUNCIONARIO_DGMM && u.getRol()!=Rol.AUDITOR &&
            !(u.getRol()==Rol.AGENTE_NAVIERO && Objects.equals(n.getRegistradoPor().getId(),u.getId())))
            throw new AccesoDenegadoException("No puede acceder a la patente provisional de esta nave.");
        return new Acceso(n,u);
    }
    private void rol(Acceso a,Rol rol) {if(a.usuario().getRol()!=rol)throw new AccesoDenegadoException("Esta acción requiere el rol "+rol+".");}
    private String motivoInhabilitada(Nave n) {
        if(n.getEstado()!=EstadoNave.REGISTRADA)return "La nave debe conservar su estado Registrada.";
        if(n.getServicio()!=TipoServicio.INTERNACIONAL)return "Esta versión académica solo contempla servicio internacional.";
        var t=tramites.findByNaveId(n.getId());
        if(t.isEmpty() || t.get().getEstado()!=EstadoTramite.APROBADA)return "El trámite de registro debe estar aprobado.";
        if(historial.lineaDeTiempo(t.get().getId()).stream().noneMatch(h->h.getAccion()==AccionTramite.APROBAR))return "Falta el acto de aprobación del registro.";
        return null;
    }
    private void exigirRegistro(Nave n) {String motivo=motivoInhabilitada(n);if(motivo!=null)throw new ReglaDeNegocioException(motivo);}
    private HistorialEstado aprobacion(Nave n) {
        return historial.lineaDeTiempo(tramites.findByNaveId(n.getId()).orElseThrow().getId()).stream()
            .filter(h->h.getAccion()==AccionTramite.APROBAR).reduce((a,b)->b).orElseThrow();
    }
    private String datosNave(Nave n) {
        var dueños=propietarios.findByNaveIdOrderByIdAsc(n.getId());
        var agente=agentes.findByNaveIdAndVigenteTrue(n.getId());
        if(dueños.isEmpty() || agente.isEmpty())throw new ReglaDeNegocioException("El expediente debe tener propietarios y agente residente vigente.");
        StringBuilder b=new StringBuilder("Registro de nave: "+n.getId()+"\nNombre: "+limpio(n.getNombre())
            +"\nTipo: "+n.getTipo()+" | Servicio: "+n.getServicio()+"\nEstado de nave: REGISTRADA"
            +"\nTonelaje bruto: "+n.getTonelajeBruto()+" | Neto: "+n.getTonelajeNeto()
            +"\nEslora: "+n.getEslora()+" m | Manga: "+n.getManga()+" m | Puntal: "+n.getPuntal()+" m"
            +"\nConstrucción: "+n.getAnioConstruccion()+" | "+limpio(n.getLugarConstruccion())
            +"\nCasco: "+limpio(n.getMaterialCasco())+" | Propulsión: "+limpio(n.getTipoPropulsion())+" | Potencia: "+n.getPotenciaKw()+" kW\nPropietarios:");
        dueños.forEach(p->b.append("\n- ").append(limpio(p.getPropietario().getNombre())).append(" | ").append(p.getPorcentaje()).append(" %"));
        b.append("\nAgente residente: ").append(limpio(agente.get().getNombre())).append(" | Idoneidad: ").append(limpio(agente.get().getIdoneidad()));return b.toString();
    }
    private void evento(Acceso a,long sid,String accion,String anterior,String nuevo,Object rid,Object eid,Object envio,String observacion) {
        db.update("""
            INSERT INTO pp_eventos(solicitud_id,actor_id,actor_nombre,actor_rol,accion,estado_anterior,estado_nuevo,requisito_id,evidencia_id,envio_id,observacion)
            VALUES(?,?,?,?,?,?,?,?,?,?,?)
            """,sid,a.usuario().getId(),a.usuario().getNombreCompleto(),a.usuario().getRol().name(),accion,anterior,nuevo,rid,eid,envio,limpio(observacion));
    }
    private static void editable(Map<String,Object> s) {
        if(!Set.of("BORRADOR","SUBSANACION").contains(s.get("estado")))throw new ReglaDeNegocioException("La solicitud ya no admite cambios en este estado.");
    }
    private static void exigirEstado(Map<String,Object> s,String estado) {
        if(!estado.equals(s.get("estado")))throw new ReglaDeNegocioException("La acción requiere la solicitud en "+estado+". Actualice la pantalla.");
    }
    private static void envioActual(Map<String,Object> s,Long envioId) {
        if(!Objects.equals(s.get("ultimo_envio_id"),envioId))throw new ReglaDeNegocioException("El envío cambió. Actualice la pantalla antes de decidir.");
    }
    private long insertar(String sql,Object... args) {return Objects.requireNonNull(db.queryForObject(sql,Long.class,args));}
    private Map<String,Object> uno(String sql,Object... args) {
        var lista=filas(sql,args);if(lista.isEmpty())throw new RecursoNoEncontradoException("Recurso no encontrado en esta solicitud.");return lista.getFirst();
    }
    private List<Map<String,Object>> filas(String sql,Object... args) {
        var lista=db.queryForList(sql,args);lista.forEach(m->m.replaceAll((k,v)->v instanceof Timestamp t?t.toInstant().toString():v instanceof OffsetDateTime f?f.toString():v));return lista;
    }
    private static long id(Map<String,Object> m) {return ((Number)m.get("id")).longValue();}
    private static String limpio(String texto) {return texto==null?"":texto.replaceAll("[\\p{Cntrl}]"," ").trim();}
    private static void validarPdf(byte[] bytes) {
        if(bytes.length>LIMITE)throw new ArchivoDemasiadoGrandeException("Máximo 10 MB por PDF.");
        if(bytes.length<5 || !Arrays.equals(Arrays.copyOf(bytes,5),new byte[]{37,80,68,70,45}))throw new FormatoNoSoportadoException("El contenido debe ser PDF.");
    }
    private static String hash(byte[] bytes) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
    private static void integridad(byte[] bytes,String esperado) {if(!hash(bytes).equals(esperado))throw new ReglaDeNegocioException("El hash no coincide. No se descargó el archivo.");}
}
