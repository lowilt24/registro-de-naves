package pa.amp.registro_naves.tramite;

import pa.amp.registro_naves.documento.TipoDocumento;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 409 (PO, 13-10-2026): el tramite no pasa a "En revision" mientras falten
 * documentos obligatorios. Ademas del mensaje lleva la lista, para que la
 * pantalla no tenga que leer el texto; siempre en el orden oficial
 * (TipoDocumento.OBLIGATORIOS), asi las pruebas son deterministas.
 */
public class DocumentosFaltantesException extends RuntimeException {

    private final List<TipoDocumento> faltantes;

    public DocumentosFaltantesException(List<TipoDocumento> faltantes) {
        super("Faltan documentos obligatorios: "
                + faltantes.stream().map(Enum::name).collect(Collectors.joining(", ")));
        this.faltantes = List.copyOf(faltantes);
    }

    public List<TipoDocumento> getFaltantes() {
        return faltantes;
    }
}
