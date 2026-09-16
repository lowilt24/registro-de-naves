package pa.amp.registro_naves.persona;

import jakarta.persistence.*;

@Entity
@Table(name = "agente_residente")
public class AgenteResidente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, unique = true, length = 50)
    private String idoneidad;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getIdoneidad() { return idoneidad; }
    public void setIdoneidad(String idoneidad) { this.idoneidad = idoneidad; }
}
