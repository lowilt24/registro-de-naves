package pa.amp.registro_naves.tramite;

import org.springframework.stereotype.Component;
import pa.amp.registro_naves.common.AccesoDenegadoException;
import pa.amp.registro_naves.nave.Nave;
import pa.amp.registro_naves.usuario.Rol;
import pa.amp.registro_naves.usuario.Usuario;

import java.util.ArrayList;
import java.util.List;

/**
 * Quien puede hacer que sobre un tramite (D1). Se decide con tres datos:
 * el rol, la relacion con la nave y el estado actual.
 *
 *   - El agente naviero que registro la nave subsana y desiste.
 *   - La DGMM admite, observa, aprueba y rechaza, sobre cualquier tramite.
 *   - AUDITOR solo lee (P8, propuesta).
 *   - Nadie mas ve el tramite.
 *
 * "El agente residente" de D1 se lee como el usuario AGENTE_NAVIERO que
 * registro la nave (lectura (a) del plan): el agente residente de HU-06 es
 * una designacion sin cuenta y no puede iniciar sesion.
 *
 * Esta clase no usa la base ni Spring mas alla de @Component, para poder
 * probarla con objetos sueltos (AutorizacionTramiteTest). Si la PO cambia
 * el actor de una accion, el cambio va en AccionTramite#actor y aqui; la
 * maquina de EstadoTramite no se toca.
 */
@Component
public class AutorizacionTramite {

    /** Ver el estado y el historial. */
    public boolean puedeLeer(Usuario usuario, Nave nave) {
        if (usuario == null || nave == null) {
            return false;
        }
        return switch (usuario.getRol()) {
            case FUNCIONARIO_DGMM, AUDITOR -> true;
            case AGENTE_NAVIERO -> esQuienRegistro(usuario, nave);
            case ADMINISTRADOR -> false;
        };
    }

    /**
     * El usuario tiene permiso para la accion por su rol y su relacion con
     * la nave. No mira el estado: eso lo responde EstadoTramite#destino.
     */
    public boolean puedeEjecutar(Usuario usuario, Nave nave, AccionTramite accion) {
        if (usuario == null || nave == null || accion == null) {
            return false;
        }
        return switch (accion.actor()) {
            case SISTEMA -> false;
            case DGMM -> usuario.getRol() == Rol.FUNCIONARIO_DGMM;
            case AGENTE -> usuario.getRol() == Rol.AGENTE_NAVIERO && esQuienRegistro(usuario, nave);
        };
    }

    /**
     * Lo que la pantalla le ofrece a este usuario en este estado: las
     * acciones con permiso y con transicion. En orden de AccionTramite.
     */
    public List<AccionTramite> accionesPermitidas(Usuario usuario, Nave nave, EstadoTramite estado) {
        List<AccionTramite> permitidas = new ArrayList<>();
        if (estado == null) {
            return permitidas;
        }
        for (AccionTramite accion : AccionTramite.values()) {
            if (accion.esManual()
                    && estado.destino(accion).isPresent()
                    && puedeEjecutar(usuario, nave, accion)) {
                permitidas.add(accion);
            }
        }
        return permitidas;
    }

    /**
     * HU-11 (D1 del Sprint 6): la bandeja de todos los tramites la ve la
     * DGMM, que decide, y el auditor, que solo lee. El agente no: el ve sus
     * naves en HU-10.
     */
    public boolean puedeVerBandeja(Usuario usuario) {
        return usuario != null
                && (usuario.getRol() == Rol.FUNCIONARIO_DGMM || usuario.getRol() == Rol.AUDITOR);
    }

    /** 403 si no puede ver la bandeja. */
    public void exigirBandeja(Usuario usuario) {
        if (!puedeVerBandeja(usuario)) {
            throw new AccesoDenegadoException(
                    "La bandeja de trámites es de la DGMM. Sus naves están en «Mis naves».");
        }
    }

    /** 403 si no puede ver el tramite. */
    public void exigirLectura(Usuario usuario, Nave nave) {
        if (!puedeLeer(usuario, nave)) {
            throw new AccesoDenegadoException("La nave indicada no le pertenece.");
        }
    }

    /**
     * 403 si el rol o la relacion con la nave no le dan la accion (P2,
     * propuesta). Va antes que el 409 de una transicion invalida: a quien
     * no le corresponde la accion no se le cuenta en que estado esta.
     */
    public void exigirAccion(Usuario usuario, Nave nave, AccionTramite accion) {
        if (puedeEjecutar(usuario, nave, accion)) {
            return;
        }
        if (!puedeLeer(usuario, nave)) {
            throw new AccesoDenegadoException("La nave indicada no le pertenece.");
        }
        throw new AccesoDenegadoException(switch (accion.actor()) {
            case DGMM -> "Solo un funcionario de la DGMM puede " + frase(accion) + ".";
            case AGENTE -> "Solo el agente que registró la nave puede " + frase(accion) + ".";
            case SISTEMA -> "Esta acción no se puede ejecutar a mano.";
        });
    }

    private static boolean esQuienRegistro(Usuario usuario, Nave nave) {
        Usuario dueno = nave.getRegistradoPor();
        return dueno != null
                && dueno.getCorreo() != null
                && dueno.getCorreo().equalsIgnoreCase(usuario.getCorreo());
    }

    static String frase(AccionTramite accion) {
        return switch (accion) {
            case ADMITIR -> "admitir el trámite";
            case OBSERVAR -> "observar el trámite";
            case APROBAR -> "aprobar el trámite";
            case RECHAZAR -> "rechazar el trámite";
            case SUBSANAR -> "subsanar el trámite";
            case DESISTIR -> "desistir del trámite";
            case PRESENTAR -> "presentar el trámite";
            case MIGRACION -> "migrar el trámite";
        };
    }
}
