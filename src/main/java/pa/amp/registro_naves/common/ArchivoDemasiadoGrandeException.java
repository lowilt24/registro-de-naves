package pa.amp.registro_naves.common;

/** HU-07, criterio 4. Se traduce a 413. */
public class ArchivoDemasiadoGrandeException extends RuntimeException {
    public ArchivoDemasiadoGrandeException(String mensaje) {
        super(mensaje);
    }
}
