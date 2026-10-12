package pa.amp.registro_naves.usuario;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Observacion de la profesora: validacion del correo al registrarse. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ValidacionCorreoIntegrationTest {

    static final String CLAVE = "ClaveSegura2026";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;

    /** Sustituye al envio real para poder leer el enlace. */
    @MockitoBean NotificadorCorreo notificador;

    private String registrar(String correo, String rol) throws Exception {
        mvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                               {"correo":"%s","password":"%s","nombreCompleto":"Persona de Prueba","rol":"%s"}
                               """.formatted(correo, CLAVE, rol)))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.correoValidado").value(false));

        ArgumentCaptor<String> enlace = ArgumentCaptor.forClass(String.class);
        verify(notificador).enviarValidacion(eq(correo), anyString(), enlace.capture());
        return enlace.getValue();
    }

    private static String rutaDe(String enlace) {
        return enlace.substring(enlace.indexOf("/api/"));
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.FormLoginRequestBuilder
            login(String correo) {
        return formLogin("/api/auth/login")
                .userParameter("correo").passwordParam("password")
                .user(correo).password(CLAVE);
    }

    @Test
    void sinValidarNoEntraYConElEnlaceSi() throws Exception {
        String enlace = registrar("nueva@prueba.pa", "AGENTE_NAVIERO");
        assertThat(enlace).startsWith("http://localhost:8080/api/auth/validar?token=");

        // Antes de abrir el enlace: login rechazado con el aviso de validar.
        mvc.perform(login("nueva@prueba.pa"))
           .andExpect(redirectedUrl("/login.html?sinValidar=1"));

        // Abrir el enlace.
        mvc.perform(get(rutaDe(enlace)))
           .andExpect(status().isFound())
           .andExpect(redirectedUrl("/login.html?validado=1"));

        // Ahora si entra.
        mvc.perform(login("nueva@prueba.pa"))
           .andExpect(redirectedUrl("/naves.html"));

        // El enlace es de un solo uso.
        mvc.perform(get(rutaDe(enlace)))
           .andExpect(redirectedUrl("/login.html?enlace=invalido"));
    }

    @Test
    void elTokenNoSeGuardaEnClaro() throws Exception {
        String enlace = registrar("hash@prueba.pa", "AGENTE_NAVIERO");
        String token = enlace.substring(enlace.indexOf("token=") + 6);

        Usuario u = usuarios.findByCorreoIgnoreCase("hash@prueba.pa").orElseThrow();
        assertThat(u.getTokenValidacion()).isNotEqualTo(token).hasSize(64);
        assertThat(u.getTokenExpiraEn()).isAfter(OffsetDateTime.now().plusHours(23));
    }

    @Test
    void enlaceVencido() throws Exception {
        String enlace = registrar("vencido@prueba.pa", "AGENTE_NAVIERO");
        Usuario u = usuarios.findByCorreoIgnoreCase("vencido@prueba.pa").orElseThrow();
        u.setTokenExpiraEn(OffsetDateTime.now().minusMinutes(1));
        usuarios.saveAndFlush(u);

        mvc.perform(get(rutaDe(enlace)))
           .andExpect(redirectedUrl("/login.html?enlace=vencido"));
        mvc.perform(login("vencido@prueba.pa"))
           .andExpect(redirectedUrl("/login.html?sinValidar=1"));
    }

    @Test
    void tokenInventadoOVacio() throws Exception {
        mvc.perform(get("/api/auth/validar").param("token", "inventado"))
           .andExpect(redirectedUrl("/login.html?enlace=invalido"));
        mvc.perform(get("/api/auth/validar"))
           .andExpect(redirectedUrl("/login.html?enlace=invalido"));
    }

    @Test
    void reenvioDaUnEnlaceNuevoYAnulaElAnterior() throws Exception {
        String primero = registrar("reenvio@prueba.pa", "AGENTE_NAVIERO");

        mvc.perform(post("/api/auth/reenviar-validacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"reenvio@prueba.pa\"}"))
           .andExpect(status().isAccepted());

        ArgumentCaptor<String> enlace = ArgumentCaptor.forClass(String.class);
        verify(notificador, org.mockito.Mockito.times(2))
                .enviarValidacion(eq("reenvio@prueba.pa"), anyString(), enlace.capture());
        String segundo = enlace.getAllValues().get(1);
        assertThat(segundo).isNotEqualTo(primero);

        mvc.perform(get(rutaDe(primero))).andExpect(redirectedUrl("/login.html?enlace=invalido"));
        mvc.perform(get(rutaDe(segundo))).andExpect(redirectedUrl("/login.html?validado=1"));
    }

    /** No revela que correos tienen cuenta: misma respuesta y ningun envio. */
    @Test
    void reenvioACorreoInexistente() throws Exception {
        mvc.perform(post("/api/auth/reenviar-validacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"nadie@prueba.pa\"}"))
           .andExpect(status().isAccepted());
        verify(notificador, never()).enviarValidacion(anyString(), anyString(), anyString());
    }

    /** Los usuarios de demo quedaron validados por la V5 y entran. */
    @Test
    void usuariosDeDemoSiguenEntrando() throws Exception {
        mvc.perform(formLogin("/api/auth/login")
                        .userParameter("correo").passwordParam("password")
                        .user("agente@navesitas.pa").password("Navesitas2026*"))
           .andExpect(redirectedUrl("/naves.html"));
        mvc.perform(formLogin("/api/auth/login")
                        .userParameter("correo").passwordParam("password")
                        .user("auditor@navesitas.pa").password("Navesitas2026*"))
           .andExpect(redirectedUrl("/naves.html"));
    }

    /** Nadie se registra solo como auditor o administrador. */
    @Test
    void rolesNoAutoasignables() throws Exception {
        for (String rol : new String[] {"AUDITOR", "ADMINISTRADOR"}) {
            mvc.perform(post("/api/auth/registro")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                   {"correo":"%s@prueba.pa","password":"%s","nombreCompleto":"X","rol":"%s"}
                                   """.formatted(rol.toLowerCase(), CLAVE, rol)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.campos.rol").exists());
        }
    }

    /** La contrasena equivocada sigue siendo "error", no "sin validar". */
    @Test
    void contrasenaIncorrecta() throws Exception {
        mvc.perform(formLogin("/api/auth/login")
                        .userParameter("correo").passwordParam("password")
                        .user("agente@navesitas.pa").password("otra"))
           .andExpect(redirectedUrl("/login.html?error=1"));
    }
}
