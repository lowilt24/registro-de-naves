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

/**
 * HU-05 — registro del propietario y vinculacion a la nave.
 *
 * Cada prueba trabaja sobre naves que ella misma crea, no sobre la nave de
 * demo: esa quedo con el 100% de participacion asignado al migrar, y
 * cualquier propietario adicional chocaria con el limite de cuotas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PropietarioIntegrationTest {

    static final String AGENTE  = "agente@navesitas.pa";
    static final String REVISOR = "revisor@navesitas.pa";

    @Autowired MockMvc mvc;
    @Autowired NaveRepository naves;

    Long naveDelAgente;

    @BeforeEach
    void prepararNaves() throws Exception {
        naveDelAgente = crearNave("Nave Prueba Propietarios", AGENTE);
    }

    /** Registra una nave con el usuario indicado y devuelve su id. */
    private Long crearNave(String nombre, String usuario) throws Exception {
        String cuerpo = """
               {
                 "nombre": "%s",
                 "tipo": "CARGA",
                 "servicio": "INTERNACIONAL",
                 "tonelajeBruto": 9000.00,
                 "tonelajeNeto": 4500.00,
                 "eslora": 120.00,
                 "manga": 18.00,
                 "puntal": 10.00,
                 "anioConstruccion": 2021,
                 "lugarConstruccion": "Astillero de prueba",
                 "materialCasco": "Acero",
                 "tipoPropulsion": "Motor diesel",
                 "potenciaKw": 5200.00
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

    private String propietario(Long naveId, String nombre, String tipo, String extra) {
        return """
               {
                 "nombre": "%s",
                 "tipo": "%s",
                 "nacionalidad": "Panamena",
                 "domicilio": "Calle 50, Ciudad de Panama",
                 "naveId": %d
                 %s
               }
               """.formatted(nombre, tipo, naveId, extra);
    }

    /** Positivo: persona natural vinculada correctamente. */
    @Test
    void registraPropietarioNaturalYLoVincula() throws Exception {
        mvc.perform(post("/api/propietarios").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(propietario(naveDelAgente, "Juan Perez", "NATURAL",
                                ", \"porcentaje\": 40.00")))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").exists())
           .andExpect(jsonPath("$.tipo").value("NATURAL"))
           .andExpect(jsonPath("$.naveId").value(naveDelAgente))
           .andExpect(jsonPath("$.porcentaje").value(40.00));
    }

    /** Positivo: persona juridica con pais de constitucion. */
    @Test
    void registraPropietarioJuridicoConPaisDeConstitucion() throws Exception {
        mvc.perform(post("/api/propietarios").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(propietario(naveDelAgente, "Marina Global S.A.", "JURIDICA",
                                ", \"paisConstitucion\": \"Panama\", \"porcentaje\": 30.00")))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.paisConstitucion").value("Panama"));
    }

    /** Negativo: campos obligatorios ausentes, con mensaje por campo. */
    @Test
    void rechazaPropietarioSinCamposObligatorios() throws Exception {
        mvc.perform(post("/api/propietarios").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Solo el nombre\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.campos.tipo").exists())
           .andExpect(jsonPath("$.campos.nacionalidad").exists())
           .andExpect(jsonPath("$.campos.domicilio").exists())
           .andExpect(jsonPath("$.campos.naveId").exists());
    }

    /** Negativo: una sociedad sin pais de constitucion no se acepta. */
    @Test
    void rechazaJuridicaSinPaisDeConstitucion() throws Exception {
        mvc.perform(post("/api/propietarios").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(propietario(naveDelAgente, "Sociedad Sin Pais", "JURIDICA", "")))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.campos.paisConstitucion").exists());
    }

    /** Negativo: nave inexistente responde 404, como pide HU-05. */
    @Test
    void naveInexistenteDevuelve404() throws Exception {
        mvc.perform(post("/api/propietarios").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(propietario(999999L, "Propietario Huerfano", "NATURAL", "")))
           .andExpect(status().isNotFound());
    }

    /**
     * Control de acceso de HU-05: un usuario no puede vincular propietarios
     * a naves que no registro. Es el mismo caso que prueba Wfuzz manipulando
     * el ID de la nave.
     */
    @Test
    void noSePuedeVincularAUnaNaveAjena() throws Exception {
        mvc.perform(post("/api/propietarios").with(user(REVISOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(propietario(naveDelAgente, "Intruso S.A.", "JURIDICA",
                                ", \"paisConstitucion\": \"Panama\"")))
           .andExpect(status().isForbidden());
    }

    /** Sin sesion no se entra. */
    @Test
    void sinSesionEsRechazado() throws Exception {
        mvc.perform(post("/api/propietarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(propietario(naveDelAgente, "Anonimo", "NATURAL", "")))
           .andExpect(status().isUnauthorized());
    }

    /** Regla de negocio: la suma de cuotas de una nave no puede pasar de 100. */
    @Test
    void rechazaParticipacionQueExcedeElCienPorCiento() throws Exception {
        mvc.perform(post("/api/propietarios").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(propietario(naveDelAgente, "Primer Duenio", "NATURAL",
                                ", \"porcentaje\": 80.00")))
           .andExpect(status().isCreated());

        mvc.perform(post("/api/propietarios").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(propietario(naveDelAgente, "Segundo Duenio", "NATURAL",
                                ", \"porcentaje\": 40.00")))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.mensaje").exists());
    }

    /** Consulta: los propietarios de una nave propia se pueden listar. */
    @Test
    void listaLosPropietariosDeLaNave() throws Exception {
        mvc.perform(post("/api/propietarios").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(propietario(naveDelAgente, "Copropietario", "NATURAL",
                                ", \"porcentaje\": 10.00")))
           .andExpect(status().isCreated());

        mvc.perform(get("/api/naves/" + naveDelAgente + "/propietarios").with(user(AGENTE)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].nombre").value("Copropietario"));
    }
}
