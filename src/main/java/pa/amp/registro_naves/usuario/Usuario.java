package pa.amp.registro_naves.usuario;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String correo;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Rol rol;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    // --- Sprint 4: validacion del correo (observacion de la profesora) ---

    /** La cuenta no inicia sesion hasta que esto sea true. */
    @Column(name = "correo_validado", nullable = false)
    private boolean correoValidado = false;

    /** Hash SHA-256 del token del enlace, nunca el token en claro. */
    @Column(name = "token_validacion", length = 64, unique = true)
    private String tokenValidacion;

    @Column(name = "token_expira_en")
    private OffsetDateTime tokenExpiraEn;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }

    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public boolean isCorreoValidado() { return correoValidado; }
    public void setCorreoValidado(boolean correoValidado) { this.correoValidado = correoValidado; }

    public String getTokenValidacion() { return tokenValidacion; }
    public void setTokenValidacion(String tokenValidacion) { this.tokenValidacion = tokenValidacion; }

    public OffsetDateTime getTokenExpiraEn() { return tokenExpiraEn; }
    public void setTokenExpiraEn(OffsetDateTime tokenExpiraEn) { this.tokenExpiraEn = tokenExpiraEn; }
}
