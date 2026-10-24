package pa.amp.registro_naves.documento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pa.amp.registro_naves.usuario.Usuario;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {

    /**
     * HU-08, criterio 4: una sola consulta con un join entre naves y
     * documentos, sin consultas en bucle. Los filtros nulos no filtran.
     */
    @Query("""
           SELECT d
             FROM Documento d
             JOIN FETCH d.nave n
             LEFT JOIN FETCH d.revisadoPor
            WHERE n.id = :naveId
              AND (:historial = true OR d.vigente = true)
              AND (:tipo   IS NULL OR d.tipo   = :tipo)
              AND (:estado IS NULL OR d.estado = :estado)
            ORDER BY d.tipo, d.version DESC
           """)
    List<Documento> buscar(@Param("naveId") Long naveId,
                           @Param("tipo") TipoDocumento tipo,
                           @Param("estado") EstadoDocumento estado,
                           @Param("historial") boolean historial);

    Optional<Documento> findByNaveIdAndTipoAndVigenteTrue(Long naveId, TipoDocumento tipo);

    /** Sprint 5: los vigentes de la nave, para la regla de documentos obligatorios. */
    List<Documento> findByNaveIdAndVigenteTrue(Long naveId);

    /** La descarga exige que el documento sea de la nave de la ruta (IDOR). */
    Optional<Documento> findByIdAndNaveId(Long id, Long naveId);

    /**
     * HU-12: escribe la revision solo si el documento sigue vigente y
     * PENDIENTE. Devuelve cuantas filas cambio: 0 quiere decir que otro
     * funcionario lo reviso o que el agente lo reemplazo entre la lectura y
     * la escritura, y el servicio responde 409 (observacion 4 de QA). Es un
     * UPDATE condicional en lugar de @Version porque "version" ya es el
     * numero de version del documento (HU-07).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
           UPDATE Documento d
              SET d.estado = :estado,
                  d.observacion = :observacion,
                  d.revisadoPor = :revisor,
                  d.revisadoEn = :cuando
            WHERE d.id = :id
              AND d.vigente = true
              AND d.estado = pa.amp.registro_naves.documento.EstadoDocumento.PENDIENTE
           """)
    int revisar(@Param("id") Long id,
                @Param("estado") EstadoDocumento estado,
                @Param("observacion") String observacion,
                @Param("revisor") Usuario revisor,
                @Param("cuando") OffsetDateTime cuando);
}
