package pa.amp.registro_naves.tramite;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pa.amp.registro_naves.common.RecursoNoEncontradoException;
import pa.amp.registro_naves.common.ReglaDeNegocioException;
import pa.amp.registro_naves.common.ValidacionCampoException;
import pa.amp.registro_naves.documento.Documento;
import pa.amp.registro_naves.documento.DocumentoRepository;
import pa.amp.registro_naves.documento.EstadoDocumento;
import pa.amp.registro_naves.documento.TipoDocumento;
import pa.amp.registro_naves.nave.Nave;
import pa.amp.registro_naves.nave.NaveRepository;
import pa.amp.registro_naves.tramite.TramiteDtos.CambioEstadoRequest;
import pa.amp.registro_naves.tramite.TramiteDtos.ConteoPorEstadoResponse;
import pa.amp.registro_naves.tramite.TramiteDtos.HistorialResponse;
import pa.amp.registro_naves.tramite.TramiteDtos.TramiteResponse;
import pa.amp.registro_naves.usuario.Usuario;
import pa.amp.registro_naves.usuario.UsuarioRepository;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * HU-09: estado del tramite. HU-10: conteo de las naves por estado.
 *
 * Orden de las respuestas de error (P2, propuesta):
 *   401 sin sesion (SecurityConfig)
 *   404 la nave no existe
 *   403 el rol o la relacion con la nave no dan la accion
 *   409 la transicion no existe desde el estado actual
 *   409 faltan documentos obligatorios para pasar a "En revision" (PO)
 *   400 falta la observacion obligatoria (P5)
 * Un cuerpo mal formado (accion vacia o inventada) es 400 antes de todo,
 * porque falla al leerlo.
 */
@Service
public class TramiteService {

    private final TramiteRepository tramites;
    private final HistorialEstadoRepository historial;
    private final NaveRepository naves;
    private final UsuarioRepository usuarios;
    private final AutorizacionTramite autorizacion;
    private final DocumentoRepository documentos;

    /** PO: un documento vigente cuenta si esta pendiente de revision o aprobado. */
    private static final Set<EstadoDocumento> DOCUMENTO_PRESENTE =
            EnumSet.of(EstadoDocumento.PENDIENTE, EstadoDocumento.APROBADO);

    public TramiteService(TramiteRepository tramites,
                          HistorialEstadoRepository historial,
                          NaveRepository naves,
                          UsuarioRepository usuarios,
                          AutorizacionTramite autorizacion,
                          DocumentoRepository documentos) {
        this.tramites = tramites;
        this.historial = historial;
        this.naves = naves;
        this.usuarios = usuarios;
        this.autorizacion = autorizacion;
        this.documentos = documentos;
    }

    /**
     * D3: HU-03 lo llama al registrar la nave, dentro de la misma
     * transaccion. Si la nave ya tiene tramite no crea otro.
     */
    @Transactional
    public Tramite crearPara(Nave nave, Usuario quienRegistra) {
        return tramites.findByNaveId(nave.getId()).orElseGet(() -> {
            Tramite tramite = tramites.save(new Tramite(nave));
            historial.save(new HistorialEstado(tramite, quienRegistra, AccionTramite.PRESENTAR,
                    null, tramite.getEstado(), null));
            return tramite;
        });
    }

    /** GET /api/naves/{id}/tramite */
    @Transactional(readOnly = true)
    public TramiteResponse consultar(Long naveId, String correo) {
        Usuario usuario = usuario(correo);
        Nave nave = nave(naveId);
        autorizacion.exigirLectura(usuario, nave);
        Tramite tramite = tramiteDe(nave);
        return TramiteResponse.de(tramite,
                autorizacion.accionesPermitidas(usuario, nave, tramite.getEstado()));
    }

    /** POST /api/naves/{id}/tramite/estado */
    @Transactional
    public TramiteResponse cambiarEstado(Long naveId, CambioEstadoRequest peticion, String correo) {
        AccionTramite accion = peticion.accion();
        if (!accion.esManual()) {
            throw new ValidacionCampoException("accion", "Esta acción no se puede ejecutar a mano.");
        }

        Usuario usuario = usuario(correo);
        Nave nave = nave(naveId);                                // 404
        autorizacion.exigirAccion(usuario, nave, accion);         // 403
        Tramite tramite = tramiteDe(nave);

        EstadoTramite anterior = tramite.getEstado();
        EstadoTramite nuevo = anterior.destino(accion)           // 409
                .orElseThrow(() -> new ReglaDeNegocioException(
                        anterior.esFinal()
                                ? "El trámite ya terminó (" + nombre(anterior) + "); no admite más cambios."
                                : "No se puede " + AutorizacionTramite.frase(accion)
                                  + " cuando está en " + nombre(anterior) + "."));

        // PO: admitir y subsanar llevan a "En revision"; las dos pasan por
        // la misma regla de documentos obligatorios.
        if (nuevo == EstadoTramite.EN_REVISION) {
            exigirDocumentosObligatorios(nave.getId());           // 409
        }

        String observacion = limpiar(peticion.observacion());
        if (accion.exigeObservacion() && observacion == null) {  // 400
            throw new ValidacionCampoException("observacion",
                    "Escriba la observación: es obligatoria al observar o rechazar.");
        }

        tramite.cambiarA(nuevo);
        // El flush hace saltar aqui el bloqueo optimista si otro usuario
        // cambio el tramite entre la lectura y la escritura (-> 409).
        tramites.saveAndFlush(tramite);
        historial.save(new HistorialEstado(tramite, usuario, accion, anterior, nuevo, observacion));

        return TramiteResponse.de(tramite,
                autorizacion.accionesPermitidas(usuario, nave, nuevo));
    }

