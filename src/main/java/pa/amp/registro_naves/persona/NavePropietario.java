package pa.amp.registro_naves.persona;

import jakarta.persistence.*;
import pa.amp.registro_naves.nave.Nave;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * HU-05 — vinculo entre una nave y uno de sus propietarios, con la cuota
 * de participacion. La suma de cuotas de una nave no puede pasar de 100;
 * esa regla la aplica PropietarioService.
 */
@Entity
@Table(name = "nave_propietario")
public class NavePropietario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nave_id", nullable = false)
    private Nave nave;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "propietario_id", nullable = false)
    private Propietario propietario;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentaje = new BigDecimal("100.00");

    @Column(name = "fecha_vinculacion", nullable = false)
    private LocalDateTime fechaVinculacion = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Nave getNave() { return nave; }
    public void setNave(Nave nave) { this.nave = nave; }

    public Propietario getPropietario() { return propietario; }
    public void setPropietario(Propietario propietario) { this.propietario = propietario; }

    public BigDecimal getPorcentaje() { return porcentaje; }
    public void setPorcentaje(BigDecimal porcentaje) { this.porcentaje = porcentaje; }

    public LocalDateTime getFechaVinculacion() { return fechaVinculacion; }
    public void setFechaVinculacion(LocalDateTime fechaVinculacion) { this.fechaVinculacion = fechaVinculacion; }
}
