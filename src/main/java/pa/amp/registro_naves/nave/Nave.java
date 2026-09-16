package pa.amp.registro_naves.nave;

import jakarta.persistence.*;
import pa.amp.registro_naves.persona.AgenteResidente;
import pa.amp.registro_naves.persona.Propietario;
import pa.amp.registro_naves.usuario.Usuario;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "nave")
public class Nave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    /** Clave real de unicidad usada por HU-04. La calcula Nave#normalizar. */
    @Column(name = "nombre_normalizado", nullable = false, unique = true, length = 120)
    private String nombreNormalizado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoNave tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoServicio servicio;

    @Column(name = "tonelaje_bruto", nullable = false, precision = 12, scale = 2)
    private BigDecimal tonelajeBruto;

    @Column(name = "tonelaje_neto", nullable = false, precision = 12, scale = 2)
    private BigDecimal tonelajeNeto;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal eslora;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal manga;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal puntal;

    @Column(name = "anio_construccion", nullable = false)
    private Integer anioConstruccion;

    @Column(name = "lugar_construccion", nullable = false, length = 120)
    private String lugarConstruccion;

    @Column(name = "material_casco", nullable = false, length = 60)
    private String materialCasco;

    @Column(name = "tipo_propulsion", nullable = false, length = 60)
    private String tipoPropulsion;

    @Column(name = "potencia_kw", nullable = false, precision = 10, scale = 2)
    private BigDecimal potenciaKw;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoNave estado = EstadoNave.REGISTRADA;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "propietario_id", nullable = false)
    private Propietario propietario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agente_residente_id", nullable = false)
    private AgenteResidente agenteResidente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por_id")
    private Usuario registradoPor;

    /**
     * Regla unica de comparacion de nombres para HU-04: sin espacios al borde,
     * espacios internos colapsados y todo en mayusculas. Asi "estrella  del
     * istmo" y "Estrella Del Istmo" cuentan como el mismo nombre.
     */
    public static String normalizar(String nombre) {
        if (nombre == null) {
            return "";
        }
        return nombre.trim().replaceAll("\\s+", " ").toUpperCase();
    }

    public void setNombre(String nombre) {
        this.nombre = nombre == null ? null : nombre.trim().replaceAll("\\s+", " ");
        this.nombreNormalizado = normalizar(nombre);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }

    public String getNombreNormalizado() { return nombreNormalizado; }

    public TipoNave getTipo() { return tipo; }
    public void setTipo(TipoNave tipo) { this.tipo = tipo; }

    public TipoServicio getServicio() { return servicio; }
    public void setServicio(TipoServicio servicio) { this.servicio = servicio; }

    public BigDecimal getTonelajeBruto() { return tonelajeBruto; }
    public void setTonelajeBruto(BigDecimal tonelajeBruto) { this.tonelajeBruto = tonelajeBruto; }

    public BigDecimal getTonelajeNeto() { return tonelajeNeto; }
    public void setTonelajeNeto(BigDecimal tonelajeNeto) { this.tonelajeNeto = tonelajeNeto; }

    public BigDecimal getEslora() { return eslora; }
    public void setEslora(BigDecimal eslora) { this.eslora = eslora; }

    public BigDecimal getManga() { return manga; }
    public void setManga(BigDecimal manga) { this.manga = manga; }

    public BigDecimal getPuntal() { return puntal; }
    public void setPuntal(BigDecimal puntal) { this.puntal = puntal; }

    public Integer getAnioConstruccion() { return anioConstruccion; }
    public void setAnioConstruccion(Integer anioConstruccion) { this.anioConstruccion = anioConstruccion; }

    public String getLugarConstruccion() { return lugarConstruccion; }
    public void setLugarConstruccion(String lugarConstruccion) { this.lugarConstruccion = lugarConstruccion; }

    public String getMaterialCasco() { return materialCasco; }
    public void setMaterialCasco(String materialCasco) { this.materialCasco = materialCasco; }

    public String getTipoPropulsion() { return tipoPropulsion; }
    public void setTipoPropulsion(String tipoPropulsion) { this.tipoPropulsion = tipoPropulsion; }

    public BigDecimal getPotenciaKw() { return potenciaKw; }
    public void setPotenciaKw(BigDecimal potenciaKw) { this.potenciaKw = potenciaKw; }

    public EstadoNave getEstado() { return estado; }
    public void setEstado(EstadoNave estado) { this.estado = estado; }

    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }

    public Propietario getPropietario() { return propietario; }
    public void setPropietario(Propietario propietario) { this.propietario = propietario; }

    public AgenteResidente getAgenteResidente() { return agenteResidente; }
    public void setAgenteResidente(AgenteResidente agenteResidente) { this.agenteResidente = agenteResidente; }

    public Usuario getRegistradoPor() { return registradoPor; }
    public void setRegistradoPor(Usuario registradoPor) { this.registradoPor = registradoPor; }
}
