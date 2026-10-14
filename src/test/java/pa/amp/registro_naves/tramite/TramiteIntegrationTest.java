package pa.amp.registro_naves.tramite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import pa.amp.registro_naves.documento.DocumentoRepository;
import pa.amp.registro_naves.documento.EstadoDocumento;
import pa.amp.registro_naves.documento.TipoDocumento;
import pa.amp.registro_naves.nave.Nave;
import pa.amp.registro_naves.nave.NaveRepository;
import pa.amp.registro_naves.usuario.Rol;
import pa.amp.registro_naves.usuario.Usuario;
import pa.amp.registro_naves.usuario.UsuarioRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-09 (estado del tramite) y HU-10 (naves por estado), contra la base
 * real. @Transactional: todo lo que crea se deshace al terminar.
 *
 * Cubre lo que pide el plan: 401, 404, 403 (nave ajena y agente que
 * aprueba), 409, 400, historial, que HU-03 crea un solo tramite y el
 * aislamiento entre dos agentes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TramiteIntegrationTest {

    static final String AGENTE  = "agente@navesitas.pa";
    static final String DGMM    = "revisor@navesitas.pa";
    static final String AUDITOR = "auditor@navesitas.pa";
    /** Segundo agente, creado en cada prueba y deshecho al terminar. */
    static final String OTRO_AGENTE = "otro.agente.sprint5@navesitas.pa";

    @Autowired MockMvc mvc;
    @Autowired NaveRepository naves;
    @Autowired UsuarioRepository usuarios;
    @Autowired TramiteRepository tramites;
    @Autowired HistorialEstadoRepository historial;
    @Autowired JdbcTemplate jdbc;
    @Autowired DocumentoRepository documentos;

    Long nave;

    @BeforeEach
    void preparar() throws Exception {
        if (!usuarios.existsByCorreoIgnoreCase(OTRO_AGENTE)) {
            Usuario otro = new Usuario();
            otro.setCorreo(OTRO_AGENTE);
            otro.setPasswordHash("!solo-pruebas");
            otro.setNombreCompleto("Otro Agente de Prueba");
            otro.setRol(Rol.AGENTE_NAVIERO);
            otro.setCorreoValidado(true);
            usuarios.save(otro);
        }
        nave = crearNave("Tramite Prueba", AGENTE);
        // PO (13-10-2026): sin los 4 documentos obligatorios no se admite.
        completarExpediente(nave, AGENTE);
    }

    // ------------------------------------------------------------------
    // HU-03 crea el tramite
    // ------------------------------------------------------------------

    @Test
    void registrarLaNaveCreaUnSoloTramiteEnSolicitudPresentada() throws Exception {
        Tramite tramite = tramites.findByNaveId(nave).orElseThrow();
        assertThat(tramite.getEstado()).isEqualTo(EstadoTramite.SOLICITUD_PRESENTADA);
        assertThat(tramites.findAll().stream().filter(t -> t.getNave().getId().equals(nave)))
                .hasSize(1);

        // P9: la entrada inicial, sin estado anterior y a nombre de quien registro.
        mvc.perform(get(ruta(nave) + "/historial").with(user(AGENTE)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(1)))
           .andExpect(jsonPath("$[0].accion").value("PRESENTAR"))
           .andExpect(jsonPath("$[0].estadoAnterior").doesNotExist())
           .andExpect(jsonPath("$[0].estadoNuevo").value("SOLICITUD_PRESENTADA"))
           .andExpect(jsonPath("$[0].rol").value("AGENTE_NAVIERO"));
    }

    @Test
    void laRespuestaDelRegistroRotulaLosDosEstadosPorSeparado() throws Exception {
        mvc.perform(post("/api/naves").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(naveJson(unico("Dos Estados"))))
           .andExpect(status().isCreated())
           // TS02/TC_HU03_01 siguen viendo el estado de la nave.
           .andExpect(jsonPath("$.estado").value("REGISTRADA"))
           .andExpect(jsonPath("$.estadoTramite").value("SOLICITUD_PRESENTADA"));
    }

    // ------------------------------------------------------------------
    // HU-09 — consulta
    // ------------------------------------------------------------------

    @Test
    void cadaRolVeSoloSusAcciones() throws Exception {
        mvc.perform(get(ruta(nave)).with(user(AGENTE)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.naveId").value(nave))
           .andExpect(jsonPath("$.estado").value("SOLICITUD_PRESENTADA"))
           .andExpect(jsonPath("$.esFinal").value(false))
           .andExpect(jsonPath("$.acciones", hasSize(1)))
           .andExpect(jsonPath("$.acciones[0]").value("DESISTIR"));

        mvc.perform(get(ruta(nave)).with(user(DGMM)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.acciones", hasSize(1)))
           .andExpect(jsonPath("$.acciones[0]").value("ADMITIR"));

        // P8: el auditor lee, sin acciones.
        mvc.perform(get(ruta(nave)).with(user(AUDITOR)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.acciones", hasSize(0)));
        mvc.perform(get(ruta(nave) + "/historial").with(user(AUDITOR)))
           .andExpect(status().isOk());
    }

    @Test
    void sinSesionEs401() throws Exception {
        mvc.perform(get(ruta(nave))).andExpect(status().isUnauthorized());
        mvc.perform(get(ruta(nave) + "/historial")).andExpect(status().isUnauthorized());
        accion(nave, null, "ADMITIR", null).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/naves").param("estado", "EN_REVISION")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/naves/conteo-por-estado")).andExpect(status().isUnauthorized());
    }

    @Test
    void naveInexistenteEs404() throws Exception {
        mvc.perform(get(ruta(999_999_999L)).with(user(DGMM))).andExpect(status().isNotFound());
        mvc.perform(get(ruta(999_999_999L) + "/historial").with(user(AGENTE))).andExpect(status().isNotFound());
        accion(999_999_999L, DGMM, "ADMITIR", null).andExpect(status().isNotFound());
    }

    @Test
    void tramiteDeOtroAgenteEs403() throws Exception {
        mvc.perform(get(ruta(nave)).with(user(OTRO_AGENTE))).andExpect(status().isForbidden());
        mvc.perform(get(ruta(nave) + "/historial").with(user(OTRO_AGENTE))).andExpect(status().isForbidden());
        accion(nave, OTRO_AGENTE, "DESISTIR", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("La nave indicada no le pertenece."));
        assertThat(estado(nave)).isEqualTo(EstadoTramite.SOLICITUD_PRESENTADA);
    }

    // ------------------------------------------------------------------
    // HU-09 — cambios de estado
    // ------------------------------------------------------------------

    @Test
    void elAgenteQueIntentaAprobarORechazarRecibe403() throws Exception {
        accion(nave, DGMM, "ADMITIR", null).andExpect(status().isOk());

        // P2: 403 por el rol, aunque la transicion exista en este estado.
        accion(nave, AGENTE, "APROBAR", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Solo un funcionario de la DGMM puede aprobar el trámite."));
        accion(nave, AGENTE, "RECHAZAR", "No me gusta")
                .andExpect(status().isForbidden());
        assertThat(estado(nave)).isEqualTo(EstadoTramite.EN_REVISION);
    }

    @Test
    void laDgmmNoSubsanaNiDesiste() throws Exception {
        accion(nave, DGMM, "DESISTIR", null).andExpect(status().isForbidden());
        accion(nave, DGMM, "ADMITIR", null).andExpect(status().isOk());
        accion(nave, DGMM, "OBSERVAR", "Falta el ITC").andExpect(status().isOk());
        accion(nave, DGMM, "SUBSANAR", null).andExpect(status().isForbidden());
    }

    @Test
    void elAuditorNoCambiaNada() throws Exception {
        accion(nave, AUDITOR, "ADMITIR", null).andExpect(status().isForbidden());
        accion(nave, AUDITOR, "DESISTIR", null).andExpect(status().isForbidden());
    }

    @Test
    void transicionFueraDeLaMaquinaEs409() throws Exception {
        // Aprobar sin admitir: la DGMM tiene el permiso, la maquina no la transicion.
        accion(nave, DGMM, "APROBAR", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(
                        "No se puede aprobar el trámite cuando está en Solicitud presentada."));
        // Subsanar sin estar observada.
        accion(nave, AGENTE, "SUBSANAR", null).andExpect(status().isConflict());
        assertThat(estado(nave)).isEqualTo(EstadoTramite.SOLICITUD_PRESENTADA);
        assertThat(historial.countByTramiteId(tramites.findByNaveId(nave).orElseThrow().getId()))
                .isEqualTo(1);
    }

    @Test
    void unEstadoFinalNoAdmiteMasCambios() throws Exception {
        accion(nave, DGMM, "ADMITIR", null).andExpect(status().isOk());
        accion(nave, DGMM, "APROBAR", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"))
                .andExpect(jsonPath("$.esFinal").value(true))
                .andExpect(jsonPath("$.acciones", hasSize(0)));

        accion(nave, DGMM, "RECHAZAR", "Tarde").andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("El trámite ya terminó (Aprobada); no admite más cambios."));
        accion(nave, DGMM, "OBSERVAR", "Tarde").andExpect(status().isConflict());
        accion(nave, AGENTE, "DESISTIR", null).andExpect(status().isConflict());
    }

    @Test
    void elAgenteDesisteDesdeSolicitudPresentada() throws Exception {
        accion(nave, AGENTE, "DESISTIR", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DESISTIDA"));
        accion(nave, DGMM, "ADMITIR", null).andExpect(status().isConflict());
    }

    @Test
    void observarYRechazarSinObservacionEs400() throws Exception {
        accion(nave, DGMM, "ADMITIR", null).andExpect(status().isOk());

        accion(nave, DGMM, "OBSERVAR", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.observacion").exists());
        accion(nave, DGMM, "RECHAZAR", "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.observacion").exists());
        assertThat(estado(nave)).isEqualTo(EstadoTramite.EN_REVISION);
    }

    @Test
    void cuerpoInvalidoEs400() throws Exception {
        // Accion que no existe: falla al leer el cuerpo.
        accion(nave, DGMM, "VOLAR", null).andExpect(status().isBadRequest());
        // Sin accion.
        mvc.perform(post(ruta(nave) + "/estado").with(user(DGMM))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.campos.accion").exists());
        // Acciones del sistema: no se piden a mano.
        accion(nave, DGMM, "PRESENTAR", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.accion").exists());
        accion(nave, AGENTE, "MIGRACION", null).andExpect(status().isBadRequest());
        // Observacion demasiado larga.
        accion(nave, DGMM, "ADMITIR", "x".repeat(1001))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.observacion").exists());
        // ID de nave que no es numero.
        mvc.perform(get("/api/naves/abc/tramite").with(user(DGMM))).andExpect(status().isBadRequest());
    }

    /** El flujo de TC_HU09_06: la DGMM admite y observa, el agente subsana. */
    @Test
    void flujoCompletoQuedaEnElHistorial() throws Exception {
        accion(nave, DGMM, "ADMITIR", null).andExpect(status().isOk());
        accion(nave, DGMM, "OBSERVAR", "  Falta el certificado de arqueo (ITC).  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("OBSERVADA"));

        // El agente ve sus dos botones en Observada.
        mvc.perform(get(ruta(nave)).with(user(AGENTE)))
           .andExpect(jsonPath("$.acciones", hasSize(2)))
           .andExpect(jsonPath("$.acciones", hasItem("SUBSANAR")))
           .andExpect(jsonPath("$.acciones", hasItem("DESISTIR")));

        accion(nave, AGENTE, "SUBSANAR", "Cargado el ITC vigente.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_REVISION"));

        mvc.perform(get(ruta(nave) + "/historial").with(user(AGENTE)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(4)))
           .andExpect(jsonPath("$[0].accion").value("PRESENTAR"))
           .andExpect(jsonPath("$[1].accion").value("ADMITIR"))
           .andExpect(jsonPath("$[1].estadoAnterior").value("SOLICITUD_PRESENTADA"))
           .andExpect(jsonPath("$[1].estadoNuevo").value("EN_REVISION"))
           .andExpect(jsonPath("$[1].rol").value("FUNCIONARIO_DGMM"))
           .andExpect(jsonPath("$[1].fecha").exists())
           .andExpect(jsonPath("$[1].usuario").exists())
           .andExpect(jsonPath("$[2].accion").value("OBSERVAR"))
           .andExpect(jsonPath("$[2].observacion").value("Falta el certificado de arqueo (ITC)."))
           .andExpect(jsonPath("$[3].accion").value("SUBSANAR"))
           .andExpect(jsonPath("$[3].estadoAnterior").value("OBSERVADA"))
           .andExpect(jsonPath("$[3].estadoNuevo").value("EN_REVISION"))
           .andExpect(jsonPath("$[3].rol").value("AGENTE_NAVIERO"));
    }

    @Test
    void laDgmmYElAuditorVenLaFichaDeCualquierNave() throws Exception {
        mvc.perform(get("/api/naves/" + nave).with(user(DGMM)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.estadoTramite").value("SOLICITUD_PRESENTADA"));
        mvc.perform(get("/api/naves/" + nave).with(user(AUDITOR))).andExpect(status().isOk());
        // Otro agente sigue sin verla.
        mvc.perform(get("/api/naves/" + nave).with(user(OTRO_AGENTE))).andExpect(status().isForbidden());
        // Y la DGMM sigue sin poder tocar las camaras del agente.
        mvc.perform(get("/api/naves/" + nave + "/propietarios").with(user(DGMM)))
           .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // HU-10 — naves por estado
    // ------------------------------------------------------------------

    @Test
    void elFiltroSoloDevuelveNavesDelAgente() throws Exception {
        Long ajena = crearNave("Tramite Ajeno", OTRO_AGENTE);
        completarExpediente(ajena, OTRO_AGENTE);
        accion(nave, DGMM, "ADMITIR", null).andExpect(status().isOk());
        accion(ajena, DGMM, "ADMITIR", null).andExpect(status().isOk());

        mvc.perform(get("/api/naves").param("estado", "EN_REVISION").with(user(AGENTE)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[*].id", hasItem(nave.intValue())))
           .andExpect(jsonPath("$[*].id", not(hasItem(ajena.intValue()))))
           .andExpect(jsonPath("$[*].estadoTramite", everyItem(is("EN_REVISION"))));

        mvc.perform(get("/api/naves").param("estado", "EN_REVISION").with(user(OTRO_AGENTE)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(1)))
           .andExpect(jsonPath("$[0].id").value(ajena));
    }

    /**
     * Issue #9: antes exigia que TODAS las naves de agente@ estuvieran en
     * REGISTRADA y fallaba si la base de desarrollo tenia otra. Ahora mira
     * solo la nave de la prueba: el estado de la nave no lo toca el tramite.
     */
    @Test
    void sinParametroLaListaNoCambia() throws Exception {
        accion(nave, DGMM, "ADMITIR", null).andExpect(status().isOk());
        int todas = naves.findByRegistradoPorCorreoIgnoreCaseOrderByIdAsc(AGENTE).size();

        mvc.perform(get("/api/naves").with(user(AGENTE)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(todas)))
           .andExpect(jsonPath("$[?(@.id == " + nave + ")].estado", contains("REGISTRADA")))
           .andExpect(jsonPath("$[?(@.id == " + nave + ")].estadoTramite", contains("EN_REVISION")));
    }

    @Test
    void elConteoCoincideConLasNaves() throws Exception {
        Long segunda = crearNave("Tramite Conteo", OTRO_AGENTE);
        completarExpediente(segunda, OTRO_AGENTE);
        Long tercera = crearNave("Tramite Conteo B", OTRO_AGENTE);
        crearNave("Tramite Conteo C", OTRO_AGENTE);
        accion(segunda, DGMM, "ADMITIR", null).andExpect(status().isOk());
        accion(tercera, OTRO_AGENTE, "DESISTIR", null).andExpect(status().isOk());

        mvc.perform(get("/api/naves/conteo-por-estado").with(user(OTRO_AGENTE)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.total").value(3))
           .andExpect(jsonPath("$.estados.SOLICITUD_PRESENTADA").value(1))
           .andExpect(jsonPath("$.estados.EN_REVISION").value(1))
           .andExpect(jsonPath("$.estados.DESISTIDA").value(1))
           .andExpect(jsonPath("$.estados.OBSERVADA").value(0))
           .andExpect(jsonPath("$.estados.APROBADA").value(0))
           .andExpect(jsonPath("$.estados.RECHAZADA").value(0));

        // Cada numero coincide con lo que devuelve el filtro.
        mvc.perform(get("/api/naves").param("estado", "EN_REVISION").with(user(OTRO_AGENTE)))
           .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/naves").param("estado", "APROBADA").with(user(OTRO_AGENTE)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void estadoInvalidoEs400NoUn500() throws Exception {
        for (String malo : new String[] {"VOLANDO", "en_revision", "1", "' OR '1'='1", "REGISTRADA"}) {
            mvc.perform(get("/api/naves").param("estado", malo).with(user(AGENTE)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.mensaje").exists());
        }
    }

    // ------------------------------------------------------------------
    // Reglas de la PO (13-10-2026) y SEC-08
    // ------------------------------------------------------------------

    @Test
    void admitirSinDocumentosDa409ConLosFaltantesEnOrden() throws Exception {
        Long sinDocs = crearNave("Tramite Sin Documentos", AGENTE);

        accion(sinDocs, DGMM, "ADMITIR", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(
                        "Faltan documentos obligatorios: CERTIFICADO_PROPIEDAD, PODER_NOTARIAL, ITC, SMC"))
                .andExpect(jsonPath("$.faltantes", contains(
                        "CERTIFICADO_PROPIEDAD", "PODER_NOTARIAL", "ITC", "SMC")));
        assertThat(estado(sinDocs)).isEqualTo(EstadoTramite.SOLICITUD_PRESENTADA);
        assertThat(historial.countByTramiteId(tramites.findByNaveId(sinDocs).orElseThrow().getId()))
                .isEqualTo(1);
    }

    @Test
    void losFaltantesSalenEnElOrdenOficialSinImportarElDeCarga() throws Exception {
        Long parcial = crearNave("Tramite Parcial", AGENTE);
        completarExpediente(parcial, AGENTE);
        // Quedan vigentes solo SMC y CERTIFICADO_PROPIEDAD.
        jdbc.update("UPDATE documentos SET vigente = false WHERE nave_id = ? AND tipo IN ('ITC', 'PODER_NOTARIAL')",
                parcial);

        accion(parcial, DGMM, "ADMITIR", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.faltantes", contains("PODER_NOTARIAL", "ITC")));
    }

    @Test
    void unDocumentoObservadoCuentaComoFaltante() throws Exception {
        observar("ITC");

        accion(nave, DGMM, "ADMITIR", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.faltantes", contains("ITC")));
    }

    /** La misma regla en las dos transiciones que llevan a "En revision". */
    @Test
    void subsanarTambienExigeLosDocumentosYPasaAlCorregirlos() throws Exception {
        accion(nave, DGMM, "ADMITIR", null).andExpect(status().isOk());
        accion(nave, DGMM, "OBSERVAR", "El ITC no corresponde.").andExpect(status().isOk());
        observar("ITC");

        accion(nave, AGENTE, "SUBSANAR", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.faltantes", contains("ITC")));
        assertThat(estado(nave)).isEqualTo(EstadoTramite.OBSERVADA);

        // El agente carga la version corregida (vigente, PENDIENTE) y subsana.
        cargarDocumento(nave, AGENTE, "ITC").andExpect(status().isCreated());
        accion(nave, AGENTE, "SUBSANAR", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_REVISION"));
    }

    @Test
    void conElTramiteTerminadoElExpedienteEsDeSoloLectura() throws Exception {
        accion(nave, DGMM, "ADMITIR", null).andExpect(status().isOk());
        accion(nave, DGMM, "APROBAR", null).andExpect(status().isOk());
        verificarSoloLectura(nave, "Aprobada");

        Long desistida = crearNave("Tramite Desistido", AGENTE);
        completarExpediente(desistida, AGENTE);
        accion(desistida, AGENTE, "DESISTIR", null).andExpect(status().isOk());
        verificarSoloLectura(desistida, "Desistida");
    }

    /** Quien no es dueno sigue recibiendo 403, no se le cuenta que el tramite termino. */
    @Test
    void enUnExpedienteCerradoAjenoSigueSiendo403() throws Exception {
        accion(nave, AGENTE, "DESISTIR", null).andExpect(status().isOk());
        cargarDocumento(nave, OTRO_AGENTE, "ITC").andExpect(status().isForbidden());
    }

    /** Issue #10: si la DGMM registrara naves, podria aprobar su propia solicitud. */
    @Test
    void soloElAgenteNavieroRegistraNaves() throws Exception {
        for (String usuario : new String[] {DGMM, AUDITOR}) {
            String nombre = unico("No Agente");
            mvc.perform(post("/api/naves").with(user(usuario))
                            .contentType(MediaType.APPLICATION_JSON).content(naveJson(nombre)))
               .andExpect(status().isForbidden())
               .andExpect(jsonPath("$.mensaje").value("Solo un agente naviero puede registrar naves."));
            assertThat(naves.findByNombreNormalizado(Nave.normalizar(nombre))).isEmpty();
        }
    }

    /** SEC-08: el caracter nulo ya no llega a PostgreSQL (antes, 500). */
    @Test
    void unaObservacionConCaracterNuloEs400() throws Exception {
        accion(nave, DGMM, "ADMITIR", null).andExpect(status().isOk());

        accion(nave, DGMM, "OBSERVAR", "nulo\\u0000intermedio")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El texto contiene caracteres no permitidos."));
        assertThat(estado(nave)).isEqualTo(EstadoTramite.EN_REVISION);
    }

    /** SEC-08: antes respondia 409 "Nombre no disponible", un mensaje enganoso. */
    @Test
    void unCampoDeLaNaveConCaracterNuloEs400() throws Exception {
        String cuerpo = naveJson(unico("Nulo Lugar"))
                .replace("Astillero de prueba", "Astillero\\u0000X");
        mvc.perform(post("/api/naves").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.mensaje").value("El texto contiene caracteres no permitidos."));
    }

    /**
     * Por JPA y no por SQL: el documento ya esta en la sesion de esta
     * transaccion y un UPDATE directo no se veria. (Que la DGMM observe
     * documentos llega en el Sprint 6.)
     */
    private void observar(String tipo) {
        var documento = documentos.findByNaveIdAndTipoAndVigenteTrue(nave, TipoDocumento.valueOf(tipo))
                .orElseThrow();
        documento.setEstado(EstadoDocumento.OBSERVADO);
        documentos.saveAndFlush(documento);
    }

    private void verificarSoloLectura(Long naveId, String estado) throws Exception {
        String mensaje = "El trámite ya terminó (" + estado + "); el expediente es de solo lectura.";
        cargarDocumento(naveId, AGENTE, "ITC")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(mensaje));
        mvc.perform(post("/api/propietarios").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                               {"nombre": "Naviera Tarde S.A.", "tipo": "JURIDICA",
                                "nacionalidad": "Panamena", "domicilio": "Calle 50",
                                "paisConstitucion": "Panama", "naveId": %d, "porcentaje": 1}
                               """.formatted(naveId)))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.mensaje").value(mensaje));
        mvc.perform(post("/api/naves/" + naveId + "/agente-residente").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                               {"nombre": "Bufete Tarde", "idoneidad": "IDN-1010",
                                "telefono": "+507 200-1234", "correo": "tarde@bufete.com.pa"}
                               """))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.mensaje").value(mensaje));
        // Leer sigue permitido.
        mvc.perform(get("/api/naves/" + naveId + "/documentos").with(user(AGENTE)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(4)));
    }

    // ------------------------------------------------------------------

    private static String ruta(Long naveId) {
        return "/api/naves/" + naveId + "/tramite";
    }

    private ResultActions accion(Long naveId, String usuario, String accion, String observacion)
            throws Exception {
        String cuerpo = observacion == null
                ? "{\"accion\":\"" + accion + "\"}"
                : "{\"accion\":\"" + accion + "\",\"observacion\":\"" + observacion + "\"}";
        var peticion = post(ruta(naveId) + "/estado")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        if (usuario != null) {
            peticion = peticion.with(user(usuario));
        }
        return mvc.perform(peticion);
    }

    private EstadoTramite estado(Long naveId) {
        return tramites.findByNaveId(naveId).orElseThrow().getEstado();
    }

    private static String unico(String base) {
        return base + " " + System.nanoTime();
    }

    private Long crearNave(String base, String usuario) throws Exception {
        String nombre = unico(base);
        mvc.perform(post("/api/naves").with(user(usuario))
                        .contentType(MediaType.APPLICATION_JSON).content(naveJson(nombre)))
           .andExpect(status().isCreated());
        return naves.findByNombreNormalizado(Nave.normalizar(nombre))
                .map(Nave::getId).orElseThrow();
    }

    private static String naveJson(String nombre) {
        return """
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
    }

    /**
     * Deja la nave lista para pasar a "En revision": propietario, agente
     * residente (precondicion de HU-07) y los 4 documentos obligatorios.
     */
    private void completarExpediente(Long naveId, String usuario) throws Exception {
        mvc.perform(post("/api/propietarios").with(user(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                               {"nombre": "Naviera Tramite S.A.", "tipo": "JURIDICA",
                                "nacionalidad": "Panamena", "domicilio": "Calle 50",
                                "paisConstitucion": "Panama", "naveId": %d}
                               """.formatted(naveId)))
           .andExpect(status().isCreated());
        mvc.perform(post("/api/naves/" + naveId + "/agente-residente").with(user(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                               {"nombre": "Bufete Tramite", "idoneidad": "IDN-0909",
                                "telefono": "+507 200-1234", "correo": "tramite@bufete.com.pa"}
                               """))
           .andExpect(status().isCreated());
        for (TipoDocumento tipo : TipoDocumento.OBLIGATORIOS) {
            cargarDocumento(naveId, usuario, tipo.name()).andExpect(status().isCreated());
        }
    }

    private ResultActions cargarDocumento(Long naveId, String usuario, String tipo) throws Exception {
        MockMultipartFile pdf = new MockMultipartFile("archivo", tipo.toLowerCase() + ".pdf",
                "application/pdf", ("%PDF-1.4\n%" + tipo + "\n%%EOF\n").getBytes());
        return mvc.perform(multipart("/api/naves/" + naveId + "/documentos")
                .file(pdf).param("tipo", tipo).with(user(usuario)));
    }
}
