package pa.amp.registro_naves.documento;

import jakarta.persistence.*;
import pa.amp.registro_naves.nave.Nave;
import pa.amp.registro_naves.usuario.Usuario;

import java.time.OffsetDateTime;

/** HU-07 — una version de un documento del expediente de la nave. */
@Entity
@Table(name = "documentos")
public class Documento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nave_id", nullable = false)
    private Nave nave;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoDocumento tipo;

    /** Nombre que mando el cliente, ya limpio. Solo para mostrar. */
    @Column(name = "nombre_archivo", nullable = false, length = 255)
    private String nombreArchivo;

    /** Ruta relativa dentro del almacen, generada por el servidor. Nunca se expone. */
    @Column(nullable = false, unique = true, length = 255)
    private String ruta;

    @Column(name = "hash_sha256", nullable = false, length = 64)
    private String hashSha256;

    @Column(name = "tamano_bytes", nullable = false)
    private Long tamanoBytes;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false)
    private boolean vigente = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoDocumento estado = EstadoDocumento.PENDIENTE;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creado_por", nullable = false)
    private Usuario creadoPor;

    // --- Sprint 6 (HU-12, V7): revision de la DGMM ---

    /** Obligatoria al observar; nota opcional al aprobar. */
    @Column(length = 1000)
    private String observacion;

    /** Funcionario que reviso. Nulo mientras esta PENDIENTE (ck_documentos_revision). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revisado_por")
    private Usuario revisadoPor;

    @Column(name = "revisado_en")
    private OffsetDateTime revisadoEn;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Nave getNave() { return nave; }
    public void setNave(Nave nave) { this.nave = nave; }

    public TipoDocumento getTipo() { return tipo; }
    public void setTipo(TipoDocumento tipo) { this.tipo = tipo; }

    public String getNombreArchivo() { return nombreArchivo; }
    public void setNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }

    public String getRuta() { return ruta; }
    public void setRuta(String ruta) { this.ruta = ruta; }

    public String getHashSha256() { return hashSha256; }
    public void setHashSha256(String hashSha256) { this.hashSha256 = hashSha256; }

    public Long getTamanoBytes() { return tamanoBytes; }
    public void setTamanoBytes(Long tamanoBytes) { this.tamanoBytes = tamanoBytes; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public boolean isVigente() { return vigente; }
    public void setVigente(boolean vigente) { this.vigente = vigente; }

    public EstadoDocumento getEstado() { return estado; }
    public void setEstado(EstadoDocumento estado) { this.estado = estado; }

    public OffsetDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(OffsetDateTime creadoEn) { this.creadoEn = creadoEn; }

    public Usuario getCreadoPor() { return creadoPor; }
    public void setCreadoPor(Usuario creadoPor) { this.creadoPor = creadoPor; }

    public String getObservacion() { return observacion; }
    public Usuario getRevisadoPor() { return revisadoPor; }
    public OffsetDateTime getRevisadoEn() { return revisadoEn; }

    /**
     * Solo para pruebas y datos de demo: en la aplicacion la revision se
     * escribe con DocumentoRepository#revisar, un UPDATE condicional que
     * evita que dos funcionarios se pisen (observacion 4 de QA).
     */
    public void marcarRevisado(EstadoDocumento estado, String observacion, Usuario revisor,
                               OffsetDateTime cuando) {
        this.estado = estado;
        this.observacion = observacion;
        this.revisadoPor = revisor;
        this.revisadoEn = cuando;
    }
}
