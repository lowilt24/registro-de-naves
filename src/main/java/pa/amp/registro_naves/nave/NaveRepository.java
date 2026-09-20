package pa.amp.registro_naves.nave;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NaveRepository extends JpaRepository<Nave, Long> {

    /**
     * HU-04. Spring Data genera una consulta parametrizada (bind variables),
     * por lo que un payload como  ' OR '1'='1  entra como texto literal y
     * nunca como SQL. Es la mitigacion que reportamos contra el fuzzing.
     */
    boolean existsByNombreNormalizado(String nombreNormalizado);
}
