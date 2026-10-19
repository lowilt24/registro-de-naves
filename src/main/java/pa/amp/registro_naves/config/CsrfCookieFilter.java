package pa.amp.registro_naves.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * SEC-05 (Sprint 6): hace que el servidor entregue la cookie XSRF-TOKEN.
 *
 * Desde Spring Security 6 el token CSRF es "perezoso": la cookie solo se
 * escribe cuando alguien lee el token en el servidor. Las pantallas de
 * Navesitas son HTML estatico y nunca lo leen, asi que la cookie no llegaba
 * al navegador, el JS no tenia que mandar en X-XSRF-TOKEN y todo POST
 * (incluido el login) era rechazado. Este filtro lee el token en cada
 * peticion para que la cookie se escriba.
 *
 * No lleva @Component a proposito: si fuera un bean, Spring Boot lo
 * registraria tambien como filtro global, fuera de la cadena de seguridad.
 * Se agrega solo en SecurityConfig, despues del CsrfFilter.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {
        CsrfToken token = (CsrfToken) peticion.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            token.getToken();   // al leerlo, el repositorio escribe la cookie
        }
        cadena.doFilter(peticion, respuesta);
    }
}
