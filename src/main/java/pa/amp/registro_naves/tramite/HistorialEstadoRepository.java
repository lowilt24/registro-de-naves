package pa.amp.registro_naves.tramite;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HistorialEstadoRepository extends JpaRepository<HistorialEstado, Long> {

    /** Linea de tiempo del tramite, de la mas vieja a la mas nueva. */
    @Query("""
           select h from HistorialEstado h
             join fetch h.usuario
            where h.tramite.id = :tramiteId
            order by h.fecha asc, h.id asc
           """)
    List<HistorialEstado> lineaDeTiempo(@Param("tramiteId") Long tramiteId);

    long countByTramiteId(Long tramiteId);
}
