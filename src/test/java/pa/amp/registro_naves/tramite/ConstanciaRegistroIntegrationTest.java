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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import pa.amp.registro_naves.constancia.*;
import pa.amp.registro_naves.documento.*;
import pa.amp.registro_naves.nave.*;
import pa.amp.registro_naves.persona.*;
import pa.amp.registro_naves.usuario.*;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class ConstanciaRegistroIntegrationTest {
    static final String AGENTE = "agente@navesitas.pa", DGMM = "revisor@navesitas.pa";
    @Autowired MockMvc mvc;
    @Autowired NaveRepository naves;
    @Autowired TramiteRepository tramites;
    @Autowired HistorialEstadoRepository historial;
    @Autowired UsuarioRepository usuarios;
    @Autowired PropietarioRepository propietarios;
    @Autowired NavePropietarioRepository participaciones;
    @Autowired AgenteResidenteRepository agentes;
    @Autowired DocumentoRepository documentos;
    @Autowired ConstanciaRegistroRepository constancias;
    @Autowired ConstanciaRegistroService servicio;
    @Autowired org.springframework.transaction.PlatformTransactionManager transacciones;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    Nave nave;
    Tramite tramite;
    HistorialEstado aprobacion;

    @BeforeEach void preparar() {
        nave = new Nave();
        nave.setNombre("Océano de Prueba " + UUID.randomUUID());
        nave.setTipo(TipoNave.CARGA); nave.setServicio(TipoServicio.INTERNACIONAL);
        nave.setTonelajeBruto(new BigDecimal("499.00")); nave.setTonelajeNeto(new BigDecimal("250.00"));
        nave.setEslora(new BigDecimal("24.00")); nave.setManga(new BigDecimal("8.00")); nave.setPuntal(new BigDecimal("4.00"));
        nave.setAnioConstruccion(2020); nave.setLugarConstruccion("Panamá"); nave.setMaterialCasco("Acero");
        nave.setTipoPropulsion("Diésel"); nave.setPotenciaKw(new BigDecimal("750.00"));
        nave.setRegistradoPor(usuarios.findByCorreoIgnoreCase(AGENTE).orElseThrow());
        naves.saveAndFlush(nave);
        tramite = new Tramite(nave); tramite.cambiarA(EstadoTramite.APROBADA); tramites.saveAndFlush(tramite);
        aprobacion = historial.saveAndFlush(new HistorialEstado(tramite,
                usuarios.findByCorreoIgnoreCase(DGMM).orElseThrow(), AccionTramite.APROBAR,
                EstadoTramite.EN_REVISION, EstadoTramite.APROBADA, null));
        propietario("Naviera Muñoz & Hijos", "60.00");
        propietario("María del Océano", "40.00");
        AgenteResidente agente = new AgenteResidente(); agente.setNave(nave);
        agente.setNombre("Firma Jurídica del Pacífico"); agente.setIdoneidad("IDN-HU15");
        agentes.saveAndFlush(agente);
    }
    void propietario(String nombre, String cuota) {
        Propietario p = new Propietario(); p.setNombre(nombre); p.setTipo(TipoPersona.NATURAL);
        p.setNacionalidad("Panameña"); p.setDomicilio("Panamá"); propietarios.saveAndFlush(p);
        NavePropietario np = new NavePropietario(); np.setNave(nave); np.setPropietario(p);
        np.setPorcentaje(new BigDecimal(cuota)); participaciones.saveAndFlush(np);
    }
    String ruta() { return "/api/naves/" + nave.getId() + "/constancia"; }
    byte[] descargar(String correo) throws Exception {
        return mvc.perform(get(ruta()).accept(MediaType.APPLICATION_PDF, MediaType.APPLICATION_JSON)
                .with(user(correo))).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", containsString("constancia-registro-" + nave.getId() + ".pdf")))
                .andReturn().getResponse().getContentAsByteArray();
    }

    @ParameterizedTest @ValueSource(strings={AGENTE, DGMM, "auditor@navesitas.pa"})
    void rolesAutorizadosDescarganPdfConHashPersistido(String correo) throws Exception {
        byte[] pdf = descargar(correo);
        var guardado = constancias.findById(nave.getId()).orElseThrow();
        assertThat(guardado.getContenido()).isEqualTo(pdf);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf));
        assertThat(guardado.getHashSha256()).isEqualTo(hash);
        mvc.perform(get(ruta()).with(user(correo))).andExpect(header().string("X-Content-SHA256", hash));
        assertThat(guardado.getAprobacion().getId()).isEqualTo(aprobacion.getId());
    }
    @Test void pdfIncluyeTodosLosDatosAcentosYResponsableReal() throws Exception {
        jdbc.update("update historial_estados set fecha = ? where id = ?",
                OffsetDateTime.parse("2026-11-01T15:30:45Z"), aprobacion.getId());
        em.clear();
        try (var pdf = Loader.loadPDF(descargar("auditor@navesitas.pa"))) {
            String texto = new PDFTextStripper().getText(pdf);
            assertThat(texto).contains("Número de registro: " + nave.getId(), nave.getNombre(), "CARGA",
                    "Arqueo bruto: 499.00 GT", "Tonelaje neto: 250.00 TRN", "Eslora: 24.00 m",
                    "Manga: 8.00 m", "Puntal: 4.00 m", "Naviera Muñoz & Hijos - 60.00 %",
                    "María del Océano - 40.00 %", "Firma Jurídica del Pacífico", "IDN-HU15",
                    "01/11/2026 10:30:45 -05:00", "Funcionario Revisor DGMM",
                    "No constituye patente", "Sin firma digital").doesNotContain("ABANDERADA");
            assertThat(pdf.getSignatureDictionaries()).isEmpty();
            // Evidencia de QA local, fuera del codigo y de los datos de desarrollo.
            pdf.save("target/hu15-constancia-ejemplo.pdf");
            javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(pdf)
                    .renderImageWithDPI(0, 110), "png", new java.io.File("target/hu15-constancia-ejemplo.png"));
        }
    }
    @Test void siguientesDescargasConservanBytesAunqueCambieNombreDelFuncionario() throws Exception {
        byte[] primera = descargar(AGENTE);
        var funcionario = usuarios.findByCorreoIgnoreCase(DGMM).orElseThrow();
        funcionario.setNombreCompleto("Nombre cambiado después de emitir"); usuarios.flush();
        assertThat(descargar(DGMM)).isEqualTo(primera);
        assertThat(descargar("auditor@navesitas.pa")).isEqualTo(primera);
        assertThat(jdbc.queryForObject("select count(*) from constancias_registro where nave_id=?", Long.class, nave.getId())).isEqualTo(1);
    }
    @Test void sinSesion401() throws Exception {
        mvc.perform(get(ruta())).andExpect(status().isUnauthorized());
        assertThat(constancias.existsById(nave.getId())).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"admin@navesitas.pa", "agente-ajeno-hu15@example.com"})
    void otrosReciben403InclusoAntesDeAprobar(String correo) throws Exception {
        if (correo.startsWith("agente-ajeno")) {
            Usuario otro = new Usuario(); otro.setCorreo(correo); otro.setRol(Rol.AGENTE_NAVIERO);
            otro.setNombreCompleto("Agente ajeno"); otro.setPasswordHash("no-usado"); usuarios.saveAndFlush(otro);
        }
        mvc.perform(get(ruta()).with(user(correo))).andExpect(status().isForbidden());
        tramite.cambiarA(EstadoTramite.EN_REVISION); tramites.flush();
        mvc.perform(get(ruta()).with(user(correo))).andExpect(status().isForbidden());
        assertThat(constancias.existsById(nave.getId())).isFalse();
    }
    @Test void administradorAunqueSeaDuenoNoDescarga() throws Exception {
        nave.setRegistradoPor(usuarios.findByCorreoIgnoreCase("admin@navesitas.pa").orElseThrow()); naves.flush();
        mvc.perform(get(ruta()).with(user("admin@navesitas.pa"))).andExpect(status().isForbidden());
    }
    @ParameterizedTest @EnumSource(value=EstadoTramite.class, names="APROBADA", mode=EnumSource.Mode.EXCLUDE)
    void cualquierEstadoNoAprobado409SinCrearConstancia(EstadoTramite estado) throws Exception {
        tramite.cambiarA(estado); tramites.flush();
        mvc.perform(get(ruta()).with(user(AGENTE))).andExpect(status().isConflict());
        assertThat(constancias.existsById(nave.getId())).isFalse();
    }
    @Test void inexistente404EIdInvalido400() throws Exception {
        mvc.perform(get("/api/naves/999999999/constancia").with(user(AGENTE))).andExpect(status().isNotFound());
        mvc.perform(get("/api/naves/invalido/constancia").with(user(AGENTE))).andExpect(status().isBadRequest());
    }
    @Test void sinEvidenciaHistoricaNoInventaFuncionario() throws Exception {
        historial.delete(aprobacion); historial.flush();
        mvc.perform(get(ruta()).accept(MediaType.APPLICATION_PDF, MediaType.APPLICATION_JSON)
                .with(user(AGENTE))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", containsString("evidencia")));
        assertThat(constancias.existsById(nave.getId())).isFalse();
    }
    @Test void faltaAgente409() throws Exception {
        agentes.deleteAll(agentes.findByNaveIdOrderByFechaDesignacionDesc(nave.getId())); agentes.flush();
        mvc.perform(get(ruta()).with(user(AGENTE))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", containsString("agente residente")));
    }
    @Test void faltanPropietarios409() throws Exception {
        participaciones.deleteAll(participaciones.findByNaveIdOrderByIdAsc(nave.getId())); participaciones.flush();
        mvc.perform(get(ruta()).with(user(AGENTE))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", containsString("propietarios")));
    }
    @Test void hashAlteradoImpideEntregarArchivo() throws Exception {
        descargar(AGENTE);
        jdbc.update("update constancias_registro set hash_sha256=? where nave_id=?", "0".repeat(64), nave.getId()); em.clear();
        mvc.perform(get(ruta()).with(user(AGENTE))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", containsString("integridad")));
    }
    @Test void propietariosLargosEnVariasPaginasSinPerderElUltimo() throws Exception {
        var primera = participaciones.findByNaveIdOrderByIdAsc(nave.getId()).getFirst();
        primera.setPorcentaje(new BigDecimal("59.44")); participaciones.flush();
        for (int i=0; i<55; i++) propietario("Propietario " + i + " " + "L".repeat(120), "0.01");
        propietario("ÚLTIMO PROPIETARIO HU15", "0.01");
        try (var pdf = Loader.loadPDF(descargar(AGENTE))) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            assertThat(new PDFTextStripper().getText(pdf)).contains("ÚLTIMO PROPIETARIO HU15");
        }
    }
    @Test void caracteresNoRepresentablesNoGeneranPdfConNombresCorruptos() throws Exception {
        nave.setNombre("Prueba Unicode \uD83D\uDEA2"); naves.flush();
        mvc.perform(get(ruta()).with(user(AGENTE))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", containsString("caracteres")));
        assertThat(constancias.existsById(nave.getId())).isFalse();
    }
    @Test void constanciaEmitidaNoSeEntregaSiYaNoEstaAprobada() throws Exception {
        descargar(AGENTE);
        tramite.cambiarA(EstadoTramite.EN_REVISION); tramites.flush();
        mvc.perform(get(ruta()).with(user(AGENTE))).andExpect(status().isConflict());
    }
    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void primerasDescargasConcurrentesConservanUnaSolaConstancia() throws Exception {
        // Esta prueba necesita commits reales visibles entre dos conexiones.
        // Solo sus propios datos se eliminan al terminar, incluso si falla.
        Long id = nave.getId();
        try (var ejecutor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var inicio = new java.util.concurrent.CountDownLatch(1);
            var primera = ejecutor.submit(() -> { inicio.await(); return servicio.descargar(id, AGENTE); });
            var segunda = ejecutor.submit(() -> { inicio.await(); return servicio.descargar(id, DGMM); });
            inicio.countDown();
            var a = primera.get(30, java.util.concurrent.TimeUnit.SECONDS);
            var b = segunda.get(30, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(a.contenido()).isEqualTo(b.contenido());
            assertThat(a.hashSha256()).isEqualTo(b.hashSha256());
            assertThat(jdbc.queryForObject("select count(*) from constancias_registro where nave_id=?", Long.class, id)).isEqualTo(1);
        } finally {
            new org.springframework.transaction.support.TransactionTemplate(transacciones).executeWithoutResult(tx -> {
                var duenos = jdbc.queryForList("select propietario_id from nave_propietario where nave_id=?", Long.class, id);
                jdbc.update("delete from constancias_registro where nave_id=?", id);
                jdbc.update("delete from historial_estados where tramite_id=?", tramite.getId());
                jdbc.update("delete from agentes_residentes where nave_id=?", id);
                jdbc.update("delete from nave_propietario where nave_id=?", id);
                jdbc.update("delete from tramites where nave_id=?", id);
                jdbc.update("delete from nave where id=?", id);
                duenos.forEach(p -> jdbc.update("delete from propietarios where id=?", p));
            });
        }
    }
    @Test void aprobarDejaNaveRegistradaYHabilitaConstanciaSinSmc() throws Exception {
        historial.delete(aprobacion); historial.flush();
        tramite.cambiarA(EstadoTramite.EN_REVISION); tramites.flush();
        nave.setEslora(new BigDecimal("23.99")); naves.flush();
        for (var tipo : new TipoDocumento[]{TipoDocumento.CERTIFICADO_PROPIEDAD, TipoDocumento.PODER_NOTARIAL}) {
            Documento d = new Documento(); d.setNave(nave); d.setTipo(tipo); d.setNombreArchivo("prueba.pdf");
            d.setRuta("hu15-" + UUID.randomUUID() + ".pdf"); d.setHashSha256("0".repeat(64));
            d.setTamanoBytes(20L); d.setVersion(1); d.setCreadoPor(nave.getRegistradoPor());
            d.marcarRevisado(EstadoDocumento.APROBADO, null, usuarios.findByCorreoIgnoreCase(DGMM).orElseThrow(), OffsetDateTime.now());
            documentos.saveAndFlush(d);
        }
        mvc.perform(post("/api/naves/" + nave.getId() + "/tramite/estado").with(user(DGMM)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"accion\":\"APROBAR\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("APROBADA"));
        assertThat(naves.findById(nave.getId()).orElseThrow().getEstado()).isEqualTo(EstadoNave.REGISTRADA);
        descargar(AGENTE);
        mvc.perform(post("/api/naves/" + nave.getId() + "/agente-residente").with(user(AGENTE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"nombre":"Otro agente","telefono":"00000000","correo":"otro@example.com"}
                    """)).andExpect(status().isConflict());
    }
}
