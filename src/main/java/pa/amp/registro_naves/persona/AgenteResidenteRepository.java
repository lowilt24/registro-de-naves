package pa.amp.registro_naves.persona;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgenteResidenteRepository extends JpaRepository<AgenteResidente, Long> {

    /** La designacion vigente de una nave. HU-06 garantiza que hay a lo sumo una. */
    Optional<AgenteResidente> findByNaveIdAndVigenteTrue(Long naveId);

    /** Historial completo, de la designacion mas reciente a la mas antigua. */
    List<AgenteResidente> findByNaveIdOrderByFechaDesignacionDesc(Long naveId);
}
