package pa.amp.registro_naves.constancia;

import jakarta.persistence.*;
import pa.amp.registro_naves.nave.Nave;
import pa.amp.registro_naves.tramite.HistorialEstado;
import java.time.OffsetDateTime;

/** PDF conservado con su hash y evidencia de aprobacion; sin setters. */
@Entity
@Table(name = "constancias_registro")
public class ConstanciaRegistro {
    @Id @Column(name = "nave_id")
    private Long naveId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aprobacion_id", nullable = false, updatable = false)
    private HistorialEstado aprobacion;
    // Sin @Lob: PostgreSQL BYTEA, no un OID externo.
    @Column(nullable = false, updatable = false, columnDefinition = "bytea")
    private byte[] contenido;
    @Column(name = "hash_sha256", nullable = false, updatable = false, length = 64)
    private String hashSha256;
    @Column(name = "generado_en", nullable = false, updatable = false)
    private OffsetDateTime generadoEn;

    protected ConstanciaRegistro() {}
    public ConstanciaRegistro(Nave nave, HistorialEstado aprobacion, byte[] contenido, String hash) {
        this.naveId = nave.getId();
        this.aprobacion = aprobacion;
        this.contenido = contenido.clone();
        this.hashSha256 = hash;
        this.generadoEn = OffsetDateTime.now();
    }
    public Long getNaveId() { return naveId; }
    public byte[] getContenido() { return contenido.clone(); }
    public String getHashSha256() { return hashSha256; }
    public OffsetDateTime getGeneradoEn() { return generadoEn; }
    public HistorialEstado getAprobacion() { return aprobacion; }
}
