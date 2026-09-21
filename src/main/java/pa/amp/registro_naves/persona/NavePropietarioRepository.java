package pa.amp.registro_naves.persona;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface NavePropietarioRepository extends JpaRepository<NavePropietario, Long> {

    List<NavePropietario> findByNaveIdOrderByIdAsc(Long naveId);

    boolean existsByNaveId(Long naveId);

    boolean existsByNaveIdAndPropietarioId(Long naveId, Long propietarioId);

    /** Suma de cuotas ya asignadas de una nave. Devuelve 0 si no tiene ninguna. */
    @Query("""
           SELECT COALESCE(SUM(np.porcentaje), 0)
             FROM NavePropietario np
            WHERE np.nave.id = :naveId
           """)
    BigDecimal sumaPorcentajes(@Param("naveId") Long naveId);
}
