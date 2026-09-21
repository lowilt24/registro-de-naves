package pa.amp.registro_naves.persona;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pa.amp.registro_naves.common.RecursoNoEncontradoException;
import pa.amp.registro_naves.common.ReglaDeNegocioException;
import pa.amp.registro_naves.nave.AccesoNave;
import pa.amp.registro_naves.nave.Nave;
import pa.amp.registro_naves.usuario.UsuarioRepository;

import java.util.List;
import java.util.Optional;

@Service
public class AgenteService {

    private final AgenteResidenteRepository agentes;
    private final NavePropietarioRepository vinculos;
    private final UsuarioRepository usuarios;
    private final AccesoNave accesoNave;

    public AgenteService(AgenteResidenteRepository agentes,
                         NavePropietarioRepository vinculos,
                         UsuarioRepository usuarios,
                         AccesoNave accesoNave) {
        this.agentes = agentes;
        this.vinculos = vinculos;
        this.usuarios = usuarios;
        this.accesoNave = accesoNave;
    }

    /**
     * HU-06 — designar el agente residente de una nave.
     *
     * Si la nave ya tenia agente, el anterior queda marcado como no
     * vigente y se conserva como historial en vez de borrarse: el
     * expediente de abanderamiento necesita saber quien gestionaba el
     * tramite en cada momento.
     */
    @Transactional
    public AgenteResponse designar(Long naveId, AgenteRequest peticion, String correoUsuario) {

        Nave nave = accesoNave.exigirPropia(naveId, correoUsuario);

        // Precondicion de HU-06.
        if (!vinculos.existsByNaveId(naveId)) {
            throw new ReglaDeNegocioException(
                    "La nave no tiene propietarios vinculados. Registre al menos uno antes "
                    + "de designar agente residente.");
        }

        // Reemplazar la designacion vigente, si la hay.
        Optional<AgenteResidente> anterior = agentes.findByNaveIdAndVigenteTrue(naveId);
        anterior.ifPresent(a -> {
            a.setVigente(false);
            agentes.save(a);
        });
        // El indice unico parcial de la base solo admite una fila vigente por
        // nave; sin este flush, la nueva insercion chocaria con la anterior.
        agentes.flush();

        AgenteResidente agente = new AgenteResidente();
        agente.setNave(nave);
        agente.setNombre(peticion.nombre().trim());
        agente.setIdoneidad(limpiar(peticion.idoneidad()));
        agente.setTelefono(peticion.telefono().trim());
        agente.setCorreo(peticion.correo().trim().toLowerCase());
        agente.setPoderNumero(limpiar(peticion.poderNumero()));
        agente.setPoderFecha(peticion.poderFecha());
        agente.setPoderLugar(limpiar(peticion.poderLugar()));
        agente.setVigente(true);
        usuarios.findByCorreoIgnoreCase(correoUsuario).ifPresent(agente::setDesignadoPor);

        return AgenteResponse.de(agentes.saveAndFlush(agente));
    }

    /** HU-06 — consultar el agente vigente de una nave. */
    @Transactional(readOnly = true)
    public AgenteResponse consultarVigente(Long naveId, String correoUsuario) {
        accesoNave.exigirPropia(naveId, correoUsuario);
        return agentes.findByNaveIdAndVigenteTrue(naveId)
                .map(AgenteResponse::de)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La nave no tiene agente residente designado."));
    }

    /** Historial de designaciones, para el expediente. */
    @Transactional(readOnly = true)
    public List<AgenteResponse> historial(Long naveId, String correoUsuario) {
        accesoNave.exigirPropia(naveId, correoUsuario);
        return agentes.findByNaveIdOrderByFechaDesignacionDesc(naveId).stream()
                .map(AgenteResponse::de)
                .toList();
    }

    private String limpiar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
