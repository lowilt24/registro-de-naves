package pa.amp.registro_naves.common;

/** HU-07, criterio 3: el contenido no es un PDF. Se traduce a 415. */
public class FormatoNoSoportadoException extends RuntimeException {
    public FormatoNoSoportadoException(String mensaje) {
        super(mensaje);
    }
}
