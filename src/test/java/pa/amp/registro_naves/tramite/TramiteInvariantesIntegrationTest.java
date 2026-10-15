package pa.amp.registro_naves.tramite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import pa.amp.registro_naves.nave.Nave;
import pa.amp.registro_naves.nave.NaveRepository;
import pa.amp.registro_naves.usuario.Rol;
import pa.amp.registro_naves.usuario.Usuario;
import pa.amp.registro_naves.usuario.UsuarioRepository;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 5 (QA) — lo que el plan de pruebas pide y TramiteIntegrationTest
 * no cubre: la V6 (invariante, idempotencia y mapeo P3), la unicidad por
 * nave en la base, el 409 por cambio simultaneo, el rol ADMINISTRADOR y el
 * agente sin naves en HU-10.
 *
 * No depende de los usuarios ni de las naves de demo: crea los suyos y
 * @Transactional los deshace al terminar. Las invariantes de la V6 se
 * cumplen con cualquier dato que haya en la base.
 *
 * Ninguna prueba admite ni subsana, asi que la regla de documentos
 * obligatorios (pendiente) no las afecta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TramiteInvariantesIntegrationTest {

    static final String AGENTE = "qa.agente.sprint5@navesitas.pa";
    static final String AGENTE_SIN_NAVES = "qa.agente.vacio.sprint5@navesitas.pa";
    static final String DGMM = "qa.dgmm.sprint5@navesitas.pa";
    static final String ADMIN = "qa.admin.sprint5@navesitas.pa";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired NaveRepository naves;
    @Autowired UsuarioRepository usuarios;
    @Autowired TramiteRepository tramites;
    @Autowired HistorialEstadoRepository historial;
    @Autowired TramiteService servicio;

    Long nave;

    @BeforeEach
    void preparar() throws Exception {
        crearUsuario(AGENTE, Rol.AGENTE_NAVIERO);
        crearUsuario(AGENTE_SIN_NAVES, Rol.AGENTE_NAVIERO);
        crearUsuario(DGMM, Rol.FUNCIONARIO_DGMM);
        crearUsuario(ADMIN, Rol.ADMINISTRADOR);
        nave = crearNave("QA Invariantes");
    }

    // ------------------------------------------------------------------
    // Migracion V6
    // ------------------------------------------------------------------

    /** Plan: "tras V6, cada nave tiene exactamente un tramite". */
    @Test
    void cadaNaveTieneExactamenteUnTramite() {
        Integer sinUno = jdbc.queryForObject("""
                SELECT count(*) FROM nave n
                 WHERE (SELECT count(*) FROM tramites t WHERE t.nave_id = n.id) <> 1
                """, Integer.class);
        assertThat(sinUno).isZero();
    }

    /** P9: todo tramite empieza con una sola entrada sin estado anterior, la primera. */
    @Test
    void cadaTramiteEmpiezaConUnaSolaEntradaInicial() {
        Integer malos = jdbc.queryForObject("""
                SELECT count(*) FROM tramites t
                 WHERE (SELECT count(*) FROM historial_estados h
                         WHERE h.tramite_id = t.id AND h.estado_anterior IS NULL) <> 1
                    OR (SELECT h.estado_anterior FROM historial_estados h
                         WHERE h.tramite_id = t.id
                         ORDER BY h.fecha, h.id LIMIT 1) IS NOT NULL
                """, Integer.class);
        assertThat(malos).isZero();
    }

    /** D3: la V6 es idempotente; ejecutarla otra vez no crea ni duplica nada. */
    @Test
    void laV6EjecutadaDosVecesNoDuplica() throws Exception {
        long tramitesAntes = contar("tramites");
        long historialAntes = contar("historial_estados");

        ejecutarV6();

        assertThat(contar("tramites")).isEqualTo(tramitesAntes);
        assertThat(contar("historial_estados")).isEqualTo(historialAntes);
    }

    /** P3 (confirmado por la PO el 13-10-2026): estado de la nave -> estado inicial del tramite. */
    @ParameterizedTest(name = "nave {0} -> tramite {1}")
    @CsvSource({
            "REGISTRADA,          SOLICITUD_PRESENTADA",
            "DOCUMENTOS_CARGADOS, SOLICITUD_PRESENTADA",
            "EN_REVISION,         EN_REVISION",
            "ABANDERADA,          APROBADA",
            "RECHAZADA,           RECHAZADA"
    })
    void laV6RellenaSegunElEstadoDeLaNave(String estadoNave, EstadoTramite esperado) throws Exception {
        // Una nave "anterior a la V6": sin tramite y con el estado indicado.
        jdbc.update("UPDATE nave SET estado = ? WHERE id = ?", estadoNave, nave);
        jdbc.update("DELETE FROM tramites WHERE nave_id = ?", nave);

        ejecutarV6();

        assertThat(jdbc.queryForObject("SELECT estado FROM tramites WHERE nave_id = ?", String.class, nave))
                .isEqualTo(esperado.name());
        var entrada = jdbc.queryForMap("""
                SELECT h.accion, h.estado_anterior, h.estado_nuevo, u.correo
                  FROM historial_estados h
                  JOIN tramites t ON t.id = h.tramite_id
                  JOIN usuario  u ON u.id = h.usuario_id
                 WHERE t.nave_id = ?
                """, nave);
        assertThat(entrada.get("accion")).isEqualTo("MIGRACION");
        assertThat(entrada.get("estado_anterior")).isNull();
        assertThat(entrada.get("estado_nuevo")).isEqualTo(esperado.name());
        assertThat(entrada.get("correo")).isEqualTo(AGENTE);
    }

    // ------------------------------------------------------------------
    // Unicidad (D3) y estados de la base (D2)
    // ------------------------------------------------------------------

    @Test
    void laBaseImpideUnSegundoTramiteParaLaMismaNave() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO tramites (nave_id) VALUES (?)", nave))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_tramites_nave");
    }

    @Test
    void crearParaNoCreaUnSegundoTramite() {
        Tramite primero = tramites.findByNaveId(nave).orElseThrow();
        Usuario agente = usuarios.findByCorreoIgnoreCase(AGENTE).orElseThrow();

        Tramite otraVez = servicio.crearPara(primero.getNave(), agente);

        assertThat(otraVez.getId()).isEqualTo(primero.getId());
        assertThat(historial.countByTramiteId(primero.getId())).isEqualTo(1);
    }

    @Test
    void laBaseRechazaUnEstadoFueraDeLaMaquina() {
        assertThatThrownBy(() -> jdbc.update("UPDATE tramites SET estado = 'BORRADOR' WHERE nave_id = ?", nave))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_tramites_estado");
    }

    // ------------------------------------------------------------------
    // HU-09 — cambio simultaneo y rol ADMINISTRADOR
    // ------------------------------------------------------------------

    /**
     * Bloqueo optimista: el servicio lee el tramite, otro usuario lo cambia
     * antes de que se guarde, y el segundo cambio no se aplica (409).
     */
    @Test
    void unCambioSimultaneoEs409() throws Exception {
        Tramite leido = tramites.findByNaveId(nave).orElseThrow();
        // "Otro usuario" cambia el tramite por fuera de esta sesion de JPA.
        jdbc.update("UPDATE tramites SET version = version + 1 WHERE id = ?", leido.getId());

        accion(nave, AGENTE, "DESISTIR")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(
                        "El trámite cambió mientras tanto. Recargue la página e intente de nuevo."));
    }

    /** D1 no menciona al ADMINISTRADOR: no ve ni cambia el tramite. */
    @Test
    void elAdministradorNoVeNiCambiaElTramite() throws Exception {
        mvc.perform(get(ruta(nave)).with(user(ADMIN))).andExpect(status().isForbidden());
        mvc.perform(get(ruta(nave) + "/historial").with(user(ADMIN))).andExpect(status().isForbidden());
        accion(nave, ADMIN, "ADMITIR").andExpect(status().isForbidden());
        accion(nave, ADMIN, "DESISTIR").andExpect(status().isForbidden());
        assertThat(tramites.findByNaveId(nave).orElseThrow().getEstado())
                .isEqualTo(EstadoTramite.SOLICITUD_PRESENTADA);
    }

    // ------------------------------------------------------------------
    // HU-10 — agente sin naves
    // ------------------------------------------------------------------

    @Test
    void unAgenteSinNavesVeConteoEnCeroYListasVacias() throws Exception {
        mvc.perform(get("/api/naves/conteo-por-estado").with(user(AGENTE_SIN_NAVES)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.total").value(0))
           .andExpect(jsonPath("$.estados.SOLICITUD_PRESENTADA").value(0))
           .andExpect(jsonPath("$.estados.EN_REVISION").value(0))
           .andExpect(jsonPath("$.estados.OBSERVADA").value(0))
           .andExpect(jsonPath("$.estados.APROBADA").value(0))
           .andExpect(jsonPath("$.estados.RECHAZADA").value(0))
           .andExpect(jsonPath("$.estados.DESISTIDA").value(0));

        mvc.perform(get("/api/naves").with(user(AGENTE_SIN_NAVES)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/naves").param("estado", "SOLICITUD_PRESENTADA").with(user(AGENTE_SIN_NAVES)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(0)));
    }

    /** El conteo del agente coincide con sus naves, sin depender de cuantas tenga. */
    @Test
    void elConteoSumaLasNavesDelAgente() throws Exception {
        crearNave("QA Invariantes B");
        accion(nave, AGENTE, "DESISTIR").andExpect(status().isOk());

        mvc.perform(get("/api/naves/conteo-por-estado").with(user(AGENTE)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.total").value(2))
           .andExpect(jsonPath("$.estados.SOLICITUD_PRESENTADA").value(1))
           .andExpect(jsonPath("$.estados.DESISTIDA").value(1));
        mvc.perform(get("/api/naves").param("estado", "DESISTIDA").with(user(AGENTE)))
           .andExpect(jsonPath("$", hasSize(1)))
           .andExpect(jsonPath("$[0].id").value(nave))
           .andExpect(jsonPath("$[0].estadoTramite").value("DESISTIDA"))
           // D4/P6: el estado de la nave no se toca.
           .andExpect(jsonPath("$[0].estado").value("REGISTRADA"));
    }

    // ------------------------------------------------------------------

    private void ejecutarV6() throws Exception {
        // Misma conexion que la transaccion de la prueba: todo se deshace al final.
        Connection conexion = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource);
        ScriptUtils.executeSqlScript(conexion,
                new ClassPathResource("db/migration/V6__tramites_e_historial.sql"));
    }

    private long contar(String tabla) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Long.class);
    }

    private void crearUsuario(String correo, Rol rol) {
        if (usuarios.existsByCorreoIgnoreCase(correo)) {
            return;
        }
        Usuario u = new Usuario();
        u.setCorreo(correo);
        u.setPasswordHash("!solo-pruebas");
        u.setNombreCompleto("QA Sprint 5 " + rol.name());
        u.setRol(rol);
        u.setCorreoValidado(true);
        usuarios.save(u);
    }

    private static String ruta(Long naveId) {
        return "/api/naves/" + naveId + "/tramite";
    }

    private ResultActions accion(Long naveId, String usuario, String accion) throws Exception {
        return mvc.perform(post(ruta(naveId) + "/estado").with(user(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accion\":\"" + accion + "\"}"));
    }

    private Long crearNave(String base) throws Exception {
        String nombre = base + " " + System.nanoTime();
        mvc.perform(post("/api/naves").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
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
                                 """.formatted(nombre)))
           .andExpect(status().isCreated());
        return naves.findByNombreNormalizado(Nave.normalizar(nombre))
                .map(Nave::getId).orElseThrow();
    }
}
