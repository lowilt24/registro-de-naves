package pa.amp.registro_naves.nave;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pa.amp.registro_naves.common.AccesoDenegadoException;
import pa.amp.registro_naves.common.RecursoNoEncontradoException;

/**
 * Punto unico donde se decide si un usuario puede operar sobre una nave.
 *
 * HU-05 exige que un agente solo pueda vincular propietarios a naves que
 * el mismo registro. Tener la comprobacion en un solo lugar evita que un
 * endpoint nuevo se olvide de hacerla, que es la forma habitual en que
 * aparecen los fallos de control de acceso.
 */
@Component
public class AccesoNave {

    private final NaveRepository naves;

    public AccesoNave(NaveRepository naves) {
        this.naves = naves;
    }

    /**
     * Devuelve la nave si existe y pertenece al usuario.
     *
     * Si no existe responde 404, como pide HU-05. Si existe pero es de
     * otro usuario responde 403.
     *
     * Nota de seguridad: responder 403 le confirma a quien sondea que esa
     * nave existe. Una version endurecida responderia 404 en ambos casos.
     * Se deja distinguible a proposito en este sprint para que la prueba
     * de Wfuzz sobre manipulacion de ID pueda diferenciar los dos casos.
     */
    @Transactional(readOnly = true)
    public Nave exigirPropia(Long naveId, String correoUsuario) {
        Nave nave = naves.findById(naveId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La nave indicada no existe."));

        if (nave.getRegistradoPor() == null
                || !nave.getRegistradoPor().getCorreo().equalsIgnoreCase(correoUsuario)) {
            throw new AccesoDenegadoException("La nave indicada no le pertenece.");
        }
        return nave;
    }
}
