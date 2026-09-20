package pa.amp.registro_naves.nave;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record NaveResponse(
        Long id,
        String nombre,
        String tipo,
        String servicio,
        BigDecimal tonelajeBruto,
        BigDecimal tonelajeNeto,
        BigDecimal eslora,
        BigDecimal manga,
        BigDecimal puntal,
        Integer anioConstruccion,
        String lugarConstruccion,
        String materialCasco,
        String tipoPropulsion,
        BigDecimal potenciaKw,
        String estado,
        LocalDateTime fechaRegistro,
        String propietario,
        String agenteResidente
) {
    public static NaveResponse de(Nave n) {
        return new NaveResponse(
                n.getId(),
                n.getNombre(),
                n.getTipo().name(),
                n.getServicio().name(),
                n.getTonelajeBruto(),
                n.getTonelajeNeto(),
                n.getEslora(),
                n.getManga(),
                n.getPuntal(),
                n.getAnioConstruccion(),
                n.getLugarConstruccion(),
                n.getMaterialCasco(),
                n.getTipoPropulsion(),
                n.getPotenciaKw(),
                n.getEstado().name(),
                n.getFechaRegistro(),
                n.getPropietario().getNombre(),
                n.getAgenteResidente().getNombre());
    }
}
