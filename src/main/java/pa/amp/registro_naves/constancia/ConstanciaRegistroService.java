package pa.amp.registro_naves.constancia;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pa.amp.registro_naves.common.*;
import pa.amp.registro_naves.nave.*;
import pa.amp.registro_naves.persona.*;
import pa.amp.registro_naves.tramite.*;
import pa.amp.registro_naves.usuario.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class ConstanciaRegistroService {
    private final NaveRepository naves;
    private final UsuarioRepository usuarios;
    private final TramiteRepository tramites;
    private final HistorialEstadoRepository historial;
    private final NavePropietarioRepository propietarios;
    private final AgenteResidenteRepository agentes;
    private final ConstanciaRegistroRepository constancias;
    private final ConstanciaPdf pdf;

    public ConstanciaRegistroService(NaveRepository naves, UsuarioRepository usuarios,
            TramiteRepository tramites, HistorialEstadoRepository historial,
            NavePropietarioRepository propietarios, AgenteResidenteRepository agentes,
            ConstanciaRegistroRepository constancias, ConstanciaPdf pdf) {
        this.naves = naves; this.usuarios = usuarios; this.tramites = tramites;
        this.historial = historial; this.propietarios = propietarios; this.agentes = agentes;
        this.constancias = constancias; this.pdf = pdf;
    }
    public record Descarga(byte[] contenido, String nombre, String hashSha256) {}

    @Transactional
    public Descarga descargar(Long naveId, String correo) {
        Nave nave = naves.findById(naveId).orElseThrow(() ->
                new RecursoNoEncontradoException("La nave indicada no existe."));
        Usuario usuario = usuarios.findByCorreoIgnoreCase(correo).orElseThrow(() ->
                new AccesoDenegadoException("No tiene permiso para descargar esta constancia."));
        boolean institucional = usuario.getRol() == Rol.FUNCIONARIO_DGMM || usuario.getRol() == Rol.AUDITOR;
        boolean propio = usuario.getRol() == Rol.AGENTE_NAVIERO && nave.getRegistradoPor() != null
                && nave.getRegistradoPor().getId().equals(usuario.getId());
        if (!institucional && !propio) {
            throw new AccesoDenegadoException("No tiene permiso para descargar esta constancia.");
        }
        // Primero permisos, despues estado: un agente ajeno siempre recibe 403.
        Tramite tramite = tramites.bloquearParaConstancia(naveId).orElseThrow(() ->
                new ReglaDeNegocioException("La nave no tiene un trámite aprobado."));
        if (tramite.getEstado() != EstadoTramite.APROBADA) {
            throw new ReglaDeNegocioException("La constancia solo está disponible con el trámite en Aprobada.");
        }
        var constancia = constancias.findById(naveId).orElseGet(() -> emitir(nave, tramite));
        byte[] contenido = constancia.getContenido();
        if (!hash(contenido).equals(constancia.getHashSha256())) {
            throw new ReglaDeNegocioException("No se pudo verificar la integridad de la constancia.");
        }
        return new Descarga(contenido, "constancia-registro-" + naveId + ".pdf", constancia.getHashSha256());
    }
    private ConstanciaRegistro emitir(Nave nave, Tramite tramite) {
        var aprobacion = historial.lineaDeTiempo(tramite.getId()).stream()
                .filter(h -> h.getAccion() == AccionTramite.APROBAR && h.getEstadoNuevo() == EstadoTramite.APROBADA)
                .reduce((primera, ultima) -> ultima).orElseThrow(() -> new ReglaDeNegocioException(
                        "No existe evidencia de fecha y funcionario de aprobación para emitir la constancia."));
        var duenos = propietarios.findByNaveIdOrderByIdAsc(nave.getId());
        var agente = agentes.findByNaveIdAndVigenteTrue(nave.getId()).orElseThrow(() ->
                new ReglaDeNegocioException("Falta el agente residente en el expediente aprobado."));
        if (duenos.isEmpty()) {
            throw new ReglaDeNegocioException("Faltan los propietarios en el expediente aprobado.");
        }
        byte[] contenido = pdf.generar(nave, duenos, agente, aprobacion);
        return constancias.saveAndFlush(new ConstanciaRegistro(nave, aprobacion, contenido, hash(contenido)));
    }
    static String hash(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
