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

@Service
public class UsuarioService {

    /** Observacion de la profesora: el enlace vence a las 24 horas. */
    static final Duration VIGENCIA_TOKEN = Duration.ofHours(24);

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final UsuarioRepository repositorio;
    private final PasswordEncoder passwordEncoder;
    private final NotificadorCorreo notificador;
    private final String urlBase;

    public UsuarioService(UsuarioRepository repositorio,
                          PasswordEncoder passwordEncoder,
                          NotificadorCorreo notificador,
                          @Value("${navesitas.url-base:http://localhost:8080}") String urlBase) {
        this.repositorio = repositorio;
        this.passwordEncoder = passwordEncoder;
        this.notificador = notificador;
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
    }

    /**
     * HU-01: registro de usuario.
     *
     * Sprint 4: la cuenta nace sin validar y se envia un enlace al correo.
     * Hasta abrirlo, UsuarioDetailsService la trata como deshabilitada.
     */
    @Transactional
    public Usuario registrar(String correo, String password, String nombreCompleto, Rol rol) {
        String correoLimpio = correo == null ? "" : correo.trim().toLowerCase();

        if (repositorio.existsByCorreoIgnoreCase(correoLimpio)) {
            throw new ReglaDeNegocioException("Ya existe una cuenta con ese correo.");
        }

        // Nadie se puede crear a si mismo una cuenta de auditor o de
        // administrador: esos roles los asigna alguien con permiso.
        Rol rolFinal = rol == null ? Rol.AGENTE_NAVIERO : rol;
        if (!rolFinal.esAutoasignable()) {
            throw new ValidacionCampoException("rol",
                    "Ese rol no se puede elegir al crear una cuenta.");
        }

        Usuario usuario = new Usuario();
        usuario.setCorreo(correoLimpio);
        usuario.setPasswordHash(passwordEncoder.encode(password));
        usuario.setNombreCompleto(nombreCompleto == null ? "" : nombreCompleto.trim());
        usuario.setRol(rolFinal);
        usuario.setCorreoValidado(false);

        String token = emitirToken(usuario);
        Usuario guardado = repositorio.save(usuario);
        enviarEnlace(guardado, token);
        return guardado;
    }

    /** Resultado de abrir el enlace del correo. */
    public enum ResultadoValidacion { VALIDADO, INVALIDO, VENCIDO }

    @Transactional
    public ResultadoValidacion validar(String token) {
        if (token == null || token.isBlank()) {
            return ResultadoValidacion.INVALIDO;
        }
        return repositorio.findByTokenValidacion(hash(token.trim()))
                .map(u -> {
                    if (u.getTokenExpiraEn() == null || u.getTokenExpiraEn().isBefore(OffsetDateTime.now())) {
                        return ResultadoValidacion.VENCIDO;
                    }
                    u.setCorreoValidado(true);
                    u.setTokenValidacion(null);
                    u.setTokenExpiraEn(null);
                    repositorio.save(u);
                    return ResultadoValidacion.VALIDADO;
                })
                .orElse(ResultadoValidacion.INVALIDO);
    }

    /**
     * Reenvio del enlace (el anterior vencio o no llego). No dice si el
     * correo existe: responde igual en todos los casos, para que no sirva
     * para averiguar que correos tienen cuenta.
     */
    @Transactional
    public void reenviarValidacion(String correo) {
        if (correo == null) {
            return;
        }
        repositorio.findByCorreoIgnoreCase(correo.trim())
                .filter(u -> !u.isCorreoValidado())
                .ifPresent(u -> {
                    String token = emitirToken(u);
                    repositorio.save(u);
                    enviarEnlace(u, token);
                });
    }

    // ------------------------------------------------------------------

    /**
     * Genera un token nuevo, guarda su hash en el usuario y devuelve el
     * token en claro, que solo viaja en el enlace del correo.
     */
    private String emitirToken(Usuario usuario) {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        usuario.setTokenValidacion(hash(token));
        usuario.setTokenExpiraEn(OffsetDateTime.now().plus(VIGENCIA_TOKEN));
        return token;
    }

    private void enviarEnlace(Usuario usuario, String token) {
        String enlace = urlBase + "/api/auth/validar?token=" + token;
        notificador.enviarValidacion(usuario.getCorreo(), usuario.getNombreCompleto(), enlace);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
