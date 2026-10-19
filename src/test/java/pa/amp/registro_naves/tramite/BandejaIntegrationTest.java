package pa.amp.registro_naves.tramite;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import pa.amp.registro_naves.nave.Nave;
import pa.amp.registro_naves.nave.NaveRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-11 — bandeja de tramites de la DGMM.
 *
 * Cada prueba crea sus propias naves y, cuando necesita una base sin
 * tramites, borra los que haya dentro de su transaccion: @Transactional lo
 * deshace todo al terminar. Asi no depende de lo que tenga la base de
 * desarrollo.
 *
 * Los estados se ponen directo en la tabla porque esta historia solo lee:
 * mover un tramite por la API exige documentos (regla de la PO del
 * Sprint 5) y eso ya lo prueban TramiteIntegrationTest y Katalon.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BandejaIntegrationTest {

    static final String AGENTE  = "agente@navesitas.pa";
    static final String DGMM    = "revisor@navesitas.pa";
    static final String AUDITOR = "auditor@navesitas.pa";
    static final String CLAVE_DEMO = "Navesitas2026*";

    @Autowired MockMvc mvc;
    @Autowired NaveRepository naves;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    // ------------------------------------------------------------------
    // Quien ve la bandeja (D1)
    // ------------------------------------------------------------------

    @Test
    void matrizDeRoles() throws Exception {
        for (String ruta : List.of("/api/tramites", "/api/tramites?estado=EN_REVISION",
                                   "/api/tramites/conteo-por-estado")) {
            mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
            mvc.perform(get(ruta).with(user(AGENTE)))
               .andExpect(status().isForbidden())
               .andExpect(jsonPath("$.mensaje").exists());
            mvc.perform(get(ruta).with(user(DGMM))).andExpect(status().isOk());
            mvc.perform(get(ruta).with(user(AUDITOR))).andExpect(status().isOk());
        }
    }

    /** Orden del equipo 401 -> 400 -> 403: el parametro se valida antes que el rol. */
    @Test
    void estadoInvalidoEs400NoUn500() throws Exception {
        for (String malo : new String[] {"VOLANDO", "en_revision", "1", "REGISTRADA", "' OR '1'='1"}) {
            for (String quien : new String[] {DGMM, AUDITOR, AGENTE}) {
                mvc.perform(get("/api/tramites").param("estado", malo).with(user(quien)))
                   .andExpect(status().isBadRequest())
                   .andExpect(jsonPath("$.mensaje").exists());
            }
        }
        mvc.perform(get("/api/tramites").param("estado", "VOLANDO"))
           .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // Contenido y filtros
    // ------------------------------------------------------------------

    @Test
    void cadaFilaTraeNaveAgenteEstadoUltimaAccionYDias() throws Exception {
        Long nave = crearNave("Bandeja Campos");
        ponerEstado(nave, EstadoTramite.SOLICITUD_PRESENTADA, 3);

        mvc.perform(get("/api/tramites").with(user(DGMM)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[?(@.naveId == " + nave + ")].nave").value(hasItem(
                   naves.findById(nave).orElseThrow().getNombre())))
           .andExpect(jsonPath("$[?(@.naveId == " + nave + ")].agente").value(hasItem("Agente Naviero de Prueba")))
           .andExpect(jsonPath("$[?(@.naveId == " + nave + ")].estado").value(hasItem("SOLICITUD_PRESENTADA")))
           .andExpect(jsonPath("$[?(@.naveId == " + nave + ")].ultimaAccion").exists())
           .andExpect(jsonPath("$[?(@.naveId == " + nave + ")].diasEnEspera").value(hasItem(3)));
    }

    @Test
    void elFiltroFuncionaConCadaUnoDeLosSeisEstados() throws Exception {
        EstadoTramite[] estados = EstadoTramite.values();
        Long[] ids = new Long[estados.length];
        for (int i = 0; i < estados.length; i++) {
            ids[i] = crearNave("Bandeja Filtro " + estados[i].name().replace("_", " "));
            ponerEstado(ids[i], estados[i], 1);
        }

        for (int i = 0; i < estados.length; i++) {
            var resultado = mvc.perform(get("/api/tramites").param("estado", estados[i].name()).with(user(DGMM)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[*].estado", everyItem(is(estados[i].name()))))
               .andExpect(jsonPath("$[*].naveId", hasItem(ids[i].intValue())));
            for (int j = 0; j < estados.length; j++) {
                if (j != i) {
                    resultado.andExpect(jsonPath("$[*].naveId", not(hasItem(ids[j].intValue()))));
                }
            }
        }
    }

    @Test
    void sinFiltroMuestraLoPorAtenderConElMasAntiguoPrimero() throws Exception {
        Long reciente   = crearNave("Bandeja Reciente");
        Long antigua    = crearNave("Bandeja Antigua");
        Long observada  = crearNave("Bandeja Observada");
        Long aprobada   = crearNave("Bandeja Aprobada");
        ponerEstado(reciente,  EstadoTramite.SOLICITUD_PRESENTADA, 2);
        ponerEstado(antigua,   EstadoTramite.EN_REVISION, 9);
        ponerEstado(observada, EstadoTramite.OBSERVADA, 30);
        ponerEstado(aprobada,  EstadoTramite.APROBADA, 40);

        MvcResult r = mvc.perform(get("/api/tramites").with(user(DGMM)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[*].estado", everyItem(is(in(List.of("SOLICITUD_PRESENTADA", "EN_REVISION"))))))
           .andExpect(jsonPath("$[*].naveId", hasItem(reciente.intValue())))
           .andExpect(jsonPath("$[*].naveId", hasItem(antigua.intValue())))
           .andExpect(jsonPath("$[*].naveId", not(hasItem(observada.intValue()))))
           .andExpect(jsonPath("$[*].naveId", not(hasItem(aprobada.intValue()))))
           .andReturn();

        String json = r.getResponse().getContentAsString();
        assertThat(json.indexOf("\"naveId\":" + antigua))
                .as("la que lleva 9 dias va antes que la de 2")
                .isLessThan(json.indexOf("\"naveId\":" + reciente));

        // Toda la lista va de la mas vieja a la mas nueva.
        List<String> fechas = com.jayway.jsonpath.JsonPath.read(json, "$[*].ultimaAccion");
        List<OffsetDateTime> horas = fechas.stream().map(OffsetDateTime::parse).toList();
        for (int i = 1; i < horas.size(); i++) {
            assertThat(horas.get(i - 1)).isBeforeOrEqualTo(horas.get(i));
        }
    }

    @Test
    void elConteoTraeLosSeisEstadosConCeros() throws Exception {
        vaciarTramites();
        Long a = crearNave("Bandeja Conteo A");
        Long b = crearNave("Bandeja Conteo B");
        crearNave("Bandeja Conteo C");
        ponerEstado(a, EstadoTramite.OBSERVADA, 1);
        ponerEstado(b, EstadoTramite.EN_REVISION, 1);

        mvc.perform(get("/api/tramites/conteo-por-estado").with(user(DGMM)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.total").value(3))
           .andExpect(jsonPath("$.estados.SOLICITUD_PRESENTADA").value(1))
           .andExpect(jsonPath("$.estados.EN_REVISION").value(1))
           .andExpect(jsonPath("$.estados.OBSERVADA").value(1))
           .andExpect(jsonPath("$.estados.APROBADA").value(0))
           .andExpect(jsonPath("$.estados.RECHAZADA").value(0))
           .andExpect(jsonPath("$.estados.DESISTIDA").value(0));

        // Cada numero coincide con lo que devuelve el filtro.
        mvc.perform(get("/api/tramites").param("estado", "OBSERVADA").with(user(DGMM)))
           .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/tramites").with(user(DGMM)))
           .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void bandejaVacia() throws Exception {
        vaciarTramites();
        mvc.perform(get("/api/tramites").with(user(DGMM)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/tramites").param("estado", "APROBADA").with(user(AUDITOR)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/tramites/conteo-por-estado").with(user(DGMM)))
           .andExpect(jsonPath("$.total").value(0))
           .andExpect(jsonPath("$.estados.SOLICITUD_PRESENTADA").value(0));
    }

    @Test
    void laBandejaLeeTramitesNoElEstadoDeLaNave() throws Exception {
        // D4 del Sprint 5: nave.estado no cuenta para la bandeja.
        Long nave = crearNave("Bandeja Fuente Oficial");
        ponerEstado(nave, EstadoTramite.OBSERVADA, 1);
        jdbc.update("update nave set estado = 'EN_REVISION' where id = ?", nave);
        em.clear();

        mvc.perform(get("/api/tramites").param("estado", "EN_REVISION").with(user(DGMM)))
           .andExpect(jsonPath("$[*].naveId", not(hasItem(nave.intValue()))));
        mvc.perform(get("/api/tramites").param("estado", "OBSERVADA").with(user(DGMM)))
           .andExpect(jsonPath("$[*].naveId", hasItem(nave.intValue())));
    }

    // ------------------------------------------------------------------
    // Entrada despues del login
    // ------------------------------------------------------------------

    @Test
    void laDgmmYElAuditorEntranALaBandejaYElAgenteASusNaves() throws Exception {
        mvc.perform(formLogin("/api/auth/login").userParameter("correo").passwordParam("password")
                        .user(DGMM).password(CLAVE_DEMO))
           .andExpect(redirectedUrl("/bandeja.html"));
        mvc.perform(formLogin("/api/auth/login").userParameter("correo").passwordParam("password")
                        .user(AUDITOR).password(CLAVE_DEMO))
           .andExpect(redirectedUrl("/bandeja.html"));
        mvc.perform(formLogin("/api/auth/login").userParameter("correo").passwordParam("password")
                        .user(AGENTE).password(CLAVE_DEMO))
           .andExpect(redirectedUrl("/naves.html"));
    }

    // ------------------------------------------------------------------
    // Dias en espera (unidad)
    // ------------------------------------------------------------------

    @Test
    void losDiasSeCuentanEnCalendarioDePanama() {
        LocalDate hoy = LocalDate.of(2026, 10, 18);
        // 23:30 del 17 en Panama son las 04:30 UTC del 18: fue ayer en Panama.
        assertThat(TramiteService.diasEntre(OffsetDateTime.of(2026, 10, 18, 4, 30, 0, 0, ZoneOffset.UTC), hoy))
                .isEqualTo(1);
        assertThat(TramiteService.diasEntre(OffsetDateTime.of(2026, 10, 18, 15, 0, 0, 0, ZoneOffset.UTC), hoy))
                .isZero();
        assertThat(TramiteService.diasEntre(OffsetDateTime.of(2026, 10, 1, 12, 0, 0, 0, ZoneOffset.UTC), hoy))
                .isEqualTo(17);
        // Un reloj adelantado no da dias negativos.
        assertThat(TramiteService.diasEntre(OffsetDateTime.of(2026, 10, 20, 12, 0, 0, 0, ZoneOffset.UTC), hoy))
                .isZero();
        assertThat(TramiteService.diasEntre(null, hoy)).isZero();
    }

    // ------------------------------------------------------------------

    private Long crearNave(String base) throws Exception {
        String nombre = base + " " + System.nanoTime();
        mvc.perform(post("/api/naves").with(user(AGENTE)).with(csrf())
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
        return naves.findByNombreNormalizado(Nave.normalizar(nombre)).map(Nave::getId).orElseThrow();
    }

    /** Pone el tramite en un estado, con su ultima accion hace N dias. */
    private void ponerEstado(Long naveId, EstadoTramite estado, int diasAtras) {
        em.flush();
        jdbc.update("""
                    update tramites
                       set estado = ?, actualizado_en = now() - make_interval(days => ?)
                     where nave_id = ?
                    """, estado.name(), diasAtras, naveId);
        em.clear();
    }

    private void vaciarTramites() {
        em.flush();
        jdbc.update("delete from historial_estados");
        jdbc.update("delete from tramites");
        em.clear();
    }
}
