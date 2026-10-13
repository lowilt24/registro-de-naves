package pa.amp.registro_naves.nave;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pa.amp.registro_naves.common.RecursoNoEncontradoException;
import pa.amp.registro_naves.common.ReglaDeNegocioException;
import pa.amp.registro_naves.tramite.EstadoTramite;
import pa.amp.registro_naves.tramite.Tramite;
import pa.amp.registro_naves.tramite.TramiteService;
import pa.amp.registro_naves.usuario.Rol;
import pa.amp.registro_naves.usuario.Usuario;
import pa.amp.registro_naves.usuario.UsuarioRepository;

import java.util.List;
import java.util.Map;

@Service
public class NaveService {

    private final NaveRepository naves;
    private final UsuarioRepository usuarios;
    private final AccesoNave accesoNave;
    private final TramiteService tramites;

    public NaveService(NaveRepository naves,
                       UsuarioRepository usuarios,
                       AccesoNave accesoNave,
                       TramiteService tramites) {
        this.naves = naves;
        this.usuarios = usuarios;
        this.accesoNave = accesoNave;
        this.tramites = tramites;
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
     *
     * Sprint 5 (D3): en la misma transaccion nace el tramite de la nave,
     * en SOLICITUD_PRESENTADA (P1: presentar equivale a registrar). Si algo
     * falla, no queda ni la nave ni el tramite.
     */
    @Transactional
    public NaveResponse registrar(NaveRequest peticion, String correoUsuario) {

        Usuario usuario = usuarios.findByCorreoIgnoreCase(correoUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se pudo identificar al usuario de la sesión."));

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

        Nave guardada;
        try {
            guardada = naves.saveAndFlush(nave);
        } catch (DataIntegrityViolationException ex) {
            // Dos usuarios enviaron el mismo nombre casi al mismo tiempo.
            throw new ReglaDeNegocioException(
                    "Nombre no disponible. Ya existe una nave registrada con ese nombre.");
        }

        Tramite tramite = tramites.crearPara(guardada, usuario);
        return NaveResponse.de(guardada, tramite.getEstado());
    }

    /** Solo las naves del usuario de la sesion. */
    @Transactional(readOnly = true)
    public List<NaveResponse> listarPropias(String correoUsuario) {
        return listarPropias(correoUsuario, null);
    }

    /**
     * HU-10. Sin estado, la lista de siempre (no rompe naves.js ni TS02).
     * Con estado, solo las naves del usuario cuyo tramite esta en ese
     * estado (D4: se filtra por TRAMITES).
     */
    @Transactional(readOnly = true)
    public List<NaveResponse> listarPropias(String correoUsuario, EstadoTramite estadoTramite) {
        List<Nave> lista = estadoTramite == null
                ? naves.findByRegistradoPorCorreoIgnoreCaseOrderByIdAsc(correoUsuario)
                : naves.buscarPropiasPorEstadoTramite(correoUsuario, estadoTramite);

        Map<Long, EstadoTramite> estados = tramites.estadosDe(
                lista.stream().map(Nave::getId).toList());

        return lista.stream()
                .map(n -> NaveResponse.de(n, estados.get(n.getId())))
                .toList();
    }

    /**
     * Ficha de una nave. El agente solo ve las suyas (AccesoNave).
     *
     * Sprint 5: la DGMM actua sobre cualquier tramite (D1) y abre el
     * expediente por el ID de la nave mientras no exista su bandeja (P7);
     * el AUDITOR lee (P8). Los dos ven la ficha de cualquier nave. Las
     * demas camaras (propietarios, agente, documentos) siguen siendo solo
     * del agente que la registro.
     */
    @Transactional(readOnly = true)
    public NaveResponse obtener(Long id, String correoUsuario) {
        Rol rol = usuarios.findByCorreoIgnoreCase(correoUsuario)
                .map(Usuario::getRol)
                .orElse(null);

        Nave nave = (rol == Rol.FUNCIONARIO_DGMM || rol == Rol.AUDITOR)
                ? naves.findById(id).orElseThrow(() ->
                        new RecursoNoEncontradoException("La nave indicada no existe."))
                : accesoNave.exigirPropia(id, correoUsuario);

        return NaveResponse.de(nave, tramites.estadosDe(List.of(nave.getId())).get(nave.getId()));
    }
}
