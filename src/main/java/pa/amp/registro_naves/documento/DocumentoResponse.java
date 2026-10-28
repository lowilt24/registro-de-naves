package pa.amp.registro_naves.documento;

import java.time.OffsetDateTime;

/** La ruta en disco no sale nunca del servidor. */
public record DocumentoResponse(
        Long id,
        Long naveId,
        String tipo,
        String nombreArchivo,
        Long tamanoBytes,
        String hashSha256,
        Integer version,
        boolean vigente,
        String estado,
        OffsetDateTime creadoEn,
        /** Sprint 6 (HU-12): obligatoria si esta OBSERVADO; nota opcional si APROBADO. */
        String observacion,
        /** Nombre del funcionario, no su correo (observacion 6 de QA). */
        String revisadoPor,
        OffsetDateTime revisadoEn
) {
    public static DocumentoResponse de(Documento d) {
        return new DocumentoResponse(
                d.getId(),
                d.getNave().getId(),
                d.getTipo().name(),
                d.getNombreArchivo(),
                d.getTamanoBytes(),
                d.getHashSha256(),
                d.getVersion(),
                d.isVigente(),
                d.getEstado().name(),
                d.getCreadoEn(),
                d.getObservacion(),
                d.getRevisadoPor() == null ? null : d.getRevisadoPor().getNombreCompleto(),
                d.getRevisadoEn());
    }
}
