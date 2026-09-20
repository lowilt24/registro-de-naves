package pa.amp.registro_naves.usuario;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pa.amp.registro_naves.common.ReglaDeNegocioException;

@Service
public class UsuarioService {

    private final UsuarioRepository repositorio;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository repositorio, PasswordEncoder passwordEncoder) {
        this.repositorio = repositorio;
        this.passwordEncoder = passwordEncoder;
    }

    /** HU-01: registro de usuario. */
    @Transactional
    public Usuario registrar(String correo, String password, String nombreCompleto, Rol rol) {
        String correoLimpio = correo == null ? "" : correo.trim().toLowerCase();

        if (repositorio.existsByCorreoIgnoreCase(correoLimpio)) {
            throw new ReglaDeNegocioException("Ya existe una cuenta con ese correo.");
        }

        Usuario usuario = new Usuario();
        usuario.setCorreo(correoLimpio);
        usuario.setPasswordHash(passwordEncoder.encode(password));
        usuario.setNombreCompleto(nombreCompleto == null ? "" : nombreCompleto.trim());
        usuario.setRol(rol == null ? Rol.AGENTE_NAVIERO : rol);
        return repositorio.save(usuario);
    }
}
