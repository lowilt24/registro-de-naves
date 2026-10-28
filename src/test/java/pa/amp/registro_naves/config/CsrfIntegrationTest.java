package pa.amp.registro_naves.config;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SEC-05 — proteccion CSRF, como la vive el navegador.
 *
 * Usa un MockMvc propio, SIN el token automatico de CsrfEnPruebasConfig:
 * aqui cada peticion lleva solo lo que mandaria una pagina de verdad.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CsrfIntegrationTest {

    static final String AGENTE = "agente@navesitas.pa";

    @Autowired WebApplicationContext contexto;
    MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    /** El fallo del commit 9f8bdaf: la cookie nunca se escribia. */
    @Test
    void abrirElLoginEntregaLaCookie() throws Exception {
        String token = tokenDe(mvc.perform(get("/login.html")).andReturn());
        assertThat(token).isNotBlank();
    }

    @Test
    void sinTokenUnPostEsRechazado() throws Exception {
        mvc.perform(post("/api/naves").with(user(AGENTE))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
           .andExpect(status().isForbidden());
    }

    @Test
    void unTokenQueNoCoincideConLaCookieEsRechazado() throws Exception {
        String token = tokenDe(mvc.perform(get("/login.html")).andReturn());
        mvc.perform(post("/api/naves").with(user(AGENTE))
                        .cookie(new Cookie("XSRF-TOKEN", token))
                        .header("X-XSRF-TOKEN", "otro-valor")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
           .andExpect(status().isForbidden());
    }

    /** Como comun.js: la cookie de vuelta en el header X-XSRF-TOKEN. */
    @Test
    void conElTokenEnElHeaderElPostPasa() throws Exception {
        String token = tokenDe(mvc.perform(get("/login.html")).andReturn());
        // Pasa CSRF y llega a la validacion: 400 por el cuerpo vacio, no 403.
        mvc.perform(post("/api/naves").with(user(AGENTE))
                        .cookie(new Cookie("XSRF-TOKEN", token))
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
           .andExpect(status().isBadRequest());
    }

    /** Como login.html: el token en el campo oculto _csrf del formulario. */
    @Test
    void elLoginConElCampoOcultoEntra() throws Exception {
        String token = tokenDe(mvc.perform(get("/login.html")).andReturn());
        mvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("XSRF-TOKEN", token))
                        .param("_csrf", token)
                        .param("correo", AGENTE)
                        .param("password", "Navesitas2026*"))
           .andExpect(redirectedUrl("/naves.html"));
    }

    @Test
    void elLoginSinTokenNoEntra() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .param("correo", AGENTE)
                        .param("password", "Navesitas2026*"))
           .andReturn();
        assertThat(r.getResponse().getRedirectedUrl()).isNotEqualTo("/naves.html");
        assertThat(r.getResponse().getStatus()).isNotEqualTo(200);
    }

    private static String tokenDe(MvcResult r) {
        Cookie cookie = r.getResponse().getCookie("XSRF-TOKEN");
        return cookie == null ? null : cookie.getValue();
    }
}
