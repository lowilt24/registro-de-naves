package pa.amp.registro_naves.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
public class SecurityConfig {

    /**
     * Identifica las llamadas a la API. En Spring Security 7 se elimino
     * AntPathRequestMatcher; RequestMatcher es una interfaz funcional, asi
     * que una lambda cumple el mismo papel sin depender de clases que
     * cambian entre versiones.
     */
    private static final RequestMatcher ES_API =
            request -> request.getRequestURI().startsWith("/api/");

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            /*
             * CSRF sigue apagado sobre /api/**. Queda como pendiente abierto
             * (hallazgo SEC-05 del informe del Sprint 2): activarlo exige que
             * las pantallas envien el token en cada peticion, y ese cambio va
             * junto con la actualizacion del frontend.
             */
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))

            .authorizeHttpRequests(auth -> auth
                // Publico: entrar, crear cuenta y el chequeo de salud.
                .requestMatchers("/", "/index.html", "/login.html",
                                 "/registro-usuario.html",
                                 "/css/**", "/js/**", "/favicon.ico",
                                 "/actuator/health").permitAll()
                .requestMatchers("/api/auth/registro", "/api/auth/sesion").permitAll()

                /*
                 * Sprint 3 — remediacion del hallazgo SEC-04.
                 *
                 * Todo lo demas exige sesion. HU-05 y HU-06 lo requieren
                 * explicitamente, y el filtro por dueno de la nave se aplica
                 * ademas en AccesoNave.
                 */
                .anyRequest().authenticated())

            /*
             * Sprint 3 — remediacion del hallazgo SEC-07.
             * Se retiraron la ruta publica de /h2-console y la relajacion de
             * frameOptions, que existian solo para la consola de H2 y
             * quedaron sin uso tras migrar a PostgreSQL.
             */

            .formLogin(form -> form
                .loginPage("/login.html")
                .loginProcessingUrl("/api/auth/login")
                .usernameParameter("correo")
                .passwordParameter("password")
                .defaultSuccessUrl("/naves.html", true)
                .failureUrl("/login.html?error=1")
                .permitAll())

            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessUrl("/login.html?salida=1")
                .permitAll())

            /*
             * Criterio de HU-06: una peticion sin sesion es rechazada. Sin
             * esto, una llamada a /api/** sin sesion devolveria el HTML del
             * login con codigo 200 y las pruebas no podrian distinguirlo.
             */
            .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), ES_API));

        return http.build();
    }
}
