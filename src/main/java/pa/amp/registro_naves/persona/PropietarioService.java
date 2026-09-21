package pa.amp.registro_naves.persona;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pa.amp.registro_naves.common.ReglaDeNegocioException;
import pa.amp.registro_naves.common.ValidacionCampoException;
import pa.amp.registro_naves.nave.AccesoNave;
import pa.amp.registro_naves.nave.Nave;
import pa.amp.registro_naves.usuario.UsuarioRepository;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PropietarioService {

    private static final BigDecimal TOTAL = new BigDecimal("100.00");

    private final PropietarioRepository propietarios;
    private final NavePropietarioRepository vinculos;
    private final UsuarioRepository usuarios;
    private final AccesoNave accesoNave;

    public PropietarioService(PropietarioRepository propietarios,
                              NavePropietarioRepository vinculos,
                              UsuarioRepository usuarios,
                              AccesoNave accesoNave) {
        this.propietarios = propietarios;
        this.vinculos = vinculos;
        this.usuarios = usuarios;
        this.accesoNave = accesoNave;
    }

    /** HU-05 — registrar un propietario y vincularlo a una nave propia. */
    @Transactional
    public PropietarioResponse registrar(PropietarioRequest peticion, String correoUsuario) {

        // 404 si la nave no existe, 403 si es de otro usuario.
        Nave nave = accesoNave.exigirPropia(peticion.naveId(), correoUsuario);

        // Regla condicional de HU-05: la sociedad necesita pais de constitucion.
        String paisConstitucion = limpiar(peticion.paisConstitucion());
        if (peticion.tipo() == TipoPersona.JURIDICA && paisConstitucion == null) {
            throw new ValidacionCampoException("paisConstitucion",
                    "El pais de constitucion es obligatorio para una persona juridica.");
        }
        // Una persona natural no se constituye en ningun pais.
        if (peticion.tipo() == TipoPersona.NATURAL) {
            paisConstitucion = null;
        }

        String identificacion = limpiar(peticion.identificacion());
        if (identificacion != null && propietarios.existsByIdentificacionIgnoreCase(identificacion)) {
            throw new ReglaDeNegocioException(
                    "Ya existe un propietario registrado con esa identificacion.");
        }

        // La cuota solicitada debe caber en lo que queda libre de la nave.
        BigDecimal porcentaje = peticion.porcentaje() == null ? TOTAL : peticion.porcentaje();
        BigDecimal asignado = vinculos.sumaPorcentajes(nave.getId());
        if (asignado.add(porcentaje).compareTo(TOTAL) > 0) {
            throw new ReglaDeNegocioException(
                    "La participacion excede el 100% de la nave. Disponible: "
                    + TOTAL.subtract(asignado) + "%.");
        }

        Propietario propietario = new Propietario();
        propietario.setNombre(peticion.nombre().trim());
        propietario.setTipo(peticion.tipo());
        propietario.setNacionalidad(peticion.nacionalidad().trim());
        propietario.setDomicilio(peticion.domicilio().trim());
        propietario.setPaisConstitucion(paisConstitucion);
        propietario.setIdentificacion(identificacion);
        usuarios.findByCorreoIgnoreCase(correoUsuario).ifPresent(propietario::setRegistradoPor);
        propietarios.save(propietario);

        NavePropietario vinculo = new NavePropietario();
        vinculo.setNave(nave);
        vinculo.setPropietario(propietario);
        vinculo.setPorcentaje(porcentaje);

        return PropietarioResponse.de(vinculos.saveAndFlush(vinculo));
    }

    /** Propietarios vinculados a una nave propia. */
    @Transactional(readOnly = true)
    public List<PropietarioResponse> listarPorNave(Long naveId, String correoUsuario) {
        accesoNave.exigirPropia(naveId, correoUsuario);
        return vinculos.findByNaveIdOrderByIdAsc(naveId).stream()
                .map(PropietarioResponse::de)
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
