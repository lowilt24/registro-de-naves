package pa.amp.registro_naves.common;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * SEC-08: un texto del JSON trae el caracter nulo (U+0000). PostgreSQL no
 * lo acepta en columnas de texto, asi que se rechaza al leer el cuerpo (400)
 * en lugar de dejar que llegue a la base y responda 500.
 */
public class TextoConCaracterNuloException extends MismatchedInputException {

    public TextoConCaracterNuloException(JsonParser p) {
        super(p, "El texto contiene el caracter nulo (U+0000).", String.class);
    }
}
