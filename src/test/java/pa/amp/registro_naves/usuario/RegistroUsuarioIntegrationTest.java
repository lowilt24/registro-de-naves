package pa.amp.registro_naves.usuario;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * QA Sprint 6 (T5): ramas de HU-01 y HU-02 que eran criterio de aceptacion
 * desde el Sprint 4 y no tenian prueba en Maven (correo duplicado, consulta
 * de la sesion sin login). Complementa a ValidacionCorreoIntegrationTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegistroUsuarioIntegrationTest {

    static final String CLAVE = "ClaveSegura2026";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder passwordEncoder;

    /** Sin envio real; tambien permite contar los correos. */
    @MockitoBean NotificadorCorreo notificador;

    private ResultActions registrar(String correo, String nombre, String rolJson) throws Exception {
        String rol = rolJson == null ? "" : ",\"rol\":\"" + rolJson + "\"";
        return mvc.perform(post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"correo":"%s","password":"%s","nombreCompleto":"%s"%s}
                         """.formatted(correo, CLAVE, nombre, rol)));
    }

    // ------------------------------------------------------------------
    // HU-01 — registro de usuario
    // ------------------------------------------------------------------

    /** Criterio de HU-01: no hay dos cuentas con el mismo correo, sin importar mayusculas. */
    @Test
    void correoDuplicadoEsRechazado() throws Exception {
        registrar("duplicado@prueba.pa", "Primera Persona", "AGENTE_NAVIERO")
           .andExpect(status().isCreated());

        registrar("DUPLICADO@Prueba.PA", "Segunda Persona", "AGENTE_NAVIERO")
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.mensaje").value("Ya existe una cuenta con ese correo."));

        // Una cuenta que ya existia antes de la prueba tambien choca.
        registrar("agente@navesitas.pa", "Otra Persona", "AGENTE_NAVIERO")
           .andExpect(status().isConflict());

        assertThat(usuarios.findAll().stream()
                .filter(u -> u.getCorreo().equalsIgnoreCase("duplicado@prueba.pa")).count())
                .isEqualTo(1);
        verify(notificador, times(1)).enviarValidacion(eq("duplicado@prueba.pa"), anyString(), anyString());
    }

    @Test
    void elCorreoSeGuardaEnMinusculasYElNombreSinEspaciosSobrantes() throws Exception {
        registrar("Mixto.QA@Prueba.PA", "  Nombre Con Espacios  ", "AGENTE_NAVIERO")
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.correo").value("mixto.qa@prueba.pa"));

        Usuario u = usuarios.findByCorreoIgnoreCase("mixto.qa@prueba.pa").orElseThrow();
        assertThat(u.getCorreo()).isEqualTo("mixto.qa@prueba.pa");
        assertThat(u.getNombreCompleto()).isEqualTo("Nombre Con Espacios");
    }

    /** Un correo con espacios alrededor no pasa la validacion de @Email (400); el navegador ya los quita. */
    @Test
    void correoConEspaciosAlrededorEs400() throws Exception {
        registrar("  espacios@prueba.pa ", "Con Espacios", "AGENTE_NAVIERO")
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.campos.correo").exists());
    }

    /** Sin rol en la peticion, la cuenta queda como agente naviero. */
    @Test
    void sinRolLaCuentaEsDeAgente() throws Exception {
        registrar("sinrol@prueba.pa", "Sin Rol", null)
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.rol").value("AGENTE_NAVIERO"));
    }

    // ------------------------------------------------------------------
    // HU-02 — sesion
    // ------------------------------------------------------------------

    /** Criterio de HU-02: consultar la sesion sin login no da error, dice que no hay sesion. */
    @Test
    void consultaDeSesionSinLogin() throws Exception {
        mvc.perform(get("/api/auth/sesion"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.autenticado").value(false))
           .andExpect(jsonPath("$.correo").doesNotExist())
           .andExpect(jsonPath("$.rol").doesNotExist());
    }

    @Test
    void consultaDeSesionConLogin() throws Exception {
        mvc.perform(get("/api/auth/sesion").with(user("revisor@navesitas.pa")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.autenticado").value(true))
           .andExpect(jsonPath("$.correo").value("revisor@navesitas.pa"))
           .andExpect(jsonPath("$.rol").value("FUNCIONARIO_DGMM"))
           .andExpect(jsonPath("$.nombreCompleto").isNotEmpty());
    }

    /** Una sesion cuyo usuario ya no existe en la base no cuenta como autenticada. */
    @Test
    void sesionDeUnUsuarioQueNoExiste() throws Exception {
        mvc.perform(get("/api/auth/sesion").with(user("borrado@prueba.pa")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.autenticado").value(false));
    }

    /** Una cuenta desactivada no entra aunque tenga el correo validado y la clave correcta. */
    @Test
    void cuentaDesactivadaNoEntra() throws Exception {
        Usuario u = new Usuario();
        u.setCorreo("inactiva@prueba.pa");
        u.setPasswordHash(passwordEncoder.encode(CLAVE));
        u.setNombreCompleto("Cuenta Inactiva");
        u.setRol(Rol.AGENTE_NAVIERO);
        u.setCorreoValidado(true);
        u.setActivo(false);
        usuarios.saveAndFlush(u);

        // Hoy el aviso es el de "confirme su correo" (DisabledException); lo
        // que importa aqui es que no entra.
        mvc.perform(formLogin("/api/auth/login")
                        .userParameter("correo").passwordParam("password")
                        .user("inactiva@prueba.pa").password(CLAVE))
           .andExpect(redirectedUrl("/login.html?sinValidar=1"));
    }

    /** Pedir otro enlace para una cuenta ya validada no manda nada (y responde igual). */
    @Test
    void reenvioAUnaCuentaYaValidadaNoEnviaCorreo() throws Exception {
        mvc.perform(post("/api/auth/reenviar-validacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"agente@navesitas.pa\"}"))
           .andExpect(status().isAccepted());
        verify(notificador, times(0)).enviarValidacion(anyString(), anyString(), anyString());
    }
}
