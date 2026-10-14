package pa.amp.registro_naves.common;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEC-08, red de seguridad: un texto con caracter nulo que no paso por el
 * lector de JSON (formulario, multipart) y llega a PostgreSQL es un dato
 * invalido del cliente (400), no un error del servidor. Cualquier otro
 * error de integridad sigue siendo 500.
 */
class ManejadorGlobalDeErroresTest {

    private final ManejadorGlobalDeErrores manejador = new ManejadorGlobalDeErrores();

    @Test
    void elCaracterNuloRechazadoPorLaBaseEs400() {
        var ex = new DataIntegrityViolationException("insert",
                new SQLException("invalid byte sequence for encoding \"UTF8\": 0x00", "22021"));

        var respuesta = manejador.integridad(ex);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).containsEntry("mensaje", "El texto contiene caracteres no permitidos.");
    }

    @Test
    void otroErrorDeIntegridadSigueSiendo500() {
        var ex = new DataIntegrityViolationException("insert",
                new SQLException("duplicate key value violates unique constraint", "23505"));

        assertThat(manejador.integridad(ex).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
