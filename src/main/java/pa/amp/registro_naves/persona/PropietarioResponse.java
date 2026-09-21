package pa.amp.registro_naves.persona;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PropietarioResponse(
        Long id,
        String nombre,
        String tipo,
        String nacionalidad,
        String domicilio,
        String paisConstitucion,
        String identificacion,
        Long naveId,
        String nave,
        BigDecimal porcentaje,
        LocalDateTime fechaVinculacion
) {
    public static PropietarioResponse de(NavePropietario vinculo) {
        Propietario p = vinculo.getPropietario();
        return new PropietarioResponse(
                p.getId(),
                p.getNombre(),
                p.getTipo().name(),
                p.getNacionalidad(),
                p.getDomicilio(),
                p.getPaisConstitucion(),
                p.getIdentificacion(),
                vinculo.getNave().getId(),
                vinculo.getNave().getNombre(),
                vinculo.getPorcentaje(),
                vinculo.getFechaVinculacion());
    }
}
