package pa.amp.registro_naves.tramite;

import jakarta.persistence.*;
import pa.amp.registro_naves.usuario.Usuario;

import java.time.OffsetDateTime;

/**
 * Una entrada del historial del tramite: quien, cuando, la accion, el
 * estado anterior, el nuevo y la observacion (regla adicional de D2).
 * Solo se inserta; nunca se edita ni se borra.
 */
@Entity
@Table(name = "historial_estados")
public class HistorialEstado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tramite_id", nullable = false)
    private Tramite tramite;

    @Column(nullable = false)
    private OffsetDateTime fecha = OffsetDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccionTramite accion;

    /** Vacio solo en la entrada con que nace el tramite (P9). */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", length = 30)
    private EstadoTramite estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, length = 30)
    private EstadoTramite estadoNuevo;

    @Column(length = 1000)
    private String observacion;

    protected HistorialEstado() {
    }

    public HistorialEstado(Tramite tramite, Usuario usuario, AccionTramite accion,
                           EstadoTramite estadoAnterior, EstadoTramite estadoNuevo,
                           String observacion) {
        this.tramite = tramite;
        this.usuario = usuario;
        this.accion = accion;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.observacion = observacion;
    }

    public Long getId() { return id; }
    public Tramite getTramite() { return tramite; }
    public OffsetDateTime getFecha() { return fecha; }
    public Usuario getUsuario() { return usuario; }
    public AccionTramite getAccion() { return accion; }
    public EstadoTramite getEstadoAnterior() { return estadoAnterior; }
    public EstadoTramite getEstadoNuevo() { return estadoNuevo; }
    public String getObservacion() { return observacion; }
}
