package pa.amp.registro_naves.common;

/** El usuario esta autenticado pero el recurso no le pertenece. Se traduce a 403. */
public class AccesoDenegadoException extends RuntimeException {
    public AccesoDenegadoException(String mensaje) {
        super(mensaje);
    }
}
