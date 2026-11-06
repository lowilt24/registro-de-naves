package pa.amp.registro_naves.usuario;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pa.amp.registro_naves.common.ReglaDeNegocioException;
import pa.amp.registro_naves.common.ValidacionCampoException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class UsuarioService {

    /** El enlace de validacion vence a las 24 horas. */
    static final Duration VIGENCIA_TOKEN = Duration.ofHours(24);

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final UsuarioRepository repositorio;
    private final PasswordEncoder passwordEncoder;
    private final NotificadorCorreo notificador;
    private final String urlBase;

    public UsuarioService(
            UsuarioRepository repositorio,
            PasswordEncoder passwordEncoder,
            NotificadorCorreo notificador,
            @Value("${navesitas.url-base:http://localhost:8080}") String urlBase) {
        this.repositorio = repositorio;
        this.passwordEncoder = passwordEncoder;
        this.notificador = notificador;
        this.urlBase = urlBase.endsWith("/")
                ? urlBase.substring(0, urlBase.length() - 1)
                : urlBase;
    }

    /**
     * HU-01: el registro publico permite crear agentes navieros.
     * La cuenta debe validar su correo antes de iniciar sesion.
     */
    @Transactional
    public Usuario registrar(
            String correo,
            String password,
            String nombreCompleto,
            Rol rol) {

        Rol rolFinal = rol == null ? Rol.AGENTE_NAVIERO : rol;

        if (!rolFinal.esAutoasignable()) {
            throw new ValidacionCampoException(
                    "rol",
                    "Ese rol no se puede elegir al crear una cuenta.");
        }

        return crearCuenta(correo, password, nombreCompleto, rolFinal);
    }

    /**
     * HU-14: alta de funcionarios DGMM y auditores.
     * Se invoca desde la ruta /api/admin/**, protegida para ADMINISTRADOR.
     * No permite crear administradores ni agentes navieros.
     */
    @Transactional
    public Usuario crearUsuarioInterno(
            String correo,
            String password,
            String nombreCompleto,
            Rol rol) {

        if (rol != Rol.FUNCIONARIO_DGMM && rol != Rol.AUDITOR) {
            throw new ValidacionCampoException(
                    "rol",
                    "Seleccione Funcionario DGMM o Auditor.");
        }

        return crearCuenta(correo, password, nombreCompleto, rol);
    }

    /**
     * Logica compartida por el registro publico y el alta administrativa.
     */
    private Usuario crearCuenta(
            String correo,
            String password,
            String nombreCompleto,
            Rol rol) {

        String correoLimpio = correo == null
                ? ""
                : correo.trim().toLowerCase(Locale.ROOT);

        if (repositorio.existsByCorreoIgnoreCase(correoLimpio)) {
            throw new ReglaDeNegocioException(
                    "Ya existe una cuenta con ese correo.");
        }

        Usuario usuario = new Usuario();
        usuario.setCorreo(correoLimpio);
        usuario.setPasswordHash(passwordEncoder.encode(password));
        usuario.setNombreCompleto(
                nombreCompleto == null ? "" : nombreCompleto.trim());
        usuario.setRol(rol);
        usuario.setActivo(true);
        usuario.setCorreoValidado(false);

        String token = emitirToken(usuario);
        Usuario guardado = repositorio.save(usuario);
        enviarEnlace(guardado, token);

        return guardado;
    }

    /** Resultado de abrir el enlace del correo. */
    public enum ResultadoValidacion {
        VALIDADO,
        INVALIDO,
        VENCIDO
    }

    @Transactional
    public ResultadoValidacion validar(String token) {
        if (token == null || token.isBlank()) {
            return ResultadoValidacion.INVALIDO;
        }

        return repositorio.findByTokenValidacion(hash(token.trim()))
                .map(usuario -> {
                    if (usuario.getTokenExpiraEn() == null
                            || usuario.getTokenExpiraEn()
                                    .isBefore(OffsetDateTime.now())) {
                        return ResultadoValidacion.VENCIDO;
                    }

                    usuario.setCorreoValidado(true);
                    usuario.setTokenValidacion(null);
                    usuario.setTokenExpiraEn(null);
                    repositorio.save(usuario);

                    return ResultadoValidacion.VALIDADO;
                })
                .orElse(ResultadoValidacion.INVALIDO);
    }

    /**
     * Reenvia el enlace sin revelar si el correo tiene una cuenta.
     */
    @Transactional
    public void reenviarValidacion(String correo) {
        if (correo == null) {
            return;
        }

        repositorio.findByCorreoIgnoreCase(correo.trim())
                .filter(usuario -> !usuario.isCorreoValidado())
                .ifPresent(usuario -> {
                    String token = emitirToken(usuario);
                    repositorio.save(usuario);
                    enviarEnlace(usuario, token);
                });
    }

    /**
     * Guarda el hash del token y devuelve el token original
     * para incluirlo en el enlace del correo.
     */
    private String emitirToken(Usuario usuario) {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);

        String token = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);

        usuario.setTokenValidacion(hash(token));
        usuario.setTokenExpiraEn(
                OffsetDateTime.now().plus(VIGENCIA_TOKEN));

        return token;
    }

    private void enviarEnlace(Usuario usuario, String token) {
        String enlace = urlBase + "/api/auth/validar?token=" + token;

        notificador.enviarValidacion(
                usuario.getCorreo(),
                usuario.getNombreCompleto(),
                enlace);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 no disponible", e);
        }
    }
}