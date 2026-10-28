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
import pa.amp.registro_naves.nave.EstadoNave;
import pa.amp.registro_naves.nave.Nave;
import pa.amp.registro_naves.nave.NaveRepository;
import pa.amp.registro_naves.tramite.TramiteDtos.BandejaItem;
import pa.amp.registro_naves.tramite.TramiteDtos.CambioEstadoRequest;
import pa.amp.registro_naves.tramite.TramiteDtos.ConteoPorEstadoResponse;
import pa.amp.registro_naves.tramite.TramiteDtos.HistorialResponse;
import pa.amp.registro_naves.tramite.TramiteDtos.TramiteResponse;
import pa.amp.registro_naves.usuario.Usuario;
import pa.amp.registro_naves.usuario.UsuarioRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
        // Sprint 6 (HU-12, D4): aprobar exige los cuatro obligatorios
        // vigentes en APROBADO.
        if (nuevo == EstadoTramite.APROBADA) {
            exigirDocumentosAprobados(nave.getId());              // 409
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

        // Sprint 6 (HU-12, D5): la decision de la DGMM llega a la nave, en
        // esta misma transaccion. Desistida no la toca: queda REGISTRADA
        // (observacion 7 de QA). TRAMITES sigue siendo la fuente del estado
        // del tramite; nave.estado solo se escribe aqui.
        if (nuevo == EstadoTramite.APROBADA) {
            nave.setEstado(EstadoNave.ABANDERADA);
            naves.save(nave);
        } else if (nuevo == EstadoTramite.RECHAZADA) {
            nave.setEstado(EstadoNave.RECHAZADA);
            naves.save(nave);
        }
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
        return conteo(tramites.contarPorEstado(correo));
    }

    // ------------------------------------------------------------------
    // HU-11 — bandeja de la DGMM
    // ------------------------------------------------------------------

    /** Sin filtro, la bandeja muestra lo que espera una decision de la DGMM (D1). */
    static final List<EstadoTramite> POR_ATENDER =
            List.of(EstadoTramite.SOLICITUD_PRESENTADA, EstadoTramite.EN_REVISION);

    /** Los dias en espera se cuentan en dias de calendario de Panama. */
    static final ZoneId ZONA = ZoneId.of("America/Panama");

    /**
     * GET /api/tramites. Tramites de todas las naves (D4 del Sprint 5: se
     * filtra por TRAMITES, nunca por nave.estado), el que lleva mas tiempo
     * esperando primero. Solo DGMM y auditor; el agente recibe 403.
     */
    @Transactional(readOnly = true)
    public List<BandejaItem> bandeja(String correo, EstadoTramite estado) {
        autorizacion.exigirBandeja(usuario(correo));                // 403
        List<EstadoTramite> estados = estado == null ? POR_ATENDER : List.of(estado);
        LocalDate hoy = LocalDate.now(ZONA);
        return tramites.bandeja(estados).stream()
                .map(t -> new BandejaItem(
                        t.getNave().getId(),
                        t.getNave().getNombre(),
                        t.getNave().getRegistradoPor().getNombreCompleto(),
                        t.getEstado(),
                        t.getActualizadoEn(),
                        diasEntre(t.getActualizadoEn(), hoy)))
                .toList();
    }

    /** GET /api/tramites/conteo-por-estado: los seis estados, de todas las naves. */
    @Transactional(readOnly = true)
    public ConteoPorEstadoResponse contarBandeja(String correo) {
        autorizacion.exigirBandeja(usuario(correo));                // 403
        return conteo(tramites.contarTodosPorEstado());
    }

    static long diasEntre(OffsetDateTime desde, LocalDate hoy) {
        if (desde == null) {
            return 0;
        }
        long dias = ChronoUnit.DAYS.between(desde.atZoneSameInstant(ZONA).toLocalDate(), hoy);
        return Math.max(dias, 0);
    }

    /** Los seis estados en el orden de la maquina, con cero los que no aparecen. */
    private static ConteoPorEstadoResponse conteo(List<Object[]> filas) {
        Map<EstadoTramite, Long> conteo = new EnumMap<>(EstadoTramite.class);
        for (EstadoTramite estado : EstadoTramite.values()) {
            conteo.put(estado, 0L);
        }
        long total = 0;
        for (Object[] fila : filas) {
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
     * Sprint 6 (HU-12, D4): para aprobar, los cuatro obligatorios vigentes
     * tienen que estar APROBADOS. Uno pendiente u observado cuenta como
     * "sin aprobar". La lista sale en el orden de TipoDocumento.OBLIGATORIOS.
     */
    public void exigirDocumentosAprobados(Long naveId) {
        Set<TipoDocumento> aprobados = documentos.findByNaveIdAndVigenteTrue(naveId).stream()
                .filter(d -> d.getEstado() == EstadoDocumento.APROBADO)
                .map(Documento::getTipo)
                .collect(Collectors.toSet());
        List<TipoDocumento> sinAprobar = TipoDocumento.OBLIGATORIOS.stream()
                .filter(t -> !aprobados.contains(t))
                .toList();
        if (!sinAprobar.isEmpty()) {
            throw DocumentosFaltantesException.sinAprobar(sinAprobar);
        }
    }

    /** HU-12: estado actual del tramite de la nave (404 si no tiene). */
    @Transactional(readOnly = true)
    public EstadoTramite estadoDe(Nave nave) {
        return tramiteDe(nave).getEstado();
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
