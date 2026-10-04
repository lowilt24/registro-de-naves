package pa.amp.registro_naves.nave;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Casos TC-01 .. TC-06 del Sprint 2, actualizados al modelo del Sprint 3.
 *
 * Cambios respecto a la version anterior:
 *  - La nave ya no lleva propietarioId ni agenteResidenteId.
 *  - Todos los endpoints exigen sesion, asi que las peticiones llevan
 *    usuario (remediacion del hallazgo SEC-04).
 *  - La clase es @Transactional: cada prueba deshace sus cambios al
 *    terminar. Sin esto, la segunda corrida de mvnw verify fallaba porque
 *    las naves creadas en la primera seguian en la base.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NaveIntegrationTest {

    static final String AGENTE = "agente@navesitas.pa";

    @Autowired
    MockMvc mvc;

    private String naveValida(String nombre) {
        return """
               {
                 "nombre": "%s",
                 "tipo": "CARGA",
                 "servicio": "INTERNACIONAL",
                 "tonelajeBruto": 12000.00,
                 "tonelajeNeto": 6400.00,
                 "eslora": 150.00,
                 "manga": 22.50,
                 "puntal": 13.00,
                 "anioConstruccion": 2020,
                 "lugarConstruccion": "Astillero de prueba",
                 "materialCasco": "Acero",
                 "tipoPropulsion": "Motor diesel",
                 "potenciaKw": 7800.00
               }
               """.formatted(nombre);
    }

    /** TC-01 (HU-03, positivo): una nave con todos los campos queda REGISTRADA. */
    @Test
    void registraNaveConDatosCompletos() throws Exception {
        mvc.perform(post("/api/naves").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(naveValida("Vientos de Chiriqui")))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").exists())
           .andExpect(jsonPath("$.estado").value("REGISTRADA"));
    }

    /** TC-02 (HU-03, negativo): faltan campos obligatorios. */
    @Test
    void rechazaNaveSinCamposObligatorios() throws Exception {
        mvc.perform(post("/api/naves").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Nave Incompleta\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.campos.tipo").exists())
           .andExpect(jsonPath("$.campos.tonelajeBruto").exists());
    }

    /** TC-03 (HU-03, negativo): tonelaje negativo y anio fuera de rango. */
    @Test
    void rechazaValoresNumericosInvalidos() throws Exception {
        String invalida = naveValida("Nave Con Numeros Malos")
                .replace("\"tonelajeBruto\": 12000.00", "\"tonelajeBruto\": -5")
                .replace("\"anioConstruccion\": 2020", "\"anioConstruccion\": 1500");

        mvc.perform(post("/api/naves").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalida))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.campos.tonelajeBruto").exists())
           .andExpect(jsonPath("$.campos.anioConstruccion").exists());
    }

    /** TC-04 (HU-04, positivo): un nombre libre se reporta disponible. */
    @Test
    void reportaNombreDisponible() throws Exception {
        mvc.perform(get("/api/naves/disponibilidad").with(user(AGENTE))
                        .param("nombre", "Gaviota del Darien"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.disponible").value(true));
    }

    /** TC-05 (HU-04, negativo): nombre ocupado, sin importar mayusculas ni espacios. */
    @Test
    void reportaNombreOcupadoIgnorandoFormato() throws Exception {
        mvc.perform(get("/api/naves/disponibilidad").with(user(AGENTE))
                        .param("nombre", "  estrella   DEL istmo "))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.disponible").value(false));
    }

    /** TC-06 (HU-04, negativo): el bloqueo aplica tambien al guardar. */
    @Test
    void bloqueaRegistroConNombreDuplicado() throws Exception {
        mvc.perform(post("/api/naves").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(naveValida("Estrella del Istmo")))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.mensaje").exists());
    }

    /** Un payload de inyeccion entra como texto, no como SQL. */
    @Test
    void payloadDeInyeccionNoRompeLaConsulta() throws Exception {
        mvc.perform(get("/api/naves/disponibilidad").with(user(AGENTE))
                        .param("nombre", "' OR '1'='1"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.disponible").value(true));
    }

    /** Un nombre vacio no se considera disponible. */
    @Test
    void nombreVacioNoEsDisponible() throws Exception {
        mvc.perform(get("/api/naves/disponibilidad").with(user(AGENTE))
                        .param("nombre", "   "))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.disponible").value(false));
    }

    /**
     * Remediacion de SEC-02: el fuzzing del Sprint 2 logro registrar una
     * nave llamada "admin'--". El patron endurecido ahora la rechaza.
     */
    @Test
    void rechazaNombreConSecuenciaDeComentarioSql() throws Exception {
        mvc.perform(post("/api/naves").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(naveValida("admin'--")))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.campos.nombre").exists());
    }

    /**
     * Remediacion de SEC-01: un cuerpo JSON mal formado es error del
     * cliente (400), no del servidor (500).
     */
    @Test
    void cuerpoMalFormadoDevuelve400() throws Exception {
        mvc.perform(post("/api/naves").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Prueba\",\"tonelajeBruto\":abc}"))
           .andExpect(status().isBadRequest());
    }

    /** Remediacion de SEC-04: sin sesion no se entra. */
    @Test
    void sinSesionElRegistroEsRechazado() throws Exception {
        mvc.perform(post("/api/naves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(naveValida("Nave Sin Sesion")))
           .andExpect(status().isUnauthorized());
    }
}
