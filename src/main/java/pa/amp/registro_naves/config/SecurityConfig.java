package pa.amp.registro_naves.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
public class SecurityConfig {

    /**
     * Identifica las llamadas a la API.
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
             * SEC-05: el frontend lee la cookie XSRF-TOKEN y devuelve
             * su valor en el encabezado X-XSRF-TOKEN.
             */
            .csrf(csrf -> csrf
                .csrfTokenRepository(
                    CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(
                    new CsrfTokenRequestAttributeHandler())
            )
            .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)

            .authorizeHttpRequests(auth -> auth
                // Paginas y recursos publicos.
                .requestMatchers(
                    "/",
                    "/index.html",
                    "/login.html",
                    "/registro-usuario.html",
                    "/css/**",
                    "/js/**",
                    "/favicon.ico",
                    "/actuator/health"
                ).permitAll()

                // Registro publico y consulta de sesion.
                .requestMatchers(org.springframework.http.HttpMethod.GET,
                    "/verificar-patente.html", "/api/verificacion/patentes/*").permitAll()
                .requestMatchers(
                    "/api/auth/registro",
                    "/api/auth/sesion"
                ).permitAll()

                // Validacion del correo sin sesion.
                .requestMatchers(
                    "/api/auth/validar",
                    "/api/auth/reenviar-validacion"
                ).permitAll()

                // HU-14: solo el administrador puede acceder.
                .requestMatchers(
                    "/api/admin/**",
                    "/admin-usuarios.html"
                ).hasRole("ADMINISTRADOR")

                // Las demas rutas requieren sesion.
                .requestMatchers("/reportes.html").hasAnyRole("FUNCIONARIO_DGMM", "AUDITOR")
                .requestMatchers("/guia-requisitos.html").hasRole("AGENTE_NAVIERO")
                .requestMatchers("/patentes.html", "/patente-provisional.html").hasAnyRole("AGENTE_NAVIERO", "FUNCIONARIO_DGMM", "AUDITOR")
                .anyRequest().authenticated()
            )

            .formLogin(form -> form
                .loginPage("/login.html")
                .loginProcessingUrl("/api/auth/login")
                .usernameParameter("correo")
                .passwordParameter("password")

                // Cada rol entra a su pantalla de trabajo.
                .successHandler((peticion, respuesta, autenticacion) ->
                    respuesta.sendRedirect(
                        autenticacion.getAuthorities().stream().anyMatch(a ->
                            "ROLE_ADMINISTRADOR".equals(a.getAuthority())
                        ) ? "/admin-usuarios.html" :
                        autenticacion.getAuthorities().stream().anyMatch(a ->
                            "ROLE_FUNCIONARIO_DGMM".equals(a.getAuthority())
                            || "ROLE_AUDITOR".equals(a.getAuthority())
                        )
                            ? "/bandeja.html"
                            : "/naves.html"
                    )
                )

                // Avisa si la cuenta esta deshabilitada.
                .failureHandler((peticion, respuesta, error) ->
                    respuesta.sendRedirect(
                        error instanceof DisabledException
                            ? "/login.html?sinValidar=1"
                            : "/login.html?error=1"
                    )
                )
                .permitAll()
            )

            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessUrl("/login.html?salida=1")
                .permitAll()
            )

            // Las llamadas a la API sin sesion reciben HTTP 401.
            .exceptionHandling(ex ->
                ex.defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    ES_API
                )
            );

        return http.build();
    }
}
