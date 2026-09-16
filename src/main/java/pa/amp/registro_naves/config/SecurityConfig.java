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
     * Identifica las llamadas a la API (todo lo que empieza con /api/).
     *
     * Nota: en Spring Security 7 se elimino AntPathRequestMatcher. RequestMatcher
     * es una interfaz funcional, asi que una lambda cumple el mismo papel sin
     * depender de clases que cambian entre versiones.
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
            // CSRF apagado solo sobre /api/**: las pantallas llaman por fetch y
            // Wfuzz y Katalon necesitan golpear los endpoints directamente.
            // Antes de una entrega real esto se vuelve a activar con token.
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))

            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/", "/index.html", "/login.html",
                            "/registro-usuario.html", "/naves.html",
                            "/css/**", "/js/**", "/favicon.ico",
                            "/actuator/health").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/api/auth/registro", "/api/auth/sesion").permitAll()
                // Durante el Sprint 2 los endpoints de nave quedan abiertos para
                // que Katalon y Wfuzz corran sin manejar sesion. En Sprint 3
                // pasan a .hasRole("AGENTE_NAVIERO").
                .requestMatchers("/api/naves/**", "/api/propietarios",
                                 "/api/agentes-residentes").permitAll()
                .anyRequest().authenticated())

            // La consola de H2 se dibuja dentro de un frame; sin esto sale en blanco.
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))

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

            // Sin esto, una llamada a /api/** sin sesion devuelve el HTML del
            // login con 200 y confunde a las pruebas automatizadas.
            .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), ES_API));

        return http.build();
    }
}