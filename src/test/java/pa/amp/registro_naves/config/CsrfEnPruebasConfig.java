package pa.amp.registro_naves.config;

import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * SEC-05 (Sprint 6): en las pruebas, toda peticion de MockMvc lleva el
 * token CSRF, igual que las pantallas desde que comun.js lo manda en
 * X-XSRF-TOKEN.
 *
 * Sin esto, cada POST de las pruebas de los sprints 2 a 5 recibia 403 al
 * activar CSRF. Ponerlo aqui, una sola vez, evita tocar cada clase de prueba
 * y sirve tambien para las que se escriban despues.
 *
 * Que CSRF de verdad rechaza una peticion sin token lo prueba
 * CsrfIntegrationTest, con un MockMvc propio que no usa este ajuste.
 *
 * Es un @Configuration (no @TestConfiguration) para que el escaneo de
 * @SpringBootApplication lo encuentre en todas las pruebas. Solo existe en
 * src/test: no llega a la aplicacion.
 */
@Configuration(proxyBeanMethods = false)
public class CsrfEnPruebasConfig {

    @Bean
    MockMvcBuilderCustomizer tokenCsrfEnTodasLasPeticiones() {
        return builder -> builder.defaultRequest(get("/").with(csrf()));
    }
}
