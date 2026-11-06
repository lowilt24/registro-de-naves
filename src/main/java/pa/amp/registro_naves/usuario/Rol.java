package pa.amp.registro_naves.usuario;

/**
 * Rol del usuario. La base lo acota con ck_usuario_rol (V5).
 *
 * Sprint 7: solo AGENTE_NAVIERO se puede elegir
 * en el registro publico.
 */
public enum Rol {
    AGENTE_NAVIERO,
    FUNCIONARIO_DGMM,
    AUDITOR,
    ADMINISTRADOR;

    /** Roles permitidos en el registro publico. */
    public boolean esAutoasignable() {
        return this == AGENTE_NAVIERO;
    }
}