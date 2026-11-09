package pa.amp.registro_naves.tramite;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pa.amp.registro_naves.aviso.AvisoAlAgente;
import pa.amp.registro_naves.documento.*;
import pa.amp.registro_naves.nave.*;
import pa.amp.registro_naves.usuario.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Transacciones reales: las pruebas con rollback automatico no ejecutan AFTER_COMMIT. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AvisosAlAgenteIntegrationTest {
    static final String AGENTE="agente@navesitas.pa", DGMM="revisor@navesitas.pa";
    @Autowired MockMvc mvc;
    @Autowired NaveRepository naves;
    @Autowired TramiteRepository tramites;
    @Autowired HistorialEstadoRepository historial;
    @Autowired DocumentoRepository documentos;
    @Autowired UsuarioRepository usuarios;
    @Autowired TramiteService servicio;
    @Autowired ApplicationEventPublisher eventos;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean NotificadorCorreo notificador;
    Long naveId, documentoId;
    String nombreNave;
    TransactionTemplate tx;

    @BeforeEach void preparar() {
        tx=new TransactionTemplate(manager);
        tx.executeWithoutResult(status -> {
            var agente=usuarios.findByCorreoIgnoreCase(AGENTE).orElseThrow();
            var dgmm=usuarios.findByCorreoIgnoreCase(DGMM).orElseThrow();
            var nave=new Nave(); nombreNave="Avisos del Océano " + UUID.randomUUID();
            nave.setNombre(nombreNave); nave.setTipo(TipoNave.CARGA); nave.setServicio(TipoServicio.INTERNACIONAL);
            nave.setTonelajeBruto(new BigDecimal("499")); nave.setTonelajeNeto(new BigDecimal("250"));
            nave.setEslora(new BigDecimal("23.99")); nave.setManga(new BigDecimal("8")); nave.setPuntal(new BigDecimal("4"));
            nave.setAnioConstruccion(2020); nave.setLugarConstruccion("Panamá"); nave.setMaterialCasco("Acero");
            nave.setTipoPropulsion("Diésel"); nave.setPotenciaKw(new BigDecimal("750")); nave.setRegistradoPor(agente);
            naves.saveAndFlush(nave); naveId=nave.getId();
            var tramite=new Tramite(nave); tramite.cambiarA(EstadoTramite.EN_REVISION); tramites.saveAndFlush(tramite);
            historial.saveAndFlush(new HistorialEstado(tramite,dgmm,AccionTramite.ADMITIR,
                    EstadoTramite.SOLICITUD_PRESENTADA,EstadoTramite.EN_REVISION,null));
            for(var tipo:new TipoDocumento[]{TipoDocumento.CERTIFICADO_PROPIEDAD,TipoDocumento.PODER_NOTARIAL,TipoDocumento.ITC}) {
                var d=new Documento(); d.setNave(nave); d.setTipo(tipo); d.setNombreArchivo("prueba.pdf");
                d.setRuta(UUID.randomUUID()+".pdf"); d.setHashSha256("0".repeat(64)); d.setTamanoBytes(20L);
                d.setVersion(1); d.setCreadoPor(agente);
                if(tipo!=TipoDocumento.ITC) d.marcarRevisado(EstadoDocumento.APROBADO,null,dgmm,OffsetDateTime.now());
                documentos.saveAndFlush(d); if(tipo==TipoDocumento.ITC) documentoId=d.getId();
            }
        });
    }
    @AfterEach void limpiar() {
        if(naveId==null) return;
        tx.executeWithoutResult(status -> {
            jdbc.update("delete from documentos where nave_id=?",naveId);
            jdbc.update("delete from historial_estados where tramite_id in (select id from tramites where nave_id=?)",naveId);
            jdbc.update("delete from tramites where nave_id=?",naveId);
            jdbc.update("delete from nave where id=?",naveId);
        });
    }
    String base() { return "/api/naves/"+naveId; }
    void estado(EstadoTramite estado) {
        tx.executeWithoutResult(s -> { var t=tramites.findByNaveId(naveId).orElseThrow(); t.cambiarA(estado); tramites.flush(); });
    }
    String cuerpo(String accion,String observacion) {
        return "{\"accion\":\""+accion+"\",\"observacion\":\""+observacion+"\"}";
    }

    @ParameterizedTest @CsvSource({"ADMITIR,SOLICITUD_PRESENTADA,EN_REVISION", "OBSERVAR,EN_REVISION,OBSERVADA",
            "APROBAR,EN_REVISION,APROBADA", "RECHAZAR,EN_REVISION,RECHAZADA"})
    void cadaDecisionDgmmEnviaUnCorreoAlDueno(String accion,EstadoTramite antes,EstadoTramite despues) throws Exception {
        estado(antes);
        mvc.perform(post(base()+"/tramite/estado").with(user(DGMM)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(accion,"Revise la información del expediente"))).andExpect(status().isOk());
        var texto=ArgumentCaptor.forClass(String.class);
        verify(notificador).enviarAviso(eq(AGENTE),eq("Registro de Naves: actualización de su trámite"),texto.capture());
        assertThat(texto.getValue()).contains(nombreNave,"Número de registro: "+naveId,"Acción:",
                "Estado del trámite:","Revise la información del expediente","/expediente.html?naveId="+naveId)
                .doesNotContain("password", "token", "hashSha256");
        assertThat(tramites.findByNaveId(naveId).orElseThrow().getEstado()).isEqualTo(despues);
        verifyNoMoreInteractions(notificador);
    }
    @Test void observarDocumentoIncluyeTipoVersionYObservacion() throws Exception {
        mvc.perform(post(base()+"/documentos/"+documentoId+"/revision").with(user(DGMM)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"OBSERVAR\",\"observacion\":\"Cargue el certificado legible\"}"))
                .andExpect(status().isOk());
        var texto=ArgumentCaptor.forClass(String.class);
        verify(notificador).enviarAviso(eq(AGENTE),anyString(),texto.capture());
        assertThat(texto.getValue()).contains(nombreNave,"Observar documento","ITC (versión 1)","Cargue el certificado legible");
        assertThat(documentos.findById(documentoId).orElseThrow().getEstado()).isEqualTo(EstadoDocumento.OBSERVADO);
        assertThat(tramites.findByNaveId(naveId).orElseThrow().getEstado()).isEqualTo(EstadoTramite.EN_REVISION);
    }
    @Test void noEnviaAntesDelCommit() {
        tx.executeWithoutResult(s -> {
            servicio.cambiarEstado(naveId,new TramiteDtos.CambioEstadoRequest(AccionTramite.OBSERVAR,"Revisar"),DGMM);
            verifyNoInteractions(notificador);
        });
        verify(notificador).enviarAviso(eq(AGENTE),anyString(),anyString());
    }
    @Test void rollbackNoEnviaCorreoNiCambiaEstado() {
        tx.executeWithoutResult(s -> {
            servicio.cambiarEstado(naveId,new TramiteDtos.CambioEstadoRequest(AccionTramite.OBSERVAR,"Revisar"),DGMM);
            s.setRollbackOnly();
        });
        verifyNoInteractions(notificador);
        assertThat(tramites.findByNaveId(naveId).orElseThrow().getEstado()).isEqualTo(EstadoTramite.EN_REVISION);
    }
    @Test void falloSmtpNoRevierteTramiteYQuedaRegistrado(CapturedOutput salida) throws Exception {
        doThrow(new MailSendException("DETALLE_PRIVADO_NO_REGISTRAR")).when(notificador).enviarAviso(anyString(),anyString(),anyString());
        mvc.perform(post(base()+"/tramite/estado").with(user(DGMM)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("OBSERVAR","Corrección pendiente"))).andExpect(status().isOk());
        assertThat(tramites.findByNaveId(naveId).orElseThrow().getEstado()).isEqualTo(EstadoTramite.OBSERVADA);
        assertThat(salida).contains("HU-16: no se pudo enviar el aviso tramite-","MailSendException")
                .doesNotContain("DETALLE_PRIVADO_NO_REGISTRAR");
    }
    @Test void falloSmtpNoRevierteDocumento(CapturedOutput salida) throws Exception {
        doThrow(new MailSendException("Fallo de prueba")).when(notificador).enviarAviso(anyString(),anyString(),anyString());
        mvc.perform(post(base()+"/documentos/"+documentoId+"/revision").with(user(DGMM)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"OBSERVAR\",\"observacion\":\"Revise\"}"))
                .andExpect(status().isOk());
        assertThat(documentos.findById(documentoId).orElseThrow().getEstado()).isEqualTo(EstadoDocumento.OBSERVADO);
        assertThat(salida).contains("HU-16: no se pudo enviar el aviso documento-");
    }
    @Test void transicionInvalidaNoNotifica() throws Exception {
        estado(EstadoTramite.SOLICITUD_PRESENTADA);
        mvc.perform(post(base()+"/tramite/estado").with(user(DGMM)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("APROBAR",""))).andExpect(status().isConflict()); verifyNoInteractions(notificador);
    }
    @Test void observarSinComentarioNoNotifica() throws Exception {
        mvc.perform(post(base()+"/tramite/estado").with(user(DGMM)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("OBSERVAR",""))).andExpect(status().isBadRequest()); verifyNoInteractions(notificador);
    }
    @ParameterizedTest @ValueSource(strings={AGENTE,"auditor@navesitas.pa","admin@navesitas.pa"})
    void otrosRolesNoPuedenOriginarAvisoDgmm(String correo) throws Exception {
        mvc.perform(post(base()+"/tramite/estado").with(user(correo)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("OBSERVAR","Revisar"))).andExpect(status().isForbidden()); verifyNoInteractions(notificador);
    }
    @Test void aprobarDocumentoNoNotifica() throws Exception {
        mvc.perform(post(base()+"/documentos/"+documentoId+"/revision").with(user(DGMM)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APROBAR\"}"))
                .andExpect(status().isOk()); verifyNoInteractions(notificador);
    }
    @Test void repetirRevisionNoEnviaOtroCorreo() throws Exception {
        var ruta=base()+"/documentos/"+documentoId+"/revision";
        for(int i=0;i<2;i++) {
            var r=mvc.perform(post(ruta).with(user(DGMM)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"decision\":\"OBSERVAR\",\"observacion\":\"Revisar\"}"));
            r.andExpect(i==0?status().isOk():status().isConflict());
        }
        verify(notificador,times(1)).enviarAviso(eq(AGENTE),anyString(),anyString());
    }
    @ParameterizedTest @ValueSource(strings={"SUBSANAR","DESISTIR"})
    void accionesDelAgenteNoNotifican(String accion) throws Exception {
        estado(EstadoTramite.OBSERVADA);
        mvc.perform(post(base()+"/tramite/estado").with(user(AGENTE)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(accion,"Corregido"))).andExpect(status().isOk()); verifyNoInteractions(notificador);
    }
    @Test void eventoSinTransaccionNoSeEnvia() {
        eventos.publishEvent(new AvisoAlAgente("fuera",naveId,nombreNave,AGENTE,"OBSERVAR","Estado: Observada","Revisar"));
        verifyNoInteractions(notificador);
    }
}