    /** GET /api/naves/{id}/tramite/historial */
    @Transactional(readOnly = true)
    public List<HistorialResponse> historial(Long naveId, String correo) {
        Usuario usuario = usuario(correo);
        Nave nave = nave(naveId);
        autorizacion.exigirLectura(usuario, nave);
        Tramite tramite = tramiteDe(nave);
        return historial.lineaDeTiempo(tramite.getId()).stream()
                .map(HistorialResponse::de)
                .toList();
    }

    /** HU-10: los seis estados, con cero los que no tienen naves del agente. */
    @Transactional(readOnly = true)
    public ConteoPorEstadoResponse contarPorEstado(String correo) {
        Map<EstadoTramite, Long> conteo = new EnumMap<>(EstadoTramite.class);
        for (EstadoTramite estado : EstadoTramite.values()) {
            conteo.put(estado, 0L);
        }
        long total = 0;
        for (Object[] fila : tramites.contarPorEstado(correo)) {
            long cantidad = ((Number) fila[1]).longValue();
            conteo.put((EstadoTramite) fila[0], cantidad);
            total += cantidad;
        }
        return new ConteoPorEstadoResponse(conteo, total);
    }

    /** Estado del tramite de cada nave, para el listado. Una sola consulta. */
    @Transactional(readOnly = true)
    public Map<Long, EstadoTramite> estadosDe(Collection<Long> naveIds) {
        Map<Long, EstadoTramite> estados = new HashMap<>();
        if (naveIds.isEmpty()) {
            return estados;
        }
        for (Tramite t : tramites.buscarPorNaves(naveIds)) {
            estados.put(t.getNave().getId(), t.getEstado());
        }
        return estados;
    }

    /**
     * PO (13-10-2026): no se pasa a "En revision" mientras falte alguno de
     * TipoDocumento.OBLIGATORIOS. Cuenta un documento vigente en PENDIENTE
     * o APROBADO; uno OBSERVADO (o cualquier estado futuro) es faltante:
     * si no, se podria subsanar sin corregir justo lo observado.
     */
    public void exigirDocumentosObligatorios(Long naveId) {
        Set<TipoDocumento> presentes = documentos.findByNaveIdAndVigenteTrue(naveId).stream()
                .filter(d -> DOCUMENTO_PRESENTE.contains(d.getEstado()))
                .map(Documento::getTipo)
                .collect(Collectors.toSet());
        List<TipoDocumento> faltantes = TipoDocumento.OBLIGATORIOS.stream()
                .filter(t -> !presentes.contains(t))
                .toList();
        if (!faltantes.isEmpty()) {
            throw new DocumentosFaltantesException(faltantes);
        }
    }

    /**
     * PO (13-10-2026): con el tramite Aprobado, Rechazado o Desistido el
     * expediente es de solo lectura. Lo llaman las escrituras del expediente
     * (documentos, propietarios, agente residente) despues de su control de
     * acceso, para que a quien no es dueno se le responda 403 y no 409.
     */
    @Transactional(readOnly = true)
    public void exigirExpedienteAbierto(Long naveId) {
        tramites.findByNaveId(naveId)
                .map(Tramite::getEstado)
                .filter(EstadoTramite::esFinal)
                .ifPresent(estado -> {
                    throw new ReglaDeNegocioException("El trámite ya terminó (" + nombre(estado)
                            + "); el expediente es de solo lectura.");
                });
    }

    // ------------------------------------------------------------------

    private Usuario usuario(String correo) {
        return usuarios.findByCorreoIgnoreCase(correo)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se pudo identificar al usuario de la sesión."));
    }

    private Nave nave(Long naveId) {
        return naves.findById(naveId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La nave indicada no existe."));
    }

    /** Toda nave tiene tramite desde la V6; si falta, es un dato roto. */
    private Tramite tramiteDe(Nave nave) {
        return tramites.findByNaveId(nave.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La nave indicada no tiene trámite."));
    }

    private static String limpiar(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    static String nombre(EstadoTramite estado) {
        return switch (estado) {
            case SOLICITUD_PRESENTADA -> "Solicitud presentada";
            case EN_REVISION -> "En revisión";
            case OBSERVADA -> "Observada";
            case APROBADA -> "Aprobada";
            case RECHAZADA -> "Rechazada";
            case DESISTIDA -> "Desistida";
        };
    }
}
