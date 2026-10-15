package pa.amp.registro_naves.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pa.amp.registro_naves.common.TextoConCaracterNuloException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.deser.jdk.StringDeserializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * SEC-08 (Wfuzz, Sprint 5): ningun texto que llegue por JSON puede traer el
 * caracter nulo. Un solo lugar para todos los DTO: el lector de cadenas de
 * Jackson. El manejador global lo convierte en 400.
 */
@Configuration
public class TextoSinNulosConfig {

    @Bean
    public JacksonModule textoSinNulos() {
        SimpleModule modulo = new SimpleModule("texto-sin-nulos");
        modulo.addDeserializer(String.class, new TextoSinNulos());
        return modulo;
    }

    static class TextoSinNulos extends StringDeserializer {

        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) {
            String texto = super.deserialize(p, ctxt);
            if (texto != null && texto.indexOf('\u0000') >= 0) {
                throw new TextoConCaracterNuloException(p);
            }
            return texto;
        }
    }
}
