package pa.amp.registro_naves.nave;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pa.amp.registro_naves.tramite.EstadoTramite;

import java.util.List;
import java.util.Optional;

public interface NaveRepository extends JpaRepository<Nave, Long> {

    /**
     * HU-04. Spring Data genera una consulta parametrizada (bind variables),
     * por lo que un payload como  ' OR '1'='1  entra como texto literal y
     * nunca como SQL. Verificado por fuzzing en el Sprint 2.
     */
    boolean existsByNombreNormalizado(String nombreNormalizado);

    /** Buscar por el nombre ya normalizado (mayusculas, espacios colapsados). */
    Optional<Nave> findByNombreNormalizado(String nombreNormalizado);

    /** Naves cuyo expediente abrio el usuario indicado. */
    List<Nave> findByRegistradoPorCorreoIgnoreCaseOrderByIdAsc(String correo);

    /**
     * HU-10: naves del usuario cuyo tramite esta en el estado indicado.
     * D4: se filtra por TRAMITES, no por nave.estado.
     */
    @Query("""
           select n from Nave n
            where lower(n.registradoPor.correo) = lower(:correo)
              and exists (select t from Tramite t where t.nave = n and t.estado = :estado)
            order by n.id asc
           """)
    List<Nave> buscarPropiasPorEstadoTramite(@Param("correo") String correo,
                                             @Param("estado") EstadoTramite estado);
}
