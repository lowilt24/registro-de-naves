package pa.amp.registro_naves.persona;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pa.amp.registro_naves.nave.Nave;
import pa.amp.registro_naves.nave.NaveRepository;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU-06 — designacion y consulta del agente residente. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AgenteIntegrationTest {

    static final String AGENTE  = "agente@navesitas.pa";
    static final String REVISOR = "revisor@navesitas.pa";

    @Autowired MockMvc mvc;
    @Autowired NaveRepository naves;

    /** Nave con propietario: cumple la precondicion de HU-06. */
    Long naveConPropietario;

    /** Nave sin propietario: sirve para probar que la precondicion se aplica. */
    Long naveSinPropietario;

    @BeforeEach
    void prepararNaves() throws Exception {
        naveConPropietario = crearNave("Nave Prueba Agente", AGENTE);
        naveSinPropietario = crearNave("Nave Sin Duenio", AGENTE);
        vincularPropietario(naveConPropietario);
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
           .andExpect(status().isCreated());

        return naves.findByNombreNormalizado(Nave.normalizar(nombre))
                .map(Nave::getId)
                .orElseThrow(() -> new IllegalStateException("No se creo la nave de prueba."));
    }

    private void vincularPropietario(Long naveId) throws Exception {
        String cuerpo = """
               {
                 "nombre": "Naviera de Prueba S.A.",
                 "tipo": "JURIDICA",
                 "nacionalidad": "Panamena",
                 "domicilio": "Calle 50, Ciudad de Panama",
                 "paisConstitucion": "Panama",
                 "naveId": %d
               }
               """.formatted(naveId);

        mvc.perform(post("/api/propietarios").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
           .andExpect(status().isCreated());
    }

    private String agente(String nombre, String idoneidad) {
        return """
               {
                 "nombre": "%s",
                 "idoneidad": "%s",
                 "telefono": "+507 200-1234",
                 "correo": "contacto@bufete.com.pa",
                 "poderNumero": "PG-2026-001",
                 "poderFecha": "2026-03-15",
                 "poderLugar": "Notaria Quinta del Circuito de Panama"
               }
               """.formatted(nombre, idoneidad);
    }

    /** Positivo: se designa el agente de una nave con propietario. */
    @Test
    void designaAgenteResidente() throws Exception {
        mvc.perform(post("/api/naves/" + naveConPropietario + "/agente-residente").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agente("Bufete Maritimo Balboa", "IDN-4521")))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.nombre").value("Bufete Maritimo Balboa"))
           .andExpect(jsonPath("$.vigente").value(true))
           .andExpect(jsonPath("$.poderNumero").value("PG-2026-001"));
    }

    /** Precondicion de HU-06: sin propietario vinculado no se puede designar. */
    @Test
    void noSeDesignaAgenteSinPropietarioVinculado() throws Exception {
        mvc.perform(post("/api/naves/" + naveSinPropietario + "/agente-residente").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agente("Bufete Sin Nave", "IDN-0001")))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.mensaje").exists());
    }

    /** Regla central: una nueva designacion reemplaza a la anterior. */
    @Test
    void laNuevaDesignacionReemplazaALaAnterior() throws Exception {
        String url = "/api/naves/" + naveConPropietario + "/agente-residente";

        mvc.perform(post(url).with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agente("Primer Bufete", "IDN-1111")))
           .andExpect(status().isCreated());

        mvc.perform(post(url).with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agente("Segundo Bufete", "IDN-2222")))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.vigente").value(true));

        // La consulta devuelve solo al vigente.
        mvc.perform(get(url).with(user(AGENTE)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.nombre").value("Segundo Bufete"));

        // El anterior sigue en el historial, marcado como no vigente.
        mvc.perform(get(url + "/historial").with(user(AGENTE)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].nombre").value("Segundo Bufete"))
           .andExpect(jsonPath("$[1].nombre").value("Primer Bufete"))
           .andExpect(jsonPath("$[1].vigente").value(false));
    }

    /** Consulta de una nave sin agente designado. */
    @Test
    void consultarAgenteInexistenteDevuelve404() throws Exception {
        mvc.perform(get("/api/naves/" + naveConPropietario + "/agente-residente").with(user(AGENTE)))
           .andExpect(status().isNotFound());
    }

    /** Negativo: faltan telefono y correo. */
    @Test
    void rechazaAgenteSinCamposObligatorios() throws Exception {
        mvc.perform(post("/api/naves/" + naveConPropietario + "/agente-residente").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Bufete Incompleto\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.campos.telefono").exists())
           .andExpect(jsonPath("$.campos.correo").exists());
    }

    /** Criterio de HU-06: una peticion sin sesion es rechazada. */
    @Test
    void sinSesionEsRechazado() throws Exception {
        mvc.perform(post("/api/naves/" + naveConPropietario + "/agente-residente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agente("Bufete Anonimo", "IDN-9999")))
           .andExpect(status().isUnauthorized());
    }

    /** No se puede designar agente a una nave ajena. */
    @Test
    void noSePuedeDesignarEnNaveAjena() throws Exception {
        mvc.perform(post("/api/naves/" + naveConPropietario + "/agente-residente").with(user(REVISOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agente("Bufete Intruso", "IDN-8888")))
           .andExpect(status().isForbidden());
    }
}
