package pa.amp.registro_naves.persona;

import jakarta.persistence.*;
import pa.amp.registro_naves.nave.Nave;
import pa.amp.registro_naves.usuario.Usuario;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * HU-06 — designacion del agente residente de una nave.
 *
 * Cada fila es una designacion, no un agente de catalogo. Una nave tiene
 * una sola designacion vigente; las anteriores quedan con vigente = false
 * para conservar el historial del expediente.
 */
@Entity
@Table(name = "agentes_residentes")
public class AgenteResidente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nave_id", nullable = false)
    private Nave nave;

    /** Nombre del abogado o de la firma. */
    @Column(nullable = false, length = 150)
    private String nombre;

    /** Numero de idoneidad. Solo aplica a un abogado individual. */
    @Column(length = 50)
    private String idoneidad;

    @Column(length = 40)
    private String telefono;

    @Column(length = 120)
    private String correo;

    // --- Referencia del poder. El archivo se adjunta en HU-07. ---

    @Column(name = "poder_numero", length = 60)
    private String poderNumero;

    @Column(name = "poder_fecha")
    private LocalDate poderFecha;

    /** Notaria o consulado donde se otorgo. */
    @Column(name = "poder_lugar", length = 150)
    private String poderLugar;

    @Column(nullable = false)
    private boolean vigente = true;

    @Column(name = "fecha_designacion", nullable = false)
    private LocalDateTime fechaDesignacion = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "designado_por_id")
    private Usuario designadoPor;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Nave getNave() { return nave; }
    public void setNave(Nave nave) { this.nave = nave; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getIdoneidad() { return idoneidad; }
    public void setIdoneidad(String idoneidad) { this.idoneidad = idoneidad; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getPoderNumero() { return poderNumero; }
    public void setPoderNumero(String poderNumero) { this.poderNumero = poderNumero; }

    public LocalDate getPoderFecha() { return poderFecha; }
    public void setPoderFecha(LocalDate poderFecha) { this.poderFecha = poderFecha; }

    public String getPoderLugar() { return poderLugar; }
    public void setPoderLugar(String poderLugar) { this.poderLugar = poderLugar; }

    public boolean isVigente() { return vigente; }
    public void setVigente(boolean vigente) { this.vigente = vigente; }

    public LocalDateTime getFechaDesignacion() { return fechaDesignacion; }
    public void setFechaDesignacion(LocalDateTime fechaDesignacion) { this.fechaDesignacion = fechaDesignacion; }

    public Usuario getDesignadoPor() { return designadoPor; }
    public void setDesignadoPor(Usuario designadoPor) { this.designadoPor = designadoPor; }
}
