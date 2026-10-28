package pa.amp.registro_naves.documento;

/** HU-12: lo que la DGMM decide sobre un documento. */
public enum DecisionRevision {
    APROBAR(EstadoDocumento.APROBADO),
    OBSERVAR(EstadoDocumento.OBSERVADO);

    private final EstadoDocumento resultado;

    DecisionRevision(EstadoDocumento resultado) {
        this.resultado = resultado;
    }

    /** Estado en que queda el documento. */
    public EstadoDocumento resultado() {
        return resultado;
    }
}
