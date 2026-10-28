package pa.amp.registro_naves.tramite;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TramiteRepository extends JpaRepository<Tramite, Long> {

    Optional<Tramite> findByNaveId(Long naveId);

    boolean existsByNaveId(Long naveId);

    /** Para pintar el estado del tramite en el listado sin una consulta por nave. */
    @Query("select t from Tramite t where t.nave.id in :naveIds")
    List<Tramite> buscarPorNaves(@Param("naveIds") Collection<Long> naveIds);

    /**
     * HU-10: cuantas naves del agente hay en cada estado. Devuelve pares
     * [EstadoTramite, Long]; los estados sin naves no aparecen y el
     * servicio los completa con cero.
     */
    @Query("""
           select t.estado, count(t)
             from Tramite t
            where lower(t.nave.registradoPor.correo) = lower(:correo)
            group by t.estado
           """)
    List<Object[]> contarPorEstado(@Param("correo") String correo);

    /**
     * HU-11: bandeja de la DGMM. Tramites de todas las naves en los estados
     * pedidos, el que lleva mas tiempo sin moverse primero. Trae la nave y
     * quien la registro en la misma consulta.
     */
    @Query("""
           select t from Tramite t
             join fetch t.nave n
             join fetch n.registradoPor
            where t.estado in :estados
            order by t.actualizadoEn asc, t.id asc
           """)
    List<Tramite> bandeja(@Param("estados") Collection<EstadoTramite> estados);

    /** HU-11: cuantos tramites hay en cada estado, de todas las naves. */
    @Query("select t.estado, count(t) from Tramite t group by t.estado")
    List<Object[]> contarTodosPorEstado();
}
