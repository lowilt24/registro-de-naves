package pa.amp.registro_naves.persona;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PropietarioRepository extends JpaRepository<Propietario, Long> {

    boolean existsByIdentificacionIgnoreCase(String identificacion);
}
