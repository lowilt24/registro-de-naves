package pa.amp.registro_naves.nave;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pa.amp.registro_naves.common.RecursoNoEncontradoException;
import pa.amp.registro_naves.common.ReglaDeNegocioException;
import pa.amp.registro_naves.usuario.Usuario;
import pa.amp.registro_naves.usuario.UsuarioRepository;

import java.util.List;

@Service
public class NaveService {

    private final NaveRepository naves;
    private final UsuarioRepository usuarios;
    private final AccesoNave accesoNave;

    public NaveService(NaveRepository naves,
                       UsuarioRepository usuarios,
                       AccesoNave accesoNave) {
        this.naves = naves;
        this.usuarios = usuarios;
        this.accesoNave = accesoNave;
    }

    /**
     * HU-04 — consulta de disponibilidad de nombre.
     * Un nombre vacio no se considera disponible: no hay nada que reservar.
     */
    @Transactional(readOnly = true)
    public DisponibilidadResponse consultarDisponibilidad(String nombre) {
        String normalizado = Nave.normalizar(nombre);

        if (normalizado.isEmpty()) {
            return new DisponibilidadResponse(
                    "", false, "Escriba un nombre para consultar su disponibilidad.");
        }

        boolean ocupado = naves.existsByNombreNormalizado(normalizado);

        return ocupado
                ? new DisponibilidadResponse(normalizado, false,
                    "Nombre no disponible. Ya existe una nave registrada con ese nombre.")
                : new DisponibilidadResponse(normalizado, true,
                    "Nombre disponible. Puede continuar con el registro.");
    }

    /**
     * HU-03 — registro de nave, con el bloqueo de HU-04 aplicado en el servidor.
     *
     * Sprint 3: el usuario autenticado queda registrado como dueno del
     * expediente. De ahi depende el control de acceso de HU-05 y HU-06.
     */
    @Transactional
    public NaveResponse registrar(NaveRequest peticion, String correoUsuario) {

        Usuario usuario = usuarios.findByCorreoIgnoreCase(correoUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se pudo identificar al usuario de la sesion."));

        if (naves.existsByNombreNormalizado(Nave.normalizar(peticion.nombre()))) {
            throw new ReglaDeNegocioException(
                    "Nombre no disponible. Ya existe una nave registrada con ese nombre.");
        }

        if (peticion.tonelajeNeto().compareTo(peticion.tonelajeBruto()) > 0) {
            throw new ReglaDeNegocioException(
                    "El tonelaje neto no puede ser mayor que el tonelaje bruto.");
        }

        Nave nave = new Nave();
        nave.setNombre(peticion.nombre());
        nave.setTipo(peticion.tipo());
        nave.setServicio(peticion.servicio());
        nave.setTonelajeBruto(peticion.tonelajeBruto());
        nave.setTonelajeNeto(peticion.tonelajeNeto());
        nave.setEslora(peticion.eslora());
        nave.setManga(peticion.manga());
        nave.setPuntal(peticion.puntal());
        nave.setAnioConstruccion(peticion.anioConstruccion());
        nave.setLugarConstruccion(peticion.lugarConstruccion().trim());
        nave.setMaterialCasco(peticion.materialCasco().trim());
        nave.setTipoPropulsion(peticion.tipoPropulsion().trim());
        nave.setPotenciaKw(peticion.potenciaKw());
        nave.setEstado(EstadoNave.REGISTRADA);
        nave.setRegistradoPor(usuario);

        try {
            return NaveResponse.de(naves.saveAndFlush(nave));
        } catch (DataIntegrityViolationException ex) {
            // Dos usuarios enviaron el mismo nombre casi al mismo tiempo.
            throw new ReglaDeNegocioException(
                    "Nombre no disponible. Ya existe una nave registrada con ese nombre.");
        }
    }

    /** Solo las naves del usuario de la sesion. */
    @Transactional(readOnly = true)
    public List<NaveResponse> listarPropias(String correoUsuario) {
        return naves.findByRegistradoPorCorreoIgnoreCaseOrderByIdAsc(correoUsuario).stream()
                .map(NaveResponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public NaveResponse obtener(Long id, String correoUsuario) {
        return NaveResponse.de(accesoNave.exigirPropia(id, correoUsuario));
    }
}
