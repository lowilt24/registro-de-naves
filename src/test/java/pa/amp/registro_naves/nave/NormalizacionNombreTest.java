package pa.amp.registro_naves.nave;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NormalizacionNombreTest {

    @Test
    void colapsaEspaciosYPasaAMayusculas() {
        assertEquals("ESTRELLA DEL ISTMO", Nave.normalizar("  estrella   del  istmo "));
    }

    @Test
    void nombreNuloSeVuelveCadenaVacia() {
        assertEquals("", Nave.normalizar(null));
    }

    @Test
    void dosEscriturasDelMismoNombreColisionan() {
        assertEquals(Nave.normalizar("Mar Caribe"), Nave.normalizar("MAR   caribe"));
    }
}
