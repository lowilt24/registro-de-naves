package pa.amp.registro_naves.usuario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import jakarta.servlet.http.Cookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminUsuarioIntegrationTest {
    @Autowired WebApplicationContext contexto;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;
    @MockitoBean NotificadorCorreo notificador;
    MockMvc mvc;
    static final String CLAVE = "ClaveSegura2026";

    @BeforeEach void preparar() {
        // Sin el token automatico de las otras suites: CSRF se comprueba realmente.
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor token() throws Exception {
        String valor = mvc.perform(get("/login.html")).andReturn()
            .getResponse().getCookie("XSRF-TOKEN").getValue();
        return request -> {
            request.setCookies(new Cookie("XSRF-TOKEN", valor));
            request.addHeader("X-XSRF-TOKEN", valor);
            return request;
        };
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login() throws Exception {
        return post("/api/auth/login").with(token());
    }

    private String cuerpo(String correo, String rol) {
        return """
            {"correo":"%s","password":"%s","nombreCompleto":"Persona Interna","rol":"%s"}
            """.formatted(correo, CLAVE, rol);
    }

    @Test void administradorDemoEntraASuPantalla() throws Exception {
        var resultado = mvc.perform(login()

                .param("correo", "admin@navesitas.pa").param("password", "Navesitas2026*"))
            .andExpect(redirectedUrl("/admin-usuarios.html")).andReturn();
        mvc.perform(get("/admin-usuarios.html").session(
                (org.springframework.mock.web.MockHttpSession) resultado.getRequest().getSession(false)))
            .andExpect(status().isOk());
    }

    @Test void sinSesionNoPuedeCrearNiAbrirPagina() throws Exception {
        mvc.perform(post("/api/admin/usuarios").with(token())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo("nuevo@prueba.pa", "AUDITOR")))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/admin-usuarios.html")).andExpect(status().isUnauthorized());
        verifyNoInteractions(notificador);
    }

    @ParameterizedTest @ValueSource(strings = {"AGENTE_NAVIERO", "FUNCIONARIO_DGMM", "AUDITOR"})
    void otrosRolesNoPuedenCrearNiAbrirPagina(String rol) throws Exception {
        mvc.perform(post("/api/admin/usuarios").with(user("otro").roles(rol)).with(token())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo("nuevo@prueba.pa", "AUDITOR")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/admin-usuarios.html").with(user("otro").roles(rol)))
            .andExpect(status().isForbidden());
        assertThat(usuarios.existsByCorreoIgnoreCase("nuevo@prueba.pa")).isFalse();
        verifyNoInteractions(notificador);
    }

    @ParameterizedTest @ValueSource(strings = {"FUNCIONARIO_DGMM", "AUDITOR"})
    void creaValidaYPermiteLoginDeAmbosRoles(String rol) throws Exception {
        String correo = "interno@prueba.pa";
        mvc.perform(post("/api/admin/usuarios").with(user("admin").roles("ADMINISTRADOR"))
                .with(token()).contentType(MediaType.APPLICATION_JSON).content(cuerpo("INTERNO@prueba.pa", rol)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.rol").value(rol))
            .andExpect(jsonPath("$.correo").value(correo)).andExpect(jsonPath("$.correoValidado").value(false))
            .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.tokenValidacion").doesNotExist());
        Usuario u = usuarios.findByCorreoIgnoreCase(correo).orElseThrow();
        assertThat(encoder.matches(CLAVE, u.getPasswordHash())).isTrue();
        ArgumentCaptor<String> enlace = ArgumentCaptor.forClass(String.class);
        verify(notificador).enviarValidacion(eq(correo), eq("Persona Interna"), enlace.capture());
        String ruta = enlace.getValue().substring(enlace.getValue().indexOf("/api/"));
        assertThat(u.getTokenValidacion()).isEqualTo(UsuarioService.hash(ruta.substring(ruta.indexOf("token=") + 6)));
        mvc.perform(login()
                .param("correo", correo).param("password", CLAVE)).andExpect(redirectedUrl("/login.html?sinValidar=1"));
        mvc.perform(get(ruta)).andExpect(redirectedUrl("/login.html?validado=1"));
        mvc.perform(login()
                .param("correo", correo).param("password", CLAVE)).andExpect(redirectedUrl("/bandeja.html"));
        mvc.perform(get(ruta)).andExpect(redirectedUrl("/login.html?enlace=invalido"));
    }

    @ParameterizedTest @ValueSource(strings = {"AGENTE_NAVIERO", "ADMINISTRADOR", "INVENTADO"})
    void rechazaRolesNoPermitidos(String rol) throws Exception {
        mvc.perform(post("/api/admin/usuarios").with(user("admin").roles("ADMINISTRADOR")).with(token())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo("nuevo@prueba.pa", rol)))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(notificador);
    }

    @ParameterizedTest @ValueSource(strings = {
        "{}", "{\"correo\":\"mal\",\"password\":\"corta\",\"nombreCompleto\":\" \",\"rol\":\"AUDITOR\"}",
        "{\"correo\":\"nuevo@prueba.pa\",\"password\":\"ClaveSegura2026\",\"nombreCompleto\":\"Persona\",\"rol\":null}",
        "{mal"})
    void rechazaDatosInvalidos(String cuerpo) throws Exception {
        mvc.perform(post("/api/admin/usuarios").with(user("admin").roles("ADMINISTRADOR")).with(token())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(notificador);
    }

    @Test void correoDuplicadoIgnoraMayusculas() throws Exception {
        mvc.perform(post("/api/admin/usuarios").with(user("admin").roles("ADMINISTRADOR")).with(token())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo("AGENTE@navesitas.pa", "AUDITOR")))
            .andExpect(status().isConflict());
        verifyNoInteractions(notificador);
    }

    @Test void csrfObligatorioYCookieRealAceptada() throws Exception {
        String cuerpo = cuerpo("csrf-interno@prueba.pa", "AUDITOR");
        mvc.perform(post("/api/admin/usuarios").with(user("admin").roles("ADMINISTRADOR"))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
        String token = mvc.perform(get("/admin-usuarios.html").with(user("admin").roles("ADMINISTRADOR")))
            .andReturn().getResponse().getCookie("XSRF-TOKEN").getValue();
        mvc.perform(post("/api/admin/usuarios").with(user("admin").roles("ADMINISTRADOR"))
                .cookie(new Cookie("XSRF-TOKEN", token)).header("X-XSRF-TOKEN", "incorrecto")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
        verifyNoInteractions(notificador);
        mvc.perform(post("/api/admin/usuarios").with(user("admin").roles("ADMINISTRADOR"))
                .cookie(new Cookie("XSRF-TOKEN", token)).header("X-XSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isCreated());
    }
}
