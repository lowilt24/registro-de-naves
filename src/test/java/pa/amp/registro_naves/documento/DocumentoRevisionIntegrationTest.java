package pa.amp.registro_naves.documento;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import pa.amp.registro_naves.nave.Nave;
import pa.amp.registro_naves.nave.NaveRepository;
import pa.amp.registro_naves.usuario.UsuarioRepository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-12 — la DGMM revisa los documentos y decide el tramite (D2–D5).
 *
 * Cada prueba es el espejo de un caso de Katalon (TC_HU12_01 a 06, TC-44 a
 * TC-49) o de una observacion de la revision de QA de la V7. Cada una arma
 * su propia nave con los cuatro documentos; @Transactional lo deshace.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DocumentoRevisionIntegrationTest {

    static final String AGENTE  = "agente@navesitas.pa";
    static final String DGMM    = "revisor@navesitas.pa";
    static final String AUDITOR = "auditor@navesitas.pa";

    @Autowired MockMvc mvc;
    @Autowired NaveRepository naves;
    @Autowired DocumentoRepository documentos;
    @Autowired UsuarioRepository usuarios;

    /** Nave con propietario, agente residente y los cuatro documentos. Tramite En revision. */
    Long nave;

    @BeforeEach
    void preparar() throws Exception {
        nave = naveConDocumentos("Revision");
        accion(nave, DGMM, "ADMITIR", null).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // TC-44 — aprobar un documento
    // ------------------------------------------------------------------

    @Test
    void laDgmmApruebaUnDocumentoVigenteYPendiente() throws Exception {
        Long itc = vigente(nave, "ITC");

        revisar(nave, itc, DGMM, "APROBAR", null)
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id").value(itc))
           .andExpect(jsonPath("$.tipo").value("ITC"))
           .andExpect(jsonPath("$.estado").value("APROBADO"))
           .andExpect(jsonPath("$.vigente").value(true))
           // Observacion 6 de QA: el nombre del funcionario, no su correo.
           .andExpect(jsonPath("$.revisadoPor").value("Funcionario Revisor DGMM"))
           .andExpect(jsonPath("$.revisadoEn").exists())
           .andExpect(jsonPath("$.observacion").doesNotExist());

        // Los otros tres siguen pendientes y el tramite no se movio (D3).
        assertThat(estados(nave)).containsEntry("ITC", "APROBADO")
                .containsEntry("CERTIFICADO_PROPIEDAD", "PENDIENTE")
                .containsEntry("PODER_NOTARIAL", "PENDIENTE")
                .containsEntry("SMC", "PENDIENTE");
        estadoTramite(nave, "EN_REVISION");

        // Ya revisado: ni aprobar otra vez ni cambiar de opinion.
        revisar(nave, itc, DGMM, "APROBAR", null).andExpect(status().isConflict());
        revisar(nave, itc, DGMM, "OBSERVAR", "Cambio de opinion").andExpect(status().isConflict())
           .andExpect(jsonPath("$.mensaje").value("El documento ya fue revisado."));
    }

    /** Observacion 5 de QA: al aprobar, la observacion es una nota opcional. */
    @Test
    void alAprobarLaObservacionSeGuardaComoNota() throws Exception {
        revisar(nave, vigente(nave, "SMC"), DGMM, "APROBAR", "  Vigente hasta 2028.  ")
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.observacion").value("Vigente hasta 2028."));
    }

    @Test
    void laDecisionNoDistingueMayusculas() throws Exception {
        revisar(nave, vigente(nave, "ITC"), DGMM, "aprobar", null)
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.estado").value("APROBADO"));
    }

    // ------------------------------------------------------------------
    // TC-45 — observar y version nueva
    // ------------------------------------------------------------------

    @Test
    void observarExigeObservacionConLimiteDeMil() throws Exception {
        Long itc = vigente(nave, "ITC");

        revisar(nave, itc, DGMM, "OBSERVAR", null)
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.campos.observacion").exists());
        revisar(nave, itc, DGMM, "OBSERVAR", "   ").andExpect(status().isBadRequest());
        revisar(nave, itc, DGMM, "OBSERVAR", "x".repeat(1001))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.campos.observacion").exists());
        assertThat(estados(nave)).containsEntry("ITC", "PENDIENTE");

        revisar(nave, itc, DGMM, "OBSERVAR", "x".repeat(1000))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.estado").value("OBSERVADO"));
        // D3: observar un documento no mueve el tramite.
        estadoTramite(nave, "EN_REVISION");
    }

    @Test
    void laVersionNuevaEntraPendienteYLaAnteriorConservaSuObservacion() throws Exception {
        Long itc = vigente(nave, "ITC");
        String motivo = "El ITC está vencido.";
        revisar(nave, itc, DGMM, "OBSERVAR", motivo)
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.observacion").value(motivo));

        cargarDocumento(nave, AGENTE, "ITC").andExpect(status().isCreated())
           .andExpect(jsonPath("$.version").value(2))
           .andExpect(jsonPath("$.estado").value("PENDIENTE"))
           .andExpect(jsonPath("$.observacion").doesNotExist());
        Long nuevo = vigente(nave, "ITC");
        assertThat(nuevo).isNotEqualTo(itc);

        mvc.perform(get("/api/naves/" + nave + "/documentos").param("historial", "true")
                        .param("tipo", "ITC").with(user(AGENTE)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[1].id").value(itc))
           .andExpect(jsonPath("$[1].vigente").value(false))
           .andExpect(jsonPath("$[1].estado").value("OBSERVADO"))
           .andExpect(jsonPath("$[1].observacion").value(motivo));

        // La anterior ya no se revisa; la nueva si.
        revisar(nave, itc, DGMM, "APROBAR", null).andExpect(status().isConflict())
           .andExpect(jsonPath("$.mensaje", containsString("versión nueva")));
        revisar(nave, nuevo, DGMM, "APROBAR", null).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // TC-46 — acceso y errores, y su orden
    // ------------------------------------------------------------------

    @Test
    void accesoYErrores() throws Exception {
        Long itc = vigente(nave, "ITC");
        Long otraNave = naveConDocumentos("Revision Otra");
        Long itcDeOtra = vigente(otraNave, "ITC");

        // 401
        mvc.perform(post(ruta(nave, itc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APROBAR\"}"))
           .andExpect(status().isUnauthorized());
        // 403: el agente dueno y el auditor no revisan
        revisar(nave, itc, AGENTE, "APROBAR", null).andExpect(status().isForbidden())
           .andExpect(jsonPath("$.mensaje").value("Solo un funcionario de la DGMM puede revisar documentos."));
        revisar(nave, itc, AUDITOR, "APROBAR", null).andExpect(status().isForbidden());
        // 404: nave inexistente, documento inexistente, documento de otra nave (IDOR)
        revisar(999_999_999L, itc, DGMM, "APROBAR", null).andExpect(status().isNotFound());
        revisar(nave, 999_999_999L, DGMM, "APROBAR", null).andExpect(status().isNotFound());
        revisar(nave, itcDeOtra, DGMM, "APROBAR", null).andExpect(status().isNotFound());
        // 400: cuerpo
        cuerpo(nave, itc, DGMM, "{}").andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.campos.decision").exists());
        cuerpo(nave, itc, DGMM, "{\"decision\": \"\"}").andExpect(status().isBadRequest());
        revisar(nave, itc, DGMM, "BORRAR", null).andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.campos.decision").exists());
        cuerpo(nave, itc, DGMM, "{\"decision\": \"APROBAR\"").andExpect(status().isBadRequest());
        // 400: caracter nulo (SEC-08), nunca 500
        cuerpo(nave, itc, DGMM, "{\"decision\":\"OBSERVAR\",\"observacion\":\"Firma\\u0000ilegible\"}")
           .andExpect(status().isBadRequest());
        // 400: ID que no es numero
        cuerpo(nave, null, DGMM, "{\"decision\":\"APROBAR\"}").andExpect(status().isBadRequest());

        // Nada de lo anterior cambio los documentos.
        assertThat(estados(nave).values()).containsOnly("PENDIENTE");
        assertThat(estados(otraNave).values()).containsOnly("PENDIENTE");
    }

    /** Orden del criterio de HU-12 (401 -> 404 -> 403 -> 409 -> 400): con dos errores gana el primero. */
    @Test
    void conDosErroresGanaElPrimeroDelOrden() throws Exception {
        Long itc = vigente(nave, "ITC");
        // Agente con decision inventada: 403 antes que 400.
        revisar(nave, itc, AGENTE, "BORRAR", null).andExpect(status().isForbidden());
        // Nave inexistente y agente: 404 antes que 403.
        revisar(999_999_999L, itc, AGENTE, "APROBAR", null).andExpect(status().isNotFound());
        // DGMM, tramite en Solicitud presentada y observar sin observacion: 409 antes que 400.
        Long sinAdmitir = naveConDocumentos("Revision Sin Admitir");
        revisar(sinAdmitir, vigente(sinAdmitir, "ITC"), DGMM, "OBSERVAR", null)
           .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------
    // TC-47 — D2: solo con el tramite En revision
    // ------------------------------------------------------------------

    @Test
    void soloSeRevisaConElTramiteEnRevision() throws Exception {
        Long otra = naveConDocumentos("Revision D2");
        Long itc = vigente(otra, "ITC");
        Long smc = vigente(otra, "SMC");

        estadoTramite(otra, "SOLICITUD_PRESENTADA");
        revisar(otra, itc, DGMM, "APROBAR", null).andExpect(status().isConflict())
           .andExpect(jsonPath("$.mensaje").value("Los documentos solo se revisan con el trámite En revisión."));

        accion(otra, DGMM, "ADMITIR", null).andExpect(status().isOk());
        accion(otra, DGMM, "OBSERVAR", "Revise el poder notarial.").andExpect(status().isOk());
        revisar(otra, itc, DGMM, "APROBAR", null).andExpect(status().isConflict());
        revisar(otra, smc, DGMM, "OBSERVAR", "Falta la firma.").andExpect(status().isConflict());
        assertThat(estados(otra).values()).containsOnly("PENDIENTE");

        accion(otra, AGENTE, "SUBSANAR", null).andExpect(status().isOk());
        revisar(otra, itc, DGMM, "APROBAR", null).andExpect(status().isOk());

        accion(otra, DGMM, "RECHAZAR", "No cumple la Ley 55.").andExpect(status().isOk());
        revisar(otra, smc, DGMM, "APROBAR", null).andExpect(status().isConflict());
        assertThat(estados(otra)).containsEntry("SMC", "PENDIENTE");
    }

    // ------------------------------------------------------------------
    // TC-48 — D4 y D5: aprobar exige los cuatro aprobados y abandera
    // ------------------------------------------------------------------

    @Test
    void aprobarElTramiteExigeLosCuatroAprobadosYAbanderaLaNave() throws Exception {
        for (String tipo : new String[] {"CERTIFICADO_PROPIEDAD", "PODER_NOTARIAL", "ITC"}) {
            revisar(nave, vigente(nave, tipo), DGMM, "APROBAR", null).andExpect(status().isOk());
        }

        // SMC pendiente: 409 con la lista, y nada cambia.
        accion(nave, DGMM, "APROBAR", null)
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.faltantes", contains("SMC")))
           .andExpect(jsonPath("$.mensaje", containsString("sin aprobar")));
        estadoTramite(nave, "EN_REVISION");
        assertThat(estadoNave(nave)).isEqualTo("REGISTRADA");

        // SMC observado: sigue sin aprobar.
        revisar(nave, vigente(nave, "SMC"), DGMM, "OBSERVAR", "El SMC no corresponde a la nave.")
           .andExpect(status().isOk());
        accion(nave, DGMM, "APROBAR", null).andExpect(status().isConflict())
           .andExpect(jsonPath("$.faltantes", contains("SMC")));

        // Version nueva del SMC, aprobada: ahora si.
        cargarDocumento(nave, AGENTE, "SMC").andExpect(status().isCreated());
        revisar(nave, vigente(nave, "SMC"), DGMM, "APROBAR", null).andExpect(status().isOk());
        accion(nave, DGMM, "APROBAR", null)
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.estado").value("APROBADA"));
        assertThat(estadoNave(nave)).isEqualTo("ABANDERADA");
        mvc.perform(get("/api/naves/" + nave).with(user(AGENTE)))
           .andExpect(jsonPath("$.estado").value("ABANDERADA"))
           .andExpect(jsonPath("$.estadoTramite").value("APROBADA"));
    }

    @Test
    void sinNingunDocumentoAprobadoLaListaTraeLosCuatroEnOrden() throws Exception {
        accion(nave, DGMM, "APROBAR", null)
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.faltantes",
                   contains("CERTIFICADO_PROPIEDAD", "PODER_NOTARIAL", "ITC", "SMC")));
    }

    // ------------------------------------------------------------------
    // TC-49 — D5 al rechazar, y reemplazo de un aprobado (observacion 8)
    // ------------------------------------------------------------------

    @Test
    void reemplazarUnAprobadoVuelveABloquearYRechazarMarcaLaNave() throws Exception {
        for (String tipo : new String[] {"CERTIFICADO_PROPIEDAD", "PODER_NOTARIAL", "ITC", "SMC"}) {
            revisar(nave, vigente(nave, tipo), DGMM, "APROBAR", null).andExpect(status().isOk());
        }
        // El agente reemplaza el ITC ya aprobado: la version nueva entra pendiente.
        cargarDocumento(nave, AGENTE, "ITC").andExpect(status().isCreated());
        assertThat(estados(nave)).containsEntry("ITC", "PENDIENTE");

        accion(nave, DGMM, "APROBAR", null).andExpect(status().isConflict())
           .andExpect(jsonPath("$.faltantes", contains("ITC")));
        assertThat(estadoNave(nave)).isEqualTo("REGISTRADA");

        accion(nave, DGMM, "RECHAZAR", "Documentación inconsistente.").andExpect(status().isOk());
        estadoTramite(nave, "RECHAZADA");
        assertThat(estadoNave(nave)).isEqualTo("RECHAZADA");
    }

    /** Observacion 7 de QA: desistir no toca la nave. */
    @Test
    void desistirDejaLaNaveRegistrada() throws Exception {
        Long otra = naveConDocumentos("Revision Desiste");
        accion(otra, AGENTE, "DESISTIR", null).andExpect(status().isOk());
        assertThat(estadoNave(otra)).isEqualTo("REGISTRADA");
    }

    // ------------------------------------------------------------------
    // Observaciones 2 y 4 de QA
    // ------------------------------------------------------------------

    /** Observacion 2: la DGMM no puede revisar lo que no puede abrir. */
    @Test
    void laDgmmYElAuditorListanYDescarganLosDocumentos() throws Exception {
        Long itc = vigente(nave, "ITC");
        for (String quien : new String[] {DGMM, AUDITOR}) {
            mvc.perform(get("/api/naves/" + nave + "/documentos").with(user(quien)))
               .andExpect(status().isOk());
            mvc.perform(get("/api/naves/" + nave + "/documentos/" + itc + "/archivo").with(user(quien)))
               .andExpect(status().isOk());
        }
    }

    /**
     * Observacion 4: si otro funcionario reviso el documento entre la
     * lectura y la escritura, el UPDATE condicional no cambia nada y el
     * servicio responde 409 en lugar de pisarlo.
     */
    @Test
    void unaSegundaRevisionSimultaneaNoPisaLaPrimera() {
        Long itc = vigente(nave, "ITC");
        var revisor = usuarios.findByCorreoIgnoreCase(DGMM).orElseThrow();

        int primera = documentos.revisar(itc, EstadoDocumento.APROBADO, null, revisor, OffsetDateTime.now());
        int segunda = documentos.revisar(itc, EstadoDocumento.OBSERVADO, "Tarde", revisor, OffsetDateTime.now());

        assertThat(primera).isEqualTo(1);
        assertThat(segunda).isZero();
        assertThat(documentos.findById(itc).orElseThrow().getEstado()).isEqualTo(EstadoDocumento.APROBADO);
    }

    // ------------------------------------------------------------------

    private static String ruta(Long naveId, Long documentoId) {
        return "/api/naves/" + naveId + "/documentos/" + documentoId + "/revision";
    }

    private ResultActions revisar(Long naveId, Long documentoId, String quien, String decision,
                                  String observacion) throws Exception {
        String json = observacion == null
                ? "{\"decision\":\"" + decision + "\"}"
                : "{\"decision\":\"" + decision + "\",\"observacion\":\"" + observacion + "\"}";
        return cuerpo(naveId, documentoId, quien, json);
    }

    /** documentoId nulo: la ruta lleva "abc" para probar el 400 del parametro. */
    private ResultActions cuerpo(Long naveId, Long documentoId, String quien, String json) throws Exception {
        String r = documentoId == null
                ? "/api/naves/" + naveId + "/documentos/abc/revision"
                : ruta(naveId, documentoId);
        return mvc.perform(post(r).with(user(quien))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions accion(Long naveId, String quien, String accion, String observacion) throws Exception {
        String json = observacion == null
                ? "{\"accion\":\"" + accion + "\"}"
                : "{\"accion\":\"" + accion + "\",\"observacion\":\"" + observacion + "\"}";
        return mvc.perform(post("/api/naves/" + naveId + "/tramite/estado").with(user(quien))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void estadoTramite(Long naveId, String esperado) throws Exception {
        mvc.perform(get("/api/naves/" + naveId + "/tramite").with(user(DGMM)))
           .andExpect(jsonPath("$.estado").value(esperado));
    }

    private String estadoNave(Long naveId) {
        return naves.findById(naveId).orElseThrow().getEstado().name();
    }

    private Long vigente(Long naveId, String tipo) {
        return documentos.findByNaveIdAndTipoAndVigenteTrue(naveId, TipoDocumento.valueOf(tipo))
                .orElseThrow().getId();
    }

    /** tipo -> estado de la version vigente, leido de la base. */
    private Map<String, String> estados(Long naveId) {
        return documentos.findByNaveIdAndVigenteTrue(naveId).stream()
                .collect(Collectors.toMap(d -> d.getTipo().name(), d -> d.getEstado().name()));
    }

    private Long naveConDocumentos(String base) throws Exception {
        String nombre = base + " " + System.nanoTime();
        mvc.perform(post("/api/naves").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {
                                   "nombre": "%s", "tipo": "CARGA", "servicio": "CABOTAJE",
                                   "tonelajeBruto": 5000.00, "tonelajeNeto": 2500.00,
                                   "eslora": 90.00, "manga": 14.00, "puntal": 8.00,
                                   "anioConstruccion": 2022, "lugarConstruccion": "Astillero de prueba",
                                   "materialCasco": "Acero", "tipoPropulsion": "Motor diesel",
                                   "potenciaKw": 3100.00
                                 }
                                 """.formatted(nombre)))
           .andExpect(status().isCreated());
        Long id = naves.findByNombreNormalizado(Nave.normalizar(nombre)).map(Nave::getId).orElseThrow();

        mvc.perform(post("/api/propietarios").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                               {"nombre": "Naviera Revision S.A.", "tipo": "JURIDICA",
                                "nacionalidad": "Panamena", "domicilio": "Calle 50",
                                "paisConstitucion": "Panama", "naveId": %d}
                               """.formatted(id)))
           .andExpect(status().isCreated());
        mvc.perform(post("/api/naves/" + id + "/agente-residente").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                               {"nombre": "Bufete Revision", "idoneidad": "IDN-1212",
                                "telefono": "+507 200-1234", "correo": "revision@bufete.com.pa"}
                               """))
           .andExpect(status().isCreated());
        for (TipoDocumento tipo : TipoDocumento.OBLIGATORIOS) {
            cargarDocumento(id, AGENTE, tipo.name()).andExpect(status().isCreated());
        }
        return id;
    }

    private ResultActions cargarDocumento(Long naveId, String quien, String tipo) throws Exception {
        MockMultipartFile pdf = new MockMultipartFile("archivo", tipo.toLowerCase() + ".pdf",
                "application/pdf", ("%PDF-1.4\n%" + tipo + " " + System.nanoTime() + "\n%%EOF\n").getBytes());
        return mvc.perform(multipart("/api/naves/" + naveId + "/documentos")
                .file(pdf).param("tipo", tipo).with(user(quien)));
    }
}
