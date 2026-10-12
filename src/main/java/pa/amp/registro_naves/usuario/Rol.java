package pa.amp.registro_naves.usuario;

/**
 * Rol del usuario. La base lo acota con ck_usuario_rol (V5).
 *
 * Sprint 4: se agrega AUDITOR, que pidio la profesora. Solo los dos
 * primeros se pueden elegir al crear una cuenta; ver UsuarioService.
 */
public enum Rol {
    AGENTE_NAVIERO,
    FUNCIONARIO_DGMM,
    AUDITOR,
    ADMINISTRADOR;

    /** Roles que alguien puede asignarse a si mismo en el formulario de registro. */
    public boolean esAutoasignable() {
        return this == AGENTE_NAVIERO || this == FUNCIONARIO_DGMM;
    }
}
