package pa.amp.registro_naves.common;

/** Error esperado de negocio: se traduce a HTTP 409 con un mensaje para el usuario. */
public class ReglaDeNegocioException extends RuntimeException {
    public ReglaDeNegocioException(String mensaje) {
        super(mensaje);
    }
}
