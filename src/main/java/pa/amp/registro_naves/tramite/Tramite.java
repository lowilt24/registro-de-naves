package pa.amp.registro_naves.tramite;

import jakarta.persistence.*;
import pa.amp.registro_naves.nave.Nave;

import java.time.OffsetDateTime;

/**
 * Tramite de registro de una nave (HU-09). Uno por nave (D3).
 *
 * D4: esta tabla es la unica fuente oficial del estado del tramite.
 * nave.estado conserva su propio significado y nada la copia aqui ni al
 * reves.
 */
@Entity
@Table(name = "tramites")
public class Tramite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nave_id", nullable = false, unique = true)
    private Nave nave;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoTramite estado = EstadoTramite.INICIAL;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @Column(name = "actualizado_en", nullable = false)
    private OffsetDateTime actualizadoEn = OffsetDateTime.now();

    /**
     * Bloqueo optimista. Si dos funcionarios cambian el mismo tramite a la
     * vez, el segundo recibe 409 y recarga, en lugar de pisar al primero.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    protected Tramite() {
    }

    public Tramite(Nave nave) {
        this.nave = nave;
    }

    /** El servicio ya valido la transicion; aqui solo se registra. */
    void cambiarA(EstadoTramite nuevo) {
        this.estado = nuevo;
        this.actualizadoEn = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Nave getNave() { return nave; }
    public EstadoTramite getEstado() { return estado; }
    public OffsetDateTime getCreadoEn() { return creadoEn; }
    public OffsetDateTime getActualizadoEn() { return actualizadoEn; }
    public Long getVersion() { return version; }
}
