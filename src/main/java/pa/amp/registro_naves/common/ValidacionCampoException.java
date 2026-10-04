package pa.amp.registro_naves.common;

/**
 * Campo invalido detectado en el servicio y no por las anotaciones, porque
 * depende de otro campo. Se traduce a 400 con la misma forma de respuesta
 * que la validacion automatica, para que la pantalla no tenga que
 * distinguir entre las dos.
 */
public class ValidacionCampoException extends RuntimeException {

    private final String campo;

    public ValidacionCampoException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
