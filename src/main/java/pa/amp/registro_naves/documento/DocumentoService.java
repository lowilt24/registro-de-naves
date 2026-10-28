package pa.amp.registro_naves.documento;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pa.amp.registro_naves.common.ArchivoDemasiadoGrandeException;
import pa.amp.registro_naves.common.FormatoNoSoportadoException;
import pa.amp.registro_naves.common.RecursoNoEncontradoException;
import pa.amp.registro_naves.common.ReglaDeNegocioException;
import pa.amp.registro_naves.common.ValidacionCampoException;
import pa.amp.registro_naves.nave.AccesoNave;
import pa.amp.registro_naves.nave.Nave;
import pa.amp.registro_naves.persona.AgenteResidenteRepository;
import pa.amp.registro_naves.persona.NavePropietarioRepository;
import pa.amp.registro_naves.tramite.AutorizacionTramite;
import pa.amp.registro_naves.tramite.EstadoTramite;
import pa.amp.registro_naves.tramite.TramiteService;
import pa.amp.registro_naves.usuario.Usuario;
import pa.amp.registro_naves.usuario.UsuarioRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class DocumentoService {

    /** HU-07, criterio 4. */
    public static final long LIMITE_BYTES = 10L * 1024 * 1024;

    /** HU-07, criterio 3: todo PDF empieza con estos cinco bytes. */
    private static final byte[] FIRMA_PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final DocumentoRepository documentos;
    private final AlmacenArchivos almacen;
    private final AccesoNave accesoNave;
    private final NavePropietarioRepository vinculos;
    private final AgenteResidenteRepository agentes;
    private final UsuarioRepository usuarios;
    private final TramiteService tramites;
    private final AutorizacionTramite autorizacion;

    /** Observacion 5 de QA: el mismo limite que historial_estados.observacion. */
    static final int LIMITE_OBSERVACION = 1000;

    public DocumentoService(DocumentoRepository documentos,
                            AlmacenArchivos almacen,
                            AccesoNave accesoNave,
                            NavePropietarioRepository vinculos,
                            AgenteResidenteRepository agentes,
                            UsuarioRepository usuarios,
                            TramiteService tramites,
                            AutorizacionTramite autorizacion) {
        this.documentos = documentos;
        this.almacen = almacen;
        this.accesoNave = accesoNave;
        this.vinculos = vinculos;
        this.agentes = agentes;
        this.usuarios = usuarios;
        this.tramites = tramites;
        this.autorizacion = autorizacion;
    }

    // ------------------------------------------------------------------
    // HU-07 — carga
    // ------------------------------------------------------------------

    /**
     * El orden de las comprobaciones es parte del contrato
     * (CONTRATO-DOCUMENTOS.md) para que las pruebas sean deterministas:
     * 404/403 nave -> 400 campos -> 413 tamano -> 415 firma -> 409 precondicion.
     * Sprint 5: la primera precondicion es que el tramite no haya terminado.
     * La sesion (401) la resuelve Spring Security antes de llegar aqui.
     */
    @Transactional
    public DocumentoResponse cargar(Long naveId, String tipoTexto, MultipartFile archivo,
                                    String correoUsuario) {

        Nave nave = accesoNave.exigirPropia(naveId, correoUsuario);

        TipoDocumento tipo = leerTipo(tipoTexto, true);
        if (archivo == null || archivo.isEmpty()) {
            throw new ValidacionCampoException("archivo", "Adjunte el archivo PDF.");
        }

        if (archivo.getSize() > LIMITE_BYTES) {
            throw new ArchivoDemasiadoGrandeException("El archivo supera el limite de 10 MB.");
        }

        if (!empiezaConFirmaPdf(archivo)) {
            throw new FormatoNoSoportadoException("El archivo no es un PDF valido.");
        }

        // Sprint 5 (PO): con el tramite terminado el expediente es de solo lectura.
        tramites.exigirExpedienteAbierto(naveId);

        // HU-07, criterio 8: precondicion encadenada con el Sprint 3.
        if (!vinculos.existsByNaveId(naveId)
                || agentes.findByNaveIdAndVigenteTrue(naveId).isEmpty()) {
            throw new ReglaDeNegocioException(
                    "La nave necesita al menos un propietario y un agente residente "
                    + "designado antes de cargar documentos.");
        }

        Usuario usuario = usuarios.findByCorreoIgnoreCase(correoUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se pudo identificar al usuario de la sesion."));

        // HU-07, criterio 7: la version anterior no se borra, deja de ser vigente.
        int version = 1;
        var anterior = documentos.findByNaveIdAndTipoAndVigenteTrue(naveId, tipo);
        if (anterior.isPresent()) {
            version = anterior.get().getVersion() + 1;
            anterior.get().setVigente(false);
            // El indice unico parcial admite una sola vigente por tipo:
            // la anterior tiene que bajar antes de insertar la nueva.
            documentos.saveAndFlush(anterior.get());
        }

        AlmacenArchivos.Guardado guardado = almacen.guardar(naveId, archivo);

        Documento documento = new Documento();
        documento.setNave(nave);
        documento.setTipo(tipo);
        documento.setNombreArchivo(limpiarNombre(archivo.getOriginalFilename()));
        documento.setRuta(guardado.rutaRelativa());
        documento.setHashSha256(guardado.hashSha256());
        documento.setTamanoBytes(guardado.tamanoBytes());
        documento.setVersion(version);
        documento.setVigente(true);
        documento.setEstado(EstadoDocumento.PENDIENTE);
        documento.setCreadoPor(usuario);

        try {
            return DocumentoResponse.de(documentos.saveAndFlush(documento));
        } catch (DataIntegrityViolationException ex) {
            // Dos cargas del mismo tipo al mismo tiempo: gana una, la otra
            // choca con uk_documentos_version. El archivo de la perdedora sobra.
            almacen.borrarSilencioso(guardado.rutaRelativa());
            throw new ReglaDeNegocioException(
                    "Otra carga del mismo tipo de documento se guardo al mismo tiempo. Intente de nuevo.");
        }
    }

    // ------------------------------------------------------------------
    // HU-08 — consulta y descarga
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<DocumentoResponse> listar(Long naveId, String tipoTexto, String estadoTexto,
                                          boolean historial, String correoUsuario) {
        // Sprint 6 (HU-12): la DGMM y el auditor tambien leen (observacion 2 de QA).
        accesoNave.exigirLectura(naveId, correoUsuario);
        TipoDocumento tipo = leerTipo(tipoTexto, false);
        EstadoDocumento estado = leerEstado(estadoTexto);
        return documentos.buscar(naveId, tipo, estado, historial).stream()
                .map(DocumentoResponse::de)
                .toList();
    }

    /** Lo que necesita el controlador para responder la descarga. */
    public record Descarga(Path archivo, String nombre) {}

    /**
     * HU-08, criterio 5. Se verifica la nave (403/404) y ademas que el
     * documento sea de esa nave: sin lo segundo, alguien con una nave
     * propia podria pedir /api/naves/{suya}/documentos/{ajeno}/archivo y
     * pasar el control. Ese caso responde 404, como si no existiera.
     */
    @Transactional(readOnly = true)
    public Descarga descargar(Long naveId, Long documentoId, String correoUsuario) {
        accesoNave.exigirLectura(naveId, correoUsuario);
        Documento documento = documentos.findByIdAndNaveId(documentoId, naveId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El documento no existe."));

        Path archivo = almacen.ubicar(documento.getRuta());
        if (!Files.isReadable(archivo)) {
            throw new RecursoNoEncontradoException("El archivo del documento no esta disponible.");
        }
        return new Descarga(archivo, documento.getNombreArchivo());
    }

    // ------------------------------------------------------------------
    // HU-12 — revision de la DGMM
    // ------------------------------------------------------------------

    /**
     * La DGMM aprueba u observa un documento.
     *
     * Orden de los errores (criterio de HU-12, aceptado por la PO; igual
     * que HU-09). Con dos errores a la vez sale el primero:
     *   401 sin sesion (Spring Security)
     *   404 la nave no existe, o el documento no es de ESA nave (IDOR)
     *   403 no es funcionario de la DGMM (el agente y el auditor no revisan)
     *   409 el tramite no esta En revision (D2), el documento ya no es la
     *       version vigente, o ya fue revisado
     *   400 decision vacia o inventada; observar sin observacion; mas de
     *       1000 caracteres
     *
     * D3: revisar un documento no mueve el tramite. Eso lo hace la DGMM
     * aparte, con las acciones de HU-09.
     */
    @Transactional
    public DocumentoResponse revisar(Long naveId, Long documentoId, RevisionRequest peticion,
                                     String correoUsuario) {
        Usuario usuario = usuarios.findByCorreoIgnoreCase(correoUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se pudo identificar al usuario de la sesión."));

        Nave nave = accesoNave.exigirLectura(naveId, correoUsuario);                // 404
        Documento documento = documentos.findByIdAndNaveId(documentoId, naveId)      // 404
                .orElseThrow(() -> new RecursoNoEncontradoException("El documento no existe."));

        autorizacion.exigirRevisionDeDocumentos(usuario);                          // 403

        EstadoTramite estadoTramite = tramites.estadoDe(nave);                      // 409
        if (estadoTramite != EstadoTramite.EN_REVISION) {
            throw new ReglaDeNegocioException(
                    "Los documentos solo se revisan con el trámite En revisión.");
        }
        if (!documento.isVigente()) {
            throw new ReglaDeNegocioException(
                    "Ese documento ya fue reemplazado por una versión nueva. Revise la versión vigente.");
        }
        if (documento.getEstado() != EstadoDocumento.PENDIENTE) {
            throw new ReglaDeNegocioException("El documento ya fue revisado.");
        }

        DecisionRevision decision = leerDecision(peticion == null ? null : peticion.decision()); // 400
        String observacion = limpiarObservacion(peticion == null ? null : peticion.observacion());
        if (observacion != null && observacion.length() > LIMITE_OBSERVACION) {
            throw new ValidacionCampoException("observacion",
                    "La observación no puede exceder " + LIMITE_OBSERVACION + " caracteres.");
        }
        if (decision == DecisionRevision.OBSERVAR && observacion == null) {
            throw new ValidacionCampoException("observacion",
                    "Escriba la observación: es obligatoria al observar un documento.");
        }

        // Observacion 4 de QA: el UPDATE solo pasa si sigue vigente y
        // pendiente. Si otro funcionario lo reviso (o el agente lo
        // reemplazo) en el medio, no cambia ninguna fila.
        int cambiadas = documentos.revisar(documento.getId(), decision.resultado(), observacion,
                usuario, OffsetDateTime.now());
        if (cambiadas == 0) {
            throw new ReglaDeNegocioException(
                    "El documento cambió mientras tanto. Recargue la página e intente de nuevo.");
        }

        Documento revisado = documentos.findByIdAndNaveId(documento.getId(), naveId).orElseThrow();
        return DocumentoResponse.de(revisado);
    }

    private static DecisionRevision leerDecision(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new ValidacionCampoException("decision", "Indique la decisión: APROBAR u OBSERVAR.");
        }
        try {
            return DecisionRevision.valueOf(texto.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidacionCampoException("decision",
                    "La decisión no es válida. Use APROBAR u OBSERVAR.");
        }
    }

    /** Sin espacios al borde; vacia cuenta como no escrita. */
    private static String limpiarObservacion(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    // ------------------------------------------------------------------

    private TipoDocumento leerTipo(String texto, boolean obligatorio) {
        if (texto == null || texto.isBlank()) {
            if (obligatorio) {
                throw new ValidacionCampoException("tipo", "Seleccione el tipo de documento.");
            }
            return null;
        }
        try {
            return TipoDocumento.valueOf(texto.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidacionCampoException("tipo",
                    "El tipo de documento no es valido. Use CERTIFICADO_PROPIEDAD, "
                    + "PODER_NOTARIAL, ITC o SMC.");
        }
    }

    private EstadoDocumento leerEstado(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return EstadoDocumento.valueOf(texto.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidacionCampoException("estado",
                    "El estado no es valido. Use PENDIENTE, APROBADO u OBSERVADO.");
        }
    }

    /**
     * La firma binaria, no la extension ni el Content-Type: los dos
     * ultimos los decide quien manda el archivo.
     */
    private boolean empiezaConFirmaPdf(MultipartFile archivo) {
        try (InputStream entrada = archivo.getInputStream()) {
            byte[] inicio = entrada.readNBytes(FIRMA_PDF.length);
            return Arrays.equals(inicio, FIRMA_PDF);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * HU-07, criterio 5. El nombre solo se guarda para mostrarlo y para el
     * nombre de la descarga; aun asi se limpia: se queda con la ultima
     * parte de la ruta, sin caracteres de control ni separadores.
     */
    static String limpiarNombre(String original) {
        if (original == null) {
            return "documento.pdf";
        }
        String nombre = original.replace('\\', '/');
        nombre = nombre.substring(nombre.lastIndexOf('/') + 1);
        nombre = nombre.replaceAll("[\\p{Cntrl}\"<>|:*?]", "").replace("..", "").trim();
        if (nombre.isEmpty()) {
            nombre = "documento.pdf";
        }
        if (nombre.length() > 200) {
            nombre = nombre.substring(nombre.length() - 200);
        }
        return nombre;
    }
}
