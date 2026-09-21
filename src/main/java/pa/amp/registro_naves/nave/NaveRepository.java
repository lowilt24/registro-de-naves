package pa.amp.registro_naves.nave;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
