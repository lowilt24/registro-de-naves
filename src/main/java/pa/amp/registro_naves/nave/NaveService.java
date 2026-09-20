package pa.amp.registro_naves.nave;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pa.amp.registro_naves.common.ReglaDeNegocioException;
import pa.amp.registro_naves.common.RecursoNoEncontradoException;
import pa.amp.registro_naves.persona.AgenteResidente;
import pa.amp.registro_naves.persona.AgenteResidenteRepository;
import pa.amp.registro_naves.persona.Propietario;
import pa.amp.registro_naves.persona.PropietarioRepository;
import pa.amp.registro_naves.usuario.Usuario;
import pa.amp.registro_naves.usuario.UsuarioRepository;

import java.util.List;

@Service
public class NaveService {

    private final NaveRepository naves;
    private final PropietarioRepository propietarios;
    private final AgenteResidenteRepository agentes;
    private final UsuarioRepository usuarios;

    public NaveService(NaveRepository naves,
                       PropietarioRepository propietarios,
                       AgenteResidenteRepository agentes,
                       UsuarioRepository usuarios) {
        this.naves = naves;
        this.propietarios = propietarios;
        this.agentes = agentes;
        this.usuarios = usuarios;
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
     * La verificacion previa da un mensaje claro; la restriccion UNIQUE de la
     * tabla cierra la carrera entre dos registros simultaneos.
     */
    @Transactional
    public NaveResponse registrar(NaveRequest peticion, String correoUsuario) {

        if (naves.existsByNombreNormalizado(Nave.normalizar(peticion.nombre()))) {
            throw new ReglaDeNegocioException(
                    "Nombre no disponible. Ya existe una nave registrada con ese nombre.");
        }

        if (peticion.tonelajeNeto().compareTo(peticion.tonelajeBruto()) > 0) {
            throw new ReglaDeNegocioException(
                    "El tonelaje neto no puede ser mayor que el tonelaje bruto.");
        }

        Propietario propietario = propietarios.findById(peticion.propietarioId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El propietario seleccionado no esta registrado."));

        AgenteResidente agente = agentes.findById(peticion.agenteResidenteId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El agente residente seleccionado no esta registrado."));

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
        nave.setPropietario(propietario);
        nave.setAgenteResidente(agente);

        if (correoUsuario != null) {
            usuarios.findByCorreoIgnoreCase(correoUsuario).ifPresent(nave::setRegistradoPor);
        }

        try {
            return NaveResponse.de(naves.saveAndFlush(nave));
        } catch (DataIntegrityViolationException ex) {
            // Dos usuarios enviaron el mismo nombre casi al mismo tiempo.
            throw new ReglaDeNegocioException(
                    "Nombre no disponible. Ya existe una nave registrada con ese nombre.");
        }
    }

    @Transactional(readOnly = true)
    public List<NaveResponse> listar() {
        return naves.findAll().stream().map(NaveResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public NaveResponse obtener(Long id) {
        return naves.findById(id)
                .map(NaveResponse::de)
                .orElseThrow(() -> new RecursoNoEncontradoException("La nave solicitada no existe."));
    }
}
