package pa.amp.registro_naves.tramite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import pa.amp.registro_naves.documento.*;
import pa.amp.registro_naves.nave.*;
import pa.amp.registro_naves.usuario.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RequisitosDocumentoIntegrationTest {
    static final String AGENTE = "agente@navesitas.pa", DGMM = "revisor@navesitas.pa";
    @Autowired MockMvc mvc;
    @Autowired NaveRepository naves;
    @Autowired TramiteRepository tramites;
    @Autowired DocumentoRepository documentos;
    @Autowired UsuarioRepository usuarios;
    Long id;

    static String naveJson(String nombre) {
        return """
            {"nombre":"%s","tipo":"CARGA","servicio":"INTERNACIONAL",
             "tonelajeBruto":499,"tonelajeNeto":200,"eslora":23.99,"manga":10,"puntal":5,
             "anioConstruccion":2022,"lugarConstruccion":"Panama","materialCasco":"Acero",
             "tipoPropulsion":"Diesel","potenciaKw":1000}
            """.formatted(nombre);
    }

    @BeforeEach void preparar() throws Exception {
        String nombre = "HU Trece " + System.nanoTime();
        mvc.perform(post("/api/naves").with(user(AGENTE)).contentType(MediaType.APPLICATION_JSON)
                .content(naveJson(nombre))).andExpect(status().isCreated());
        id = naves.findByNombreNormalizado(Nave.normalizar(nombre)).orElseThrow().getId();
        mvc.perform(post("/api/propietarios").with(user(AGENTE)).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nombre":"Naviera HU Trece","tipo":"JURIDICA","nacionalidad":"Panamena",
                     "domicilio":"Panama","paisConstitucion":"Panama","naveId":%d}
                    """.formatted(id))).andExpect(status().isCreated());
        mvc.perform(post("/api/naves/" + id + "/agente-residente").with(user(AGENTE))
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"nombre":"Bufete HU Trece","idoneidad":"IDN-HU13","telefono":"+507 200-1234",
                     "correo":"hu13@bufete.pa"}
                    """)).andExpect(status().isCreated());
    }

    String base() { return "/api/naves/" + id; }
    Nave nave() { return naves.findById(id).orElseThrow(); }
    ResultActions accion(String accion, String correo) throws Exception {
        return mvc.perform(post(base() + "/tramite/estado").with(user(correo))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"accion\":\"" + accion + "\",\"observacion\":\"Correccion documental\"}"));
    }
    void cargar(TipoDocumento tipo) throws Exception {
        mvc.perform(multipart(base() + "/documentos").file(new MockMultipartFile("archivo", tipo + ".pdf",
            "application/pdf", "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII)))
            .param("tipo", tipo.name()).with(user(AGENTE))).andExpect(status().isCreated());
    }
    void basicos() throws Exception { cargar(TipoDocumento.CERTIFICADO_PROPIEDAD); cargar(TipoDocumento.PODER_NOTARIAL); }
    void aprobarDocumentos() throws Exception {
        for (var d : documentos.findByNaveIdAndVigenteTrue(id)) {
            mvc.perform(post(base() + "/documentos/" + d.getId() + "/revision").with(user(DGMM))
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APROBAR\"}"))
                .andExpect(status().isOk());
        }
    }

    @Test void pequenosNoNecesitanItcNiSmcEnNingunaTransicion() throws Exception {
        mvc.perform(get(base() + "/documentos/requisitos").with(user(AGENTE)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[2].estado").value("NO_APLICA"))
            .andExpect(jsonPath("$[3].estado").value("NO_APLICA"));
        basicos();
        accion("ADMITIR", DGMM).andExpect(status().isOk());
        accion("OBSERVAR", DGMM).andExpect(status().isOk());
        accion("SUBSANAR", AGENTE).andExpect(status().isOk());
        accion("APROBAR", DGMM).andExpect(status().isConflict())
            .andExpect(jsonPath("$.faltantes", contains("CERTIFICADO_PROPIEDAD", "PODER_NOTARIAL")));
        aprobarDocumentos();
        accion("APROBAR", DGMM).andExpect(status().isOk());
        assertThat(nave().getEstado()).isEqualTo(EstadoNave.ABANDERADA);
        assertThat(documentos.findByNaveIdAndVigenteTrue(id)).hasSize(2);
    }

    @ParameterizedTest @ValueSource(strings={"ADMITIR","SUBSANAR","APROBAR"})
    void faltaObligatorioBloqueaLasTresAcciones(String accion) throws Exception {
        nave().setEslora(new BigDecimal("24")); naves.flush();
        basicos();
        Tramite t = tramites.findByNaveId(id).orElseThrow();
        if (accion.equals("SUBSANAR")) t.cambiarA(EstadoTramite.OBSERVADA);
        if (accion.equals("APROBAR")) { t.cambiarA(EstadoTramite.EN_REVISION); aprobarDocumentos(); }
        tramites.flush();
        EstadoTramite anterior = t.getEstado();
        accion(accion, accion.equals("SUBSANAR") ? AGENTE : DGMM)
            .andExpect(status().isConflict()).andExpect(jsonPath("$.faltantes", contains("ITC")));
        assertThat(t.getEstado()).isEqualTo(anterior);
    }

    @Test void itcObligatorioSmcExentoYVersionesSeConservan() throws Exception {
        nave().setEslora(new BigDecimal("24.01")); naves.flush();
        basicos(); cargar(TipoDocumento.ITC);
        mvc.perform(get(base() + "/documentos/requisitos").with(user(AGENTE)))
            .andExpect(jsonPath("$[2].estado").value("OBLIGATORIO"))
            .andExpect(jsonPath("$[3].estado").value("NO_APLICA"));
        accion("ADMITIR", DGMM).andExpect(status().isOk());
        aprobarDocumentos();
        // Cambio de aplicabilidad en memoria de prueba: no existe endpoint para editar ficha.
        nave().setEslora(new BigDecimal("23.99")); naves.flush();
        var itc = documentos.findByNaveIdAndVigenteTrue(id).stream().filter(d -> d.getTipo()==TipoDocumento.ITC).findFirst().orElseThrow();
        cargar(TipoDocumento.ITC);
        mvc.perform(get(base()+"/documentos?historial=true").with(user(AGENTE)))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(4)));
        mvc.perform(get(base()+"/documentos/"+itc.getId()+"/archivo").with(user(AGENTE)))
            .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF));
        accion("APROBAR", DGMM).andExpect(status().isOk()); // ITC opcional pendiente no bloquea.
    }

    @ParameterizedTest @CsvSource({"CABOTAJE,CARGA", "MIXTO,CARGA", "INTERNACIONAL,MIXTA", "INTERNACIONAL,TANQUERO"})
    void pendienteBloqueaAunConTodosLosDocumentos(String servicio, String tipo) throws Exception {
        nave().setServicio(TipoServicio.valueOf(servicio)); nave().setTipo(TipoNave.valueOf(tipo)); naves.flush();
        for (var d : TipoDocumento.values()) cargar(d);
        for (String accion : new String[]{"ADMITIR","SUBSANAR","APROBAR"}) {
            Tramite t = tramites.findByNaveId(id).orElseThrow();
            t.cambiarA(accion.equals("ADMITIR") ? EstadoTramite.SOLICITUD_PRESENTADA
                : accion.equals("SUBSANAR") ? EstadoTramite.OBSERVADA : EstadoTramite.EN_REVISION);
            tramites.flush();
            EstadoTramite anterior = t.getEstado();
            accion(accion, accion.equals("SUBSANAR") ? AGENTE : DGMM).andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", containsString("REQUIERE_REVISION")));
            assertThat(t.getEstado()).isEqualTo(anterior);
        }
        assertThat(documentos.findByNaveIdAndVigenteTrue(id)).hasSize(4);
    }

    @Test void permisoConsultaIgualAlExpediente() throws Exception {
        mvc.perform(get(base()+"/documentos/requisitos")).andExpect(status().isUnauthorized());
        for(String correo : new String[]{AGENTE,DGMM,"auditor@navesitas.pa"})
            mvc.perform(get(base()+"/documentos/requisitos").with(user(correo))).andExpect(status().isOk());
        mvc.perform(get(base()+"/documentos/requisitos").with(user("admin@navesitas.pa"))).andExpect(status().isForbidden());
        Usuario otro = new Usuario(); otro.setCorreo("otro-hu13@prueba.pa"); otro.setNombreCompleto("Otro Agente");
        otro.setPasswordHash("no-usado"); otro.setRol(Rol.AGENTE_NAVIERO); usuarios.saveAndFlush(otro);
        mvc.perform(get(base()+"/documentos/requisitos").with(user(otro.getCorreo()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/naves/999999999/documentos/requisitos").with(user(AGENTE))).andExpect(status().isNotFound());
    }

    @ParameterizedTest @ValueSource(strings={"\"eslora\":null", "\"eslora\":-1", "\"tonelajeBruto\":null", "\"tonelajeBruto\":0", "\"servicio\":null", "\"servicio\":\"NACIONAL\"", "\"tipo\":\"NO_CLASIFICADO\""})
    void datosInvalidosResponden400(String campo) throws Exception {
        String nombreCampo = campo.substring(1,campo.indexOf('"',1));
        String json = naveJson("Invalida " + System.nanoTime()).replaceAll("\""+nombreCampo+"\":(\"[^\"]*\"|[0-9.]+)", java.util.regex.Matcher.quoteReplacement(campo));
        mvc.perform(post("/api/naves").with(user(AGENTE)).contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isBadRequest());
    }
}
