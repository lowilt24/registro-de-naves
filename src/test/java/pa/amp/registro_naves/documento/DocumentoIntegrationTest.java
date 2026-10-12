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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import pa.amp.registro_naves.nave.Nave;
import pa.amp.registro_naves.nave.NaveRepository;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-07 (carga) y HU-08 (consulta y descarga). Los casos con numero son el
 * espejo de los de Katalon; los demas cubren seguridad que Katalon no ve.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DocumentoIntegrationTest {

    static final String AGENTE  = "agente@navesitas.pa";
    static final String REVISOR = "revisor@navesitas.pa";

    @Autowired MockMvc mvc;
    @Autowired NaveRepository naves;
    @Autowired AlmacenArchivos almacen;
    @Autowired DocumentoRepository documentos;

    /** Nave con propietario y agente: cumple la precondicion de HU-07. */
    Long nave;

    @BeforeEach
    void prepararNave() throws Exception {
        nave = crearNave("Nave Prueba Documentos", AGENTE);
        vincularPropietario(nave);
        designarAgente(nave);
    }

    // ------------------------------------------------------------------
    // HU-07 — carga
    // ------------------------------------------------------------------

    /** TC-17: carga valida, con tamano, hash y estado. */
    @Test
    void cargaUnPdfValido() throws Exception {
        byte[] contenido = pdf("itc");
        mvc.perform(cargar(nave, "ITC", archivo("itc.pdf", contenido)).with(user(AGENTE)))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.tipo").value("ITC"))
           .andExpect(jsonPath("$.version").value(1))
           .andExpect(jsonPath("$.vigente").value(true))
           .andExpect(jsonPath("$.estado").value("PENDIENTE"))
           .andExpect(jsonPath("$.nombreArchivo").value("itc.pdf"))
           .andExpect(jsonPath("$.tamanoBytes").value(contenido.length))
           .andExpect(jsonPath("$.hashSha256").value(sha256(contenido)))
           .andExpect(jsonPath("$.ruta").doesNotExist())
           .andExpect(jsonPath("$.creadoEn").exists());
    }

    /** TC-17, criterio 7: recargar el mismo tipo crea version nueva y conserva la anterior. */
    @Test
    void recargarElMismoTipoCreaVersionNueva() throws Exception {
        mvc.perform(cargar(nave, "ITC", archivo("v1.pdf", pdf("uno"))).with(user(AGENTE)))
           .andExpect(status().isCreated());
        mvc.perform(cargar(nave, "ITC", archivo("v2.pdf", pdf("dos"))).with(user(AGENTE)))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.version").value(2));

        mvc.perform(get(rutaLista(nave)).with(user(AGENTE)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(1)))
           .andExpect(jsonPath("$[0].version").value(2));

        mvc.perform(get(rutaLista(nave)).param("historial", "true").with(user(AGENTE)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(2)))
           .andExpect(jsonPath("$[0].version").value(2))
           .andExpect(jsonPath("$[0].vigente").value(true))
           .andExpect(jsonPath("$[1].version").value(1))
           .andExpect(jsonPath("$[1].vigente").value(false));
    }

    /** TC-18: texto plano y ejecutable con nombre .pdf y Content-Type de PDF. */
    @Test
    void rechazaArchivoQueNoEsPdfAunqueLoParezca() throws Exception {
        byte[] texto = "esto es texto plano".getBytes(StandardCharsets.UTF_8);
        byte[] ejecutable = {0x4D, 0x5A, (byte) 0x90, 0x00, 0x03};

        mvc.perform(cargar(nave, "SMC", archivo("texto.pdf", texto)).with(user(AGENTE)))
           .andExpect(status().isUnsupportedMediaType())
           .andExpect(jsonPath("$.campos.archivo").exists());
        mvc.perform(cargar(nave, "SMC", archivo("programa.pdf", ejecutable)).with(user(AGENTE)))
           .andExpect(status().isUnsupportedMediaType());

        assertThat(documentos.buscar(nave, null, null, true)).isEmpty();
    }

    /** TC-19: un PDF de 10 MB + 1 byte responde 413. */
    @Test
    void rechazaArchivoSobreElLimite() throws Exception {
        byte[] grande = new byte[(int) DocumentoService.LIMITE_BYTES + 1];
        byte[] firma = "%PDF-1.4".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(firma, 0, grande, 0, firma.length);

        mvc.perform(cargar(nave, "CERTIFICADO_PROPIEDAD", archivo("grande.pdf", grande)).with(user(AGENTE)))
           .andExpect(status().isContentTooLarge())
           .andExpect(jsonPath("$.mensaje").exists());
    }

    /** Exactamente 10 MB si entra: el limite es inclusivo. */
    @Test
    void aceptaArchivoDeExactamenteDiezMegas() throws Exception {
        byte[] justo = new byte[(int) DocumentoService.LIMITE_BYTES];
        byte[] firma = "%PDF-1.4".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(firma, 0, justo, 0, firma.length);

        mvc.perform(cargar(nave, "CERTIFICADO_PROPIEDAD", archivo("justo.pdf", justo)).with(user(AGENTE)))
           .andExpect(status().isCreated());
    }

    /** TC-20: nave ajena 403, nave inexistente 404. */
    @Test
    void noSeCargaEnNaveAjenaNiInexistente() throws Exception {
        mvc.perform(cargar(nave, "ITC", archivo("x.pdf", pdf("x"))).with(user(REVISOR)))
           .andExpect(status().isForbidden());
        mvc.perform(cargar(999_999_999L, "ITC", archivo("x.pdf", pdf("x"))).with(user(AGENTE)))
           .andExpect(status().isNotFound());
    }

    /** Criterio 8: sin agente residente vigente no se carga. */
    @Test
    void sinAgenteResidenteNoSeCarga() throws Exception {
        Long sinAgente = crearNave("Nave Sin Agente Docs", AGENTE);
        vincularPropietario(sinAgente);

        mvc.perform(cargar(sinAgente, "ITC", archivo("x.pdf", pdf("x"))).with(user(AGENTE)))
           .andExpect(status().isConflict());
    }

    /** Criterio 1: tipo fuera de la lista, o sin tipo, o sin archivo: 400 por campo. */
    @Test
    void validaTipoYArchivo() throws Exception {
        mvc.perform(cargar(nave, "PASAPORTE", archivo("x.pdf", pdf("x"))).with(user(AGENTE)))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.campos.tipo").exists());
        mvc.perform(multipart(rutaLista(nave)).file(archivo("x.pdf", pdf("x"))).with(user(AGENTE)))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.campos.tipo").exists());
        mvc.perform(multipart(rutaLista(nave)).param("tipo", "ITC").with(user(AGENTE)))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.campos.archivo").exists());
    }

    /**
     * Criterio 5: un nombre con ../ no escapa del almacen. El archivo se
     * guarda con un nombre generado y el nombre original se guarda limpio.
     */
    @Test
    void elNombreDelClienteNoTocaElDisco() throws Exception {
        MvcResult r = mvc.perform(cargar(nave, "SMC",
                        archivo("../../../etc/passwd.pdf", pdf("traversal"))).with(user(AGENTE)))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.nombreArchivo").value("passwd.pdf"))
           .andReturn();

        Documento guardado = documentos.buscar(nave, TipoDocumento.SMC, null, false).getFirst();
        assertThat(guardado.getRuta()).matches(nave + "/[0-9a-f-]{36}\\.pdf");
        Path enDisco = almacen.ubicar(guardado.getRuta());
        assertThat(Files.exists(enDisco)).isTrue();
        assertThat(r.getResponse().getContentAsString()).doesNotContain("etc");
    }

    /** Enviar JSON al endpoint de carga es un error del cliente, no un 500. */
    @Test
    void jsonEnLugarDeMultipartDa415() throws Exception {
        mvc.perform(post(rutaLista(nave)).with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"ITC\"}"))
           .andExpect(status().isUnsupportedMediaType());
    }

    // ------------------------------------------------------------------
    // HU-08 — consulta
    // ------------------------------------------------------------------

    /** TC-21 */
    @Test
    void consultaConDocumentos() throws Exception {
        mvc.perform(cargar(nave, "SMC", archivo("smc.pdf", pdf("smc"))).with(user(AGENTE)));
        mvc.perform(cargar(nave, "ITC", archivo("itc.pdf", pdf("itc"))).with(user(AGENTE)));

        mvc.perform(get(rutaLista(nave)).with(user(AGENTE)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(2)))
           .andExpect(jsonPath("$[0].tipo").value("ITC"))
           .andExpect(jsonPath("$[1].tipo").value("SMC"))
           .andExpect(jsonPath("$[0].creadoEn").exists());
    }

    /** TC-22: sin documentos es 200 con lista vacia, no 404. */
    @Test
    void consultaSinDocumentos() throws Exception {
        mvc.perform(get(rutaLista(nave)).with(user(AGENTE)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(0)));
    }

    /** TC-23 */
    @Test
    void filtraPorTipoYEstado() throws Exception {
        mvc.perform(cargar(nave, "SMC", archivo("smc.pdf", pdf("smc"))).with(user(AGENTE)));
        mvc.perform(cargar(nave, "ITC", archivo("itc.pdf", pdf("itc"))).with(user(AGENTE)));

        mvc.perform(get(rutaLista(nave)).param("tipo", "ITC").with(user(AGENTE)))
           .andExpect(jsonPath("$", hasSize(1)))
           .andExpect(jsonPath("$[0].tipo").value("ITC"));
        mvc.perform(get(rutaLista(nave)).param("estado", "PENDIENTE").with(user(AGENTE)))
           .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get(rutaLista(nave)).param("estado", "APROBADO").with(user(AGENTE)))
           .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get(rutaLista(nave)).param("tipo", "PASAPORTE").with(user(AGENTE)))
           .andExpect(status().isBadRequest());
        mvc.perform(get(rutaLista(nave)).param("estado", "FALTANTE").with(user(AGENTE)))
           .andExpect(status().isBadRequest());
    }

    /** Criterio 6 */
    @Test
    void consultaDeNaveAjenaOSinSesion() throws Exception {
        mvc.perform(get(rutaLista(nave)).with(user(REVISOR))).andExpect(status().isForbidden());
        mvc.perform(get(rutaLista(nave))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/naves/abc/documentos").with(user(AGENTE))).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // HU-08 — descarga
    // ------------------------------------------------------------------

    /** TC-24: el dueno descarga el mismo archivo que subio. */
    @Test
    void elDuenoDescargaElArchivo() throws Exception {
        byte[] contenido = pdf("descarga");
        Long id = idDe(mvc.perform(cargar(nave, "ITC", archivo("itc final.pdf", contenido)).with(user(AGENTE)))
                .andReturn());

        MvcResult r = mvc.perform(get(rutaArchivo(nave, id)).with(user(AGENTE)))
           .andExpect(status().isOk())
           .andExpect(header().string("Content-Type", "application/pdf"))
           .andExpect(header().string("Content-Disposition",
                   org.hamcrest.Matchers.startsWith("attachment;")))
           .andReturn();
        assertThat(r.getResponse().getContentAsByteArray()).isEqualTo(contenido);
    }

    /** TC-24: otro usuario por la ruta de la nave duena -> 403; sin sesion -> 401. */
    @Test
    void otroUsuarioNoDescarga() throws Exception {
        Long id = idDe(mvc.perform(cargar(nave, "ITC", archivo("itc.pdf", pdf("x"))).with(user(AGENTE)))
                .andReturn());

        mvc.perform(get(rutaArchivo(nave, id)).with(user(REVISOR))).andExpect(status().isForbidden());
        mvc.perform(get(rutaArchivo(nave, id))).andExpect(status().isUnauthorized());
    }

    /**
     * TC-24, IDOR: el revisor tiene una nave propia y pide el documento
     * ajeno colgado de ella. Pasa el control de la nave, pero el documento
     * no es de esa nave: 404.
     */
    @Test
    void idorConNavePropiaYDocumentoAjeno() throws Exception {
        Long id = idDe(mvc.perform(cargar(nave, "ITC", archivo("itc.pdf", pdf("x"))).with(user(AGENTE)))
                .andReturn());
        Long naveDelRevisor = crearNave("Nave Del Revisor Docs", REVISOR);

        mvc.perform(get(rutaArchivo(naveDelRevisor, id)).with(user(REVISOR)))
           .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------

    @Test
    void limpiezaDeNombres() {
        assertThat(DocumentoService.limpiarNombre("..\\..\\windows\\system32\\a.pdf")).isEqualTo("a.pdf");
        assertThat(DocumentoService.limpiarNombre("/etc/passwd")).isEqualTo("passwd");
        assertThat(DocumentoService.limpiarNombre("a\u0000b\r\n.pdf")).isEqualTo("ab.pdf");
        assertThat(DocumentoService.limpiarNombre("..")).isEqualTo("documento.pdf");
        assertThat(DocumentoService.limpiarNombre(null)).isEqualTo("documento.pdf");
    }

    // ------------------------------------------------------------------

    private static byte[] pdf(String texto) {
        return ("%PDF-1.4\n%" + texto + "\n%%EOF\n").getBytes(StandardCharsets.UTF_8);
    }

    private static MockMultipartFile archivo(String nombre, byte[] contenido) {
        return new MockMultipartFile("archivo", nombre, "application/pdf", contenido);
    }

    private static String rutaLista(Long naveId) {
        return "/api/naves/" + naveId + "/documentos";
    }

    private static String rutaArchivo(Long naveId, Long documentoId) {
        return rutaLista(naveId) + "/" + documentoId + "/archivo";
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder
            cargar(Long naveId, String tipo, MockMultipartFile archivo) {
        return (org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder)
                multipart(rutaLista(naveId)).file(archivo).param("tipo", tipo);
    }

    private static Long idDe(MvcResult r) throws Exception {
        String json = r.getResponse().getContentAsString();
        return Long.valueOf(json.replaceAll("(?s).*\"id\"\\s*:\\s*(\\d+).*", "$1"));
    }

    private static String sha256(byte[] datos) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datos));
    }

    private Long crearNave(String nombre, String usuario) throws Exception {
        String cuerpo = """
               {
                 "nombre": "%s",
                 "tipo": "CARGA",
                 "servicio": "CABOTAJE",
                 "tonelajeBruto": 5000.00,
                 "tonelajeNeto": 2500.00,
                 "eslora": 90.00,
                 "manga": 14.00,
                 "puntal": 8.00,
                 "anioConstruccion": 2022,
                 "lugarConstruccion": "Astillero de prueba",
                 "materialCasco": "Acero",
                 "tipoPropulsion": "Motor diesel",
                 "potenciaKw": 3100.00
               }
               """.formatted(nombre);
        mvc.perform(post("/api/naves").with(user(usuario))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
           .andExpect(status().isCreated());
        return naves.findByNombreNormalizado(Nave.normalizar(nombre))
                .map(Nave::getId).orElseThrow();
    }

    private void vincularPropietario(Long naveId) throws Exception {
        mvc.perform(post("/api/propietarios").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                               {
                                 "nombre": "Naviera Documentos S.A.",
                                 "tipo": "JURIDICA",
                                 "nacionalidad": "Panamena",
                                 "domicilio": "Calle 50, Ciudad de Panama",
                                 "paisConstitucion": "Panama",
                                 "naveId": %d
                               }
                               """.formatted(naveId)))
           .andExpect(status().isCreated());
    }

    private void designarAgente(Long naveId) throws Exception {
        mvc.perform(post("/api/naves/" + naveId + "/agente-residente").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                               {
                                 "nombre": "Bufete Documentos",
                                 "idoneidad": "IDN-0707",
                                 "telefono": "+507 200-1234",
                                 "correo": "docs@bufete.com.pa"
                               }
                               """))
           .andExpect(status().isCreated());
    }
}
