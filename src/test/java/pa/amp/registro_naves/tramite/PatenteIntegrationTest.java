package pa.amp.registro_naves.tramite;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import pa.amp.registro_naves.patente.*;
import pa.amp.registro_naves.documento.*;
import pa.amp.registro_naves.nave.*;
import pa.amp.registro_naves.persona.*;
import pa.amp.registro_naves.usuario.*;
import pa.amp.registro_naves.common.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static pa.amp.registro_naves.patente.PatentePeticiones.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class PatenteIntegrationTest {
    static final String AGENTE="agente@navesitas.pa",DGMM="revisor@navesitas.pa",AUDITOR="auditor@navesitas.pa",ADMIN="admin@navesitas.pa";
    @Autowired MockMvc mvc;
    @Autowired PatenteService servicio;
    @Autowired NaveRepository naves;
    @Autowired UsuarioRepository usuarios;
    @Autowired TramiteRepository tramites;
    @Autowired HistorialEstadoRepository historial;
    @Autowired PropietarioRepository propietarios;
    @Autowired NavePropietarioRepository participaciones;
    @Autowired AgenteResidenteRepository agentes;
    @Autowired DocumentoRepository documentos;
    @Autowired AlmacenArchivos almacen;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;
    Nave nave;Tramite tramite;HistorialEstado acto;
    @BeforeEach void preparar() {
        nave=new Nave();nave.setNombre("Patente océano "+UUID.randomUUID());nave.setTipo(TipoNave.CARGA);nave.setServicio(TipoServicio.INTERNACIONAL);
        nave.setTonelajeBruto(new BigDecimal("499"));nave.setTonelajeNeto(new BigDecimal("250"));nave.setEslora(new BigDecimal("24"));nave.setManga(new BigDecimal("8"));nave.setPuntal(new BigDecimal("4"));
        nave.setAnioConstruccion(2020);nave.setLugarConstruccion("Panamá");nave.setMaterialCasco("Acero");nave.setTipoPropulsion("Diésel");nave.setPotenciaKw(new BigDecimal("750"));
        nave.setRegistradoPor(usuarios.findByCorreoIgnoreCase(AGENTE).orElseThrow());naves.saveAndFlush(nave);
        tramite=new Tramite(nave);tramite.cambiarA(EstadoTramite.APROBADA);tramites.saveAndFlush(tramite);
        acto=historial.saveAndFlush(new HistorialEstado(tramite,usuarios.findByCorreoIgnoreCase(DGMM).orElseThrow(),AccionTramite.APROBAR,EstadoTramite.EN_REVISION,EstadoTramite.APROBADA,null));
        Propietario p=new Propietario();p.setNombre("Naviera Muñoz");p.setTipo(TipoPersona.NATURAL);p.setNacionalidad("Panameña");p.setDomicilio("Panamá");propietarios.saveAndFlush(p);
        NavePropietario np=new NavePropietario();np.setNave(nave);np.setPropietario(p);np.setPorcentaje(new BigDecimal("100"));participaciones.saveAndFlush(np);
        AgenteResidente ar=new AgenteResidente();ar.setNave(nave);ar.setNombre("Firma Pacífico");ar.setIdoneidad("TEST-S10");agentes.saveAndFlush(ar);
    }
    String ruta() {return "/api/naves/"+nave.getId()+"/patente-provisional";}
    Map<String,Object> crear() {return servicio.crear(nave.getId(),new Crear(Procedencia.CONSTRUCCION),AGENTE);}
    @SuppressWarnings("unchecked") List<Map<String,Object>> requisitos() {return (List<Map<String,Object>>)servicio.consultar(nave.getId(),AGENTE).get("requisitos");}
    long id(Map<String,Object> m) {return ((Number)m.get("id")).longValue();}
    long envio() {return jdbc.queryForObject("SELECT ultimo_envio_id FROM pp_solicitudes WHERE nave_id=?",Long.class,nave.getId());}
    MockMultipartFile archivo(String contenido) {return new MockMultipartFile("archivo","../../prueba.pdf","application/pdf",("%PDF-1.7\n"+contenido).getBytes(StandardCharsets.UTF_8));}
    void cargarTodo() {for(var r:requisitos())servicio.cargar(nave.getId(),id(r),archivo("prueba "+id(r)),AGENTE);}
    void enviado() {
        crear();servicio.agregarRequisito(nave.getId(),new Requisito("Certificado técnico del caso",Clase.TECNICO,"Decisión académica DGMM",TipoDocumento.ITC),DGMM);
        servicio.confirmarRequisitos(nave.getId(),new Confirmacion(false,"Catálogo definido para este caso"),DGMM);cargarTodo();servicio.enviar(nave.getId(),AGENTE);
    }
    void revisarTodo() {for(var r:requisitos())servicio.revisar(nave.getId(),((Number)r.get("evidenciaId")).longValue(),new Revision(envio(),"APROBAR",null),DGMM);}
    @Test void creacionVinculaActoYNoModificaRegistro() {
        var datos=crear();assertThat(requisitos()).hasSize(4);assertThat(datos.get("solicitud")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT registro_aprobacion_id FROM pp_solicitudes WHERE nave_id=?",Long.class,nave.getId())).isEqualTo(acto.getId());
        assertThat(tramite.getEstado()).isEqualTo(EstadoTramite.APROBADA);assertThat(nave.getEstado()).isEqualTo(EstadoNave.REGISTRADA);
        assertThatThrownBy(this::crear).isInstanceOf(ReglaDeNegocioException.class);
    }
    @ParameterizedTest @EnumSource(Procedencia.class)
    void procedenciaDeterminaUnSoloDocumento(Procedencia p) {
        servicio.crear(nave.getId(),new Crear(p),AGENTE);assertThat(requisitos()).hasSize(4);
        String titulo=(String)requisitos().get(1).get("nombre");
        assertThat(titulo).contains(switch(p){case CONSTRUCCION->"construcción";case REGISTRO_ANTERIOR->"cancelación";case VENTA_JUDICIAL->"judicial";});
    }
    @Test void registroNoAprobadoYFaltaActoBloquean() {
        tramite.cambiarA(EstadoTramite.EN_REVISION);tramites.flush();assertThatThrownBy(this::crear).isInstanceOf(ReglaDeNegocioException.class);
        tramite.cambiarA(EstadoTramite.APROBADA);tramites.flush();historial.delete(acto);historial.flush();assertThatThrownBy(this::crear).isInstanceOf(ReglaDeNegocioException.class);
    }
    @ParameterizedTest @EnumSource(value=TipoServicio.class,names="INTERNACIONAL",mode=EnumSource.Mode.EXCLUDE)
    void otrosServiciosNoSeConfundenConExcepcionInterior(TipoServicio servicio) {
        nave.setServicio(servicio);naves.flush();assertThatThrownBy(this::crear).isInstanceOf(ReglaDeNegocioException.class);
    }
    @ParameterizedTest @ValueSource(strings={DGMM,AUDITOR,ADMIN})
    void soloAgenteDuenoCrea(String correo) throws Exception {
        mvc.perform(post(ruta()).with(user(correo)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"procedencia\":\"CONSTRUCCION\"}")).andExpect(status().isForbidden());
    }
    @Test void agenteAjenoNoConsultaNiListaNiDescarga() throws Exception {
        Usuario u=new Usuario();u.setCorreo("otro-s10@example.com");u.setNombreCompleto("Otro");u.setRol(Rol.AGENTE_NAVIERO);u.setPasswordHash("no-usado");usuarios.saveAndFlush(u);crear();
        mvc.perform(get(ruta()).with(user(u.getCorreo()))).andExpect(status().isForbidden());
        mvc.perform(get(ruta()+"/archivo").with(user(u.getCorreo()))).andExpect(status().isForbidden());
        assertThat(servicio.listar(u.getCorreo(),null)).isEmpty();
    }
    @Test void sinSesion401Validaciones400YCsrf403() throws Exception {
        mvc.perform(get(ruta())).andExpect(status().isUnauthorized());
        mvc.perform(post(ruta()).with(user(AGENTE)).with(csrf().useInvalidToken()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post(ruta()).with(user(AGENTE)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/patentes-provisionales?estado=INVALIDO").with(user(AUDITOR))).andExpect(status().isBadRequest());
    }
    @Test void auditorNoEjecutaNingunaMutacion() throws Exception {
        enviado();
        String[][] acciones={{"/enviar","{}"},{"/requisitos","{\"nombre\":\"Test\",\"clase\":\"TECNICO\",\"motivo\":\"Test\"}"},
            {"/requisitos/confirmar","{\"sinTecnicosAdicionales\":false,\"motivo\":\"Test\"}"},{"/aprobar","{\"envioId\":"+envio()+"}"},
            {"/devolver","{\"envioId\":"+envio()+",\"observacion\":\"Test\"}"},
            {"/evidencias/"+requisitos().getFirst().get("evidenciaId")+"/revision","{\"envioId\":"+envio()+",\"decision\":\"APROBAR\"}"}};
        for(var accion:acciones)mvc.perform(post(ruta()+accion[0]).with(user(AUDITOR)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(accion[1])).andExpect(status().isForbidden());
        mvc.perform(multipart(ruta()+"/requisitos/"+id(requisitos().getFirst())+"/archivo").file(archivo("x")).with(user(AUDITOR)).with(csrf())).andExpect(status().isForbidden());
        assertThat(servicio.consultar(nave.getId(),AUDITOR).get("acciones")).isEqualTo(List.of());
    }
    @Test void envioExigeCatalogoConfirmadoYTodosLosArchivos() {
        crear();cargarTodo();assertThatThrownBy(()->servicio.enviar(nave.getId(),AGENTE)).isInstanceOf(ReglaDeNegocioException.class);
        assertThatThrownBy(()->servicio.confirmarRequisitos(nave.getId(),new Confirmacion(false,"Sin definir"),DGMM)).isInstanceOf(ReglaDeNegocioException.class);
        servicio.confirmarRequisitos(nave.getId(),new Confirmacion(true,"Caso académico sin adicionales"),DGMM);
        servicio.agregarRequisito(nave.getId(),new Requisito("Adicional",Clase.ADICIONAL,"Caso concreto",null),DGMM);
        assertThatThrownBy(()->servicio.enviar(nave.getId(),AGENTE)).isInstanceOf(ReglaDeNegocioException.class);
        servicio.confirmarRequisitos(nave.getId(),new Confirmacion(true,"Confirmado"),DGMM);
        assertThatThrownBy(()->servicio.enviar(nave.getId(),AGENTE)).isInstanceOf(ReglaDeNegocioException.class).hasMessageContaining("Adicional");
    }
    @Test void formatosTamanoYNombreSeguro() {
        crear();long rid=id(requisitos().getFirst());
        assertThatThrownBy(()->servicio.cargar(nave.getId(),rid,new MockMultipartFile("archivo","x.pdf","application/pdf",new byte[0]),AGENTE)).isInstanceOf(ValidacionCampoException.class);
        assertThatThrownBy(()->servicio.cargar(nave.getId(),rid,new MockMultipartFile("archivo","x.pdf","application/pdf","texto".getBytes()),AGENTE)).isInstanceOf(FormatoNoSoportadoException.class);
        assertThatThrownBy(()->servicio.cargar(nave.getId(),rid,new MockMultipartFile("archivo","x.pdf","application/pdf",new byte[10485761]),AGENTE)).isInstanceOf(ArchivoDemasiadoGrandeException.class);
        servicio.cargar(nave.getId(),rid,archivo("ok"),AGENTE);servicio.cargar(nave.getId(),rid,archivo("corregido"),AGENTE);
        var r=requisitos().getFirst();assertThat(r.get("nombreArchivo")).isEqualTo("prueba.pdf");assertThat(r.get("version")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pp_evidencias WHERE requisito_id=?",Integer.class,rid)).isEqualTo(2);
    }
    @Test void referenciaExactaCopiaBytesYNoHeredaAprobacion() {
        crear();long rid=id(requisitos().getFirst());var archivo=archivo("original");var guardado=almacen.guardar(nave.getId(),archivo);
        Documento d=new Documento();d.setNave(nave);d.setTipo(TipoDocumento.ITC);d.setVersion(1);d.setNombreArchivo("itc.pdf");d.setRuta(guardado.rutaRelativa());d.setHashSha256(guardado.hashSha256());d.setTamanoBytes(guardado.tamanoBytes());d.setCreadoPor(nave.getRegistradoPor());documentos.saveAndFlush(d);
        try {
            assertThatThrownBy(()->servicio.referenciar(nave.getId(),rid,new Referencia(d.getId()),AGENTE)).isInstanceOf(ReglaDeNegocioException.class);
            servicio.permitirReutilizacion(nave.getId(),rid,new Reutilizacion(TipoDocumento.ITC,"Equivalencia explícita solo para prueba"),DGMM);
            servicio.referenciar(nave.getId(),rid,new Referencia(d.getId()),AGENTE);var r=requisitos().getFirst();
            assertThat(r.get("origenDocumentoId")).isEqualTo(d.getId());assertThat(r.get("decision")).isNull();
            almacen.borrarSilencioso(d.getRuta());
            assertThat(servicio.descargarEvidencia(nave.getId(),((Number)r.get("evidenciaId")).longValue(),AUDITOR).contenido()).isEqualTo(archivo.getBytes());
            servicio.permitirReutilizacion(nave.getId(),rid,new Reutilizacion(TipoDocumento.PODER_NOTARIAL,"Cambio de equivalencia"),DGMM);
            servicio.confirmarRequisitos(nave.getId(),new Confirmacion(true,"Caso prueba"),DGMM);
            for(var req:requisitos())if(id(req)!=rid)servicio.cargar(nave.getId(),id(req),archivo("otra"),AGENTE);
            assertThatThrownBy(()->servicio.enviar(nave.getId(),AGENTE)).hasMessageContaining("tipo autorizado");
        } catch(java.io.IOException e) {throw new java.io.UncheckedIOException(e);} finally {almacen.borrarSilencioso(d.getRuta());}
    }
    @Test void subsanacionConservaEnvioYRequiereNuevaEvidenciaYNuevaRevision() throws Exception {
        enviado();long anterior=envio();var observada=requisitos().getFirst();long rid=id(observada),eid=((Number)observada.get("evidenciaId")).longValue();
        assertThatThrownBy(()->servicio.revisar(nave.getId(),eid,new Revision(anterior,"OBSERVAR"," "),DGMM)).isInstanceOf(ValidacionCampoException.class);
        assertThatThrownBy(()->servicio.devolver(nave.getId(),new Devolucion(anterior,"Corrija"),DGMM)).isInstanceOf(ReglaDeNegocioException.class);
        servicio.revisar(nave.getId(),eid,new Revision(anterior,"OBSERVAR","Falta autenticación"),DGMM);
        assertThatThrownBy(()->servicio.revisar(nave.getId(),eid,new Revision(anterior,"APROBAR",null),DGMM)).isInstanceOf(ReglaDeNegocioException.class);
        servicio.devolver(nave.getId(),new Devolucion(anterior,"Adjunte copia autenticada"),DGMM);
        assertThatThrownBy(()->servicio.enviar(nave.getId(),AGENTE)).hasMessageContaining("corregir");
        assertThatThrownBy(()->servicio.cargar(nave.getId(),id(requisitos().get(1)),archivo("no"),AGENTE)).hasMessageContaining("Solo puede sustituir");
        servicio.cargar(nave.getId(),rid,archivo("autenticada"),AGENTE);servicio.enviar(nave.getId(),AGENTE);
        assertThat(envio()).isNotEqualTo(anterior);assertThat(requisitos()).allSatisfy(r->assertThat(r.get("decision")).isNull());
        assertThatThrownBy(()->servicio.aprobar(nave.getId(),new Contexto(anterior),DGMM)).hasMessageContaining("envío cambió");
        assertThatThrownBy(()->servicio.revisar(nave.getId(),eid,new Revision(anterior,"APROBAR",null),DGMM)).hasMessageContaining("envío cambió");
        assertThat(jdbc.queryForObject("SELECT evidencia_id FROM pp_envio_items WHERE envio_id=? AND requisito_id=?",Long.class,anterior,rid)).isEqualTo(eid);
        assertThat(servicio.descargarEvidencia(nave.getId(),eid,AUDITOR).contenido()).isEqualTo(archivo("prueba "+rid).getBytes());
    }
    @Test void aprobacionUnicaPdfConservaInstantaneaYHashTrasCambiosVivos() throws Exception {
        enviado();String nombre=nave.getNombre();
        assertThatThrownBy(()->servicio.descargarPdf(nave.getId(),AGENTE)).isInstanceOf(ReglaDeNegocioException.class);
        assertThatThrownBy(()->servicio.aprobar(nave.getId(),new Contexto(envio()),DGMM)).hasMessageContaining("todas las evidencias");
        assertThatThrownBy(()->servicio.cargar(nave.getId(),id(requisitos().getFirst()),archivo("x"),AGENTE)).isInstanceOf(ReglaDeNegocioException.class);
        revisarTodo();servicio.aprobar(nave.getId(),new Contexto(envio()),DGMM);servicio.aprobar(nave.getId(),new Contexto(envio()),DGMM);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pp_emisiones WHERE nave_id=?",Integer.class,nave.getId())).isEqualTo(1);
        nave.setNombre("Cambiado luego de envío");naves.flush();
        var a=servicio.descargarPdf(nave.getId(),AGENTE);var b=servicio.descargarPdf(nave.getId(),AUDITOR);
        assertThat(a.contenido()).isEqualTo(b.contenido());assertThat(a.hashSha256()).hasSize(64);
        try(var doc=Loader.loadPDF(a.contenido())) {
            assertThat(new PDFTextStripper().getText(doc)).contains(nombre,"Naviera Muñoz","100","Firma Pacífico","REGISTRADA","sin validez oficial","SIM-PP-").doesNotContain("Cambiado luego","ABANDERADA");
            assertThat(doc.getSignatureDictionaries()).isEmpty();
            doc.save("target/sprint10-patente-ejemplo.pdf");
            javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImageWithDPI(0,100),"png",new java.io.File("target/sprint10-patente-ejemplo.png"));
        }
        mvc.perform(get(ruta()+"/archivo").with(user(AUDITOR))).andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header().string("X-Content-SHA256",a.hashSha256())).andExpect(header().string("Cache-Control","no-store"));
        assertThat(tramite.getEstado()).isEqualTo(EstadoTramite.APROBADA);assertThat(nave.getEstado()).isEqualTo(EstadoNave.REGISTRADA);
    }
    @Test void falloPdfPermiteReintentarMismaEmision() {
        enviado();revisarTodo();servicio.aprobar(nave.getId(),new Contexto(envio()),DGMM);
        var e=jdbc.queryForMap("SELECT id,numero,datos_emitidos FROM pp_emisiones WHERE nave_id=?",nave.getId());
        jdbc.update("UPDATE pp_emisiones SET datos_emitidos=? WHERE id=?","No representable: "+Character.toString(0x1F6A2),e.get("id"));
        assertThatThrownBy(()->servicio.descargarPdf(nave.getId(),AGENTE)).isInstanceOf(ReglaDeNegocioException.class);
        assertThat(jdbc.queryForObject("SELECT contenido IS NULL FROM pp_emisiones WHERE id=?",Boolean.class,e.get("id"))).isTrue();
        jdbc.update("UPDATE pp_emisiones SET datos_emitidos=? WHERE id=?",e.get("datos_emitidos"),e.get("id"));
        assertThat(servicio.descargarPdf(nave.getId(),AGENTE).nombre()).isEqualTo(e.get("numero")+".pdf");
    }
    @Test void verificacionPublicaMinimaSinAccesoAlExpediente() throws Exception {
        enviado();revisarTodo();servicio.aprobar(nave.getId(),new Contexto(envio()),DGMM);
        UUID token=jdbc.queryForObject("SELECT verificacion_id FROM pp_emisiones WHERE nave_id=?",UUID.class,nave.getId());
        mvc.perform(get("/api/verificacion/patentes/"+token)).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.numero").exists()).andExpect(jsonPath("$.estado").value("APROBADA"))
            .andExpect(jsonPath("$.vigencia").value("Sin vencimiento definido"))
            .andExpect(jsonPath("$.solicitante").doesNotExist()).andExpect(jsonPath("$.propietarios").doesNotExist())
            .andExpect(jsonPath("$.contenido").doesNotExist()).andExpect(jsonPath("$.documentos").doesNotExist());
        mvc.perform(get("/api/verificacion/patentes/"+UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/api/verificacion/patentes/1")).andExpect(status().isBadRequest());
        mvc.perform(get(ruta()+"/archivo")).andExpect(status().isUnauthorized());
    }
    @Test void evidenciaAjena404HashAlterado409() throws Exception {
        crear();cargarTodo();long eid=((Number)requisitos().getFirst().get("evidenciaId")).longValue();
        mvc.perform(get(ruta()+"/evidencias/999999999/archivo").with(user(AUDITOR))).andExpect(status().isNotFound());
        jdbc.update("UPDATE pp_evidencias SET hash_sha256=? WHERE id=?","0".repeat(64),eid);
        assertThatThrownBy(()->servicio.descargarEvidencia(nave.getId(),eid,AUDITOR)).hasMessageContaining("hash");
    }
    @Test void historialNoTieneEdicionPublicaNiExponeContenido() throws Exception {
        enviado();
        mvc.perform(get(ruta()).with(user(AUDITOR))).andExpect(status().isOk()).andExpect(jsonPath("$.historial[0].actor").exists())
            .andExpect(jsonPath("$.requisitos[0].contenido").doesNotExist()).andExpect(jsonPath("$.documentosReutilizables[0].ruta").doesNotExist());
        mvc.perform(delete(ruta()+"/historial/1").with(user(DGMM)).with(csrf())).andExpect(status().isNotFound());
    }
    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void creacionAprobacionYPdfConcurrentesSonUnicos() throws Exception {
        try(var ejecutor=Executors.newFixedThreadPool(2)) {
            var inicio=new CountDownLatch(1);
            Callable<Boolean> crear=()->{inicio.await();try{crear();return true;}catch(ReglaDeNegocioException e){return false;}};
            var primera=ejecutor.submit(crear);var segunda=ejecutor.submit(crear);inicio.countDown();
            assertThat(List.of(primera.get(30,TimeUnit.SECONDS),segunda.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            servicio.confirmarRequisitos(nave.getId(),new Confirmacion(true,"Caso concurrente académico"),DGMM);cargarTodo();servicio.enviar(nave.getId(),AGENTE);revisarTodo();
            long eid=envio();var aprobar=new CountDownLatch(1);
            Callable<Map<String,Object>> decidir=()->{aprobar.await();return servicio.aprobar(nave.getId(),new Contexto(eid),DGMM);};
            var a=ejecutor.submit(decidir);var b=ejecutor.submit(decidir);aprobar.countDown();
            assertThat(a.get(30,TimeUnit.SECONDS).get("emision")).isEqualTo(b.get(30,TimeUnit.SECONDS).get("emision"));
            var pdfA=ejecutor.submit(()->servicio.descargarPdf(nave.getId(),AGENTE));var pdfB=ejecutor.submit(()->servicio.descargarPdf(nave.getId(),AUDITOR));
            assertThat(pdfA.get(30,TimeUnit.SECONDS).contenido()).isEqualTo(pdfB.get(30,TimeUnit.SECONDS).contenido());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM pp_emisiones WHERE nave_id=?",Integer.class,nave.getId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM pp_eventos e JOIN pp_solicitudes s ON s.id=e.solicitud_id WHERE s.nave_id=? AND accion='APROBAR_EMITIR'",Integer.class,nave.getId())).isEqualTo(1);
        } finally {limpiarConfirmado();}
    }
    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void falloAlGuardarHistorialRevierteEstadoYEmision() {
        String funcion="qa_pp_"+UUID.randomUUID().toString().replace("-","");
        try {
            enviado();revisarTodo();
            jdbc.execute("CREATE FUNCTION "+funcion+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.accion='APROBAR_EMITIR' THEN RAISE EXCEPTION 'fallo de prueba'; END IF; RETURN NEW; END $$");
            jdbc.execute("CREATE TRIGGER "+funcion+" BEFORE INSERT ON pp_eventos FOR EACH ROW EXECUTE FUNCTION "+funcion+"()");
            assertThatThrownBy(()->servicio.aprobar(nave.getId(),new Contexto(envio()),DGMM)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(jdbc.queryForObject("SELECT estado FROM pp_solicitudes WHERE nave_id=?",String.class,nave.getId())).isEqualTo("EN_REVISION");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM pp_emisiones WHERE nave_id=?",Integer.class,nave.getId())).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS "+funcion+" ON pp_eventos");jdbc.execute("DROP FUNCTION IF EXISTS "+funcion+"()");limpiarConfirmado();
        }
    }
    void limpiarConfirmado() {
        new TransactionTemplate(transacciones).executeWithoutResult(tx->{
            var solicitudes=jdbc.queryForList("SELECT id FROM pp_solicitudes WHERE nave_id=?",Long.class,nave.getId());
            for(long sid:solicitudes) {
                jdbc.update("UPDATE pp_solicitudes SET ultimo_envio_id=NULL WHERE id=?",sid);
                jdbc.update("DELETE FROM pp_eventos WHERE solicitud_id=?",sid);jdbc.update("DELETE FROM pp_emisiones WHERE solicitud_id=?",sid);
                jdbc.update("DELETE FROM pp_revisiones WHERE envio_id IN(SELECT id FROM pp_envios WHERE solicitud_id=?)",sid);
                jdbc.update("DELETE FROM pp_envio_items WHERE envio_id IN(SELECT id FROM pp_envios WHERE solicitud_id=?)",sid);
                jdbc.update("DELETE FROM pp_envios WHERE solicitud_id=?",sid);jdbc.update("DELETE FROM pp_evidencias WHERE solicitud_id=?",sid);
                jdbc.update("DELETE FROM pp_requisitos WHERE solicitud_id=?",sid);jdbc.update("DELETE FROM pp_solicitudes WHERE id=?",sid);
            }
            var propietariosIds=jdbc.queryForList("SELECT propietario_id FROM nave_propietario WHERE nave_id=?",Long.class,nave.getId());
            jdbc.update("DELETE FROM historial_estados WHERE tramite_id=?",tramite.getId());jdbc.update("DELETE FROM agentes_residentes WHERE nave_id=?",nave.getId());
            jdbc.update("DELETE FROM nave_propietario WHERE nave_id=?",nave.getId());jdbc.update("DELETE FROM tramites WHERE nave_id=?",nave.getId());
            jdbc.update("DELETE FROM nave WHERE id=?",nave.getId());for(long pid:propietariosIds)jdbc.update("DELETE FROM propietarios WHERE id=?",pid);
        });
    }
}
