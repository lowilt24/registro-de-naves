package pa.amp.registro_naves.nave;

import pa.amp.registro_naves.tramite.EstadoTramite;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

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
        /** Estado de la nave (nave.estado). No es el estado del tramite. */
        String estado,
        OffsetDateTime creadoEn,
        /**
         * Sprint 5 (D4): estado del tramite, que vive en TRAMITES. Va aparte
         * de "estado" para que la pantalla los rotule por separado (P6).
         */
        String estadoTramite
) {
    public static NaveResponse de(Nave n) {
        return de(n, null);
    }

    public static NaveResponse de(Nave n, EstadoTramite estadoTramite) {
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
                n.getCreadoEn(),
                estadoTramite == null ? null : estadoTramite.name());
    }
}
