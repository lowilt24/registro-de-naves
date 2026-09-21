package pa.amp.registro_naves.persona;

import jakarta.persistence.*;
import pa.amp.registro_naves.usuario.Usuario;

import java.time.LocalDateTime;

/** HU-05 — propietario de una nave, persona natural o juridica. */
@Entity
@Table(name = "propietarios")
public class Propietario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nombre de la persona natural o razon social de la sociedad. */
    @Column(nullable = false, length = 150)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoPersona tipo;

    @Column(nullable = false, length = 80)
    private String nacionalidad;

    @Column(nullable = false, length = 250)
    private String domicilio;

    /** Obligatorio solo cuando el tipo es JURIDICA. */
    @Column(name = "pais_constitucion", length = 80)
    private String paisConstitucion;

    /** Cedula, pasaporte o RUC. Opcional: HU-05 no lo exige. */
    @Column(unique = true, length = 50)
    private String identificacion;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por_id")
    private Usuario registradoPor;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public TipoPersona getTipo() { return tipo; }
    public void setTipo(TipoPersona tipo) { this.tipo = tipo; }

    public String getNacionalidad() { return nacionalidad; }
    public void setNacionalidad(String nacionalidad) { this.nacionalidad = nacionalidad; }

    public String getDomicilio() { return domicilio; }
    public void setDomicilio(String domicilio) { this.domicilio = domicilio; }

    public String getPaisConstitucion() { return paisConstitucion; }
    public void setPaisConstitucion(String paisConstitucion) { this.paisConstitucion = paisConstitucion; }

    public String getIdentificacion() { return identificacion; }
    public void setIdentificacion(String identificacion) { this.identificacion = identificacion; }

    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }

    public Usuario getRegistradoPor() { return registradoPor; }
    public void setRegistradoPor(Usuario registradoPor) { this.registradoPor = registradoPor; }
}
