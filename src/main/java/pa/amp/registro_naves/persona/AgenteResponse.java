package pa.amp.registro_naves.persona;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AgenteResponse(
        Long id,
        Long naveId,
        String nave,
        String nombre,
        String idoneidad,
        String telefono,
        String correo,
        String poderNumero,
        LocalDate poderFecha,
        String poderLugar,
        boolean vigente,
        LocalDateTime fechaDesignacion
) {
    public static AgenteResponse de(AgenteResidente a) {
        return new AgenteResponse(
                a.getId(),
                a.getNave().getId(),
                a.getNave().getNombre(),
                a.getNombre(),
                a.getIdoneidad(),
                a.getTelefono(),
                a.getCorreo(),
                a.getPoderNumero(),
                a.getPoderFecha(),
                a.getPoderLugar(),
                a.isVigente(),
                a.getFechaDesignacion());
    }
}
