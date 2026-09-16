package pa.amp.registro_naves.nave;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Espejo en codigo de los casos TC-01 .. TC-06 de la lamina de Katalon.
 * Katalon prueba la pantalla; esto prueba el endpoint. Si el backend rompe,
 * el pipeline lo detecta antes de que alguien abra Katalon Studio.
 *
 * El JSON va escrito a mano a proposito: asi la prueba no depende de la
 * version de Jackson, que cambio de paquete en Spring Boot 4.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NaveIntegrationTest {

    @Autowired
    MockMvc mvc;

    /** Nave completa y valida; solo cambia el nombre. */
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
                 "potenciaKw": 7800.00,
                 "propietarioId": 1,
                 "agenteResidenteId": 1
               }
               """.formatted(nombre);
    }

    /** TC-01 (HU-03, positivo): una nave con todos los campos queda REGISTRADA. */
    @Test
    void registraNaveConDatosCompletos() throws Exception {
        mvc.perform(post("/api/naves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(naveValida("Vientos de Chiriqui")))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").exists())
           .andExpect(jsonPath("$.estado").value("REGISTRADA"))
           .andExpect(jsonPath("$.propietario").exists())
           .andExpect(jsonPath("$.agenteResidente").exists());
    }

    /** TC-02 (HU-03, negativo): faltan campos obligatorios. */
    @Test
    void rechazaNaveSinCamposObligatorios() throws Exception {
        mvc.perform(post("/api/naves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Nave Incompleta\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.campos.tipo").exists())
           .andExpect(jsonPath("$.campos.tonelajeBruto").exists());
    }

    /** TC-03 (HU-03, negativo): tonelaje negativo y anio fuera de rango. */
    @Test
    void rechazaValoresNumericosInvalidos() throws Exception {
        String invalida = """
               {
                 "nombre": "Nave Con Numeros Malos",
                 "tipo": "CARGA",
                 "servicio": "INTERNACIONAL",
                 "tonelajeBruto": -5,
                 "tonelajeNeto": 6400.00,
                 "eslora": 150.00,
                 "manga": 22.50,
                 "puntal": 13.00,
                 "anioConstruccion": 1500,
                 "lugarConstruccion": "Astillero de prueba",
                 "materialCasco": "Acero",
                 "tipoPropulsion": "Motor diesel",
                 "potenciaKw": 7800.00,
                 "propietarioId": 1,
                 "agenteResidenteId": 1
               }
               """;

        mvc.perform(post("/api/naves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalida))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.campos.tonelajeBruto").exists())
           .andExpect(jsonPath("$.campos.anioConstruccion").exists());
    }

    /** TC-04 (HU-04, positivo): un nombre libre se reporta disponible. */
    @Test
    void reportaNombreDisponible() throws Exception {
        mvc.perform(get("/api/naves/disponibilidad").param("nombre", "Gaviota del Darien"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.disponible").value(true));
    }

    /**
     * TC-05 (HU-04, negativo): un nombre ya usado se reporta ocupado, sin
     * importar mayusculas ni espacios de mas.
     */
    @Test
    void reportaNombreOcupadoIgnorandoFormato() throws Exception {
        mvc.perform(get("/api/naves/disponibilidad").param("nombre", "  estrella   DEL istmo "))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.disponible").value(false));
    }

    /**
     * TC-06 (HU-04, negativo): el bloqueo tambien aplica al guardar, aunque se
     * salte la pantalla y se llame el endpoint directamente.
     */
    @Test
    void bloqueaRegistroConNombreDuplicado() throws Exception {
        mvc.perform(post("/api/naves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(naveValida("Estrella del Istmo")))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.mensaje").exists());
    }

    /**
     * Extra para la lamina de Wfuzz: un payload de inyeccion entra como texto,
     * no como SQL, y el servidor responde de forma controlada.
     */
    @Test
    void payloadDeInyeccionNoRompeLaConsulta() throws Exception {
        mvc.perform(get("/api/naves/disponibilidad").param("nombre", "' OR '1'='1"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.disponible").value(true));
    }

    /** Extra: un nombre vacio no se considera disponible. */
    @Test
    void nombreVacioNoEsDisponible() throws Exception {
        mvc.perform(get("/api/naves/disponibilidad").param("nombre", "   "))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.disponible").value(false));
    }
}