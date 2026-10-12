package pa.amp.registro_naves.documento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** La descarga exige que el documento sea de la nave de la ruta (IDOR). */
    Optional<Documento> findByIdAndNaveId(Long id, Long naveId);
}
