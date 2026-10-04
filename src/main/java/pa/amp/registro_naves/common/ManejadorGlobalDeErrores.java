package pa.amp.registro_naves.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Los mensajes que salen de aqui son genericos a proposito: no exponen
 * nombres de tabla, SQL ni stack traces.
 */
@RestControllerAdvice
public class ManejadorGlobalDeErrores {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException ex) {
        Map<String, String> campos = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
          .forEach(e -> campos.putIfAbsent(e.getField(), e.getDefaultMessage()));

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("mensaje", "Hay campos obligatorios sin completar o con formato invalido.");
        cuerpo.put("campos", campos);
        return ResponseEntity.badRequest().body(cuerpo);
    }

    /**
     * Remediacion del hallazgo SEC-01 del informe de seguridad del Sprint 2.
     *
     * Un cuerpo JSON mal formado (por ejemplo {"tonelajeBruto": abc}) falla
     * al deserializar, antes de llegar a la validacion. Sin este manejador
     * caia en el catch generico de abajo y se respondia 500, dando a
     * entender que el servidor habia fallado cuando el error era del
     * cliente. El fuzzing produjo seis casos de estos.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> cuerpoIlegible(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "mensaje", "El cuerpo de la solicitud no tiene un formato valido."));
    }

    @ExceptionHandler(ValidacionCampoException.class)
    public ResponseEntity<Map<String, Object>> campoInvalido(ValidacionCampoException ex) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("mensaje", "Hay campos obligatorios sin completar o con formato invalido.");
        cuerpo.put("campos", Map.of(ex.getCampo(), ex.getMessage()));
        return ResponseEntity.badRequest().body(cuerpo);
    }

    @ExceptionHandler(ReglaDeNegocioException.class)
    public ResponseEntity<Map<String, Object>> negocio(ReglaDeNegocioException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensaje", ex.getMessage()));
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> noEncontrado(RecursoNoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("mensaje", ex.getMessage()));
    }

    @ExceptionHandler(AccesoDenegadoException.class)
    public ResponseEntity<Map<String, Object>> accesoDenegado(AccesoDenegadoException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("mensaje", ex.getMessage()));
    }

    // ------------------------------------------------------------------
    // Sprint 4 — carga de archivos (HU-07)
    // ------------------------------------------------------------------

    /** HU-07, criterio 4: el archivo pasa de 10 MB. */
    @ExceptionHandler(ArchivoDemasiadoGrandeException.class)
    public ResponseEntity<Map<String, Object>> archivoGrande(ArchivoDemasiadoGrandeException ex) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(Map.of("mensaje", ex.getMessage()));
    }

    /**
     * Lo que pasa del tope del servlet (12 MB, ver application.properties)
     * ni siquiera llega al servicio. Tambien es 413, no 500.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> cargaExcedida(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(Map.of("mensaje", "El archivo supera el limite de 10 MB."));
    }

    /** HU-07, criterio 3: la firma binaria no es la de un PDF. */
    @ExceptionHandler(FormatoNoSoportadoException.class)
    public ResponseEntity<Map<String, Object>> formatoNoSoportado(FormatoNoSoportadoException ex) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("mensaje", ex.getMessage());
        cuerpo.put("campos", Map.of("archivo", "El contenido del archivo no corresponde a un PDF."));
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(cuerpo);
    }

    /** Peticion multipart rota (por ejemplo, cortada a la mitad). */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, Object>> multipartInvalido(MultipartException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("mensaje", "La carga no tiene un formato valido."));
    }

    // ------------------------------------------------------------------
    // Errores del cliente que antes caian en el 500 generico. Los encontro
    // el fuzzing: un 500 da a entender que el servidor fallo cuando la
    // peticion era la que estaba mal (mismo caso que SEC-01).
    // ------------------------------------------------------------------

    /** Por ejemplo, JSON enviado al endpoint de carga, que espera multipart. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> tipoDeContenido(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(Map.of("mensaje", "El tipo de contenido de la solicitud no es el esperado."));
    }

    /** Por ejemplo, /api/naves/abc/documentos. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> parametroInvalido(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("mensaje", "Uno de los parametros de la solicitud no es valido."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> metodoNoPermitido(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Map.of("mensaje", "Metodo no permitido."));
    }

    /** Ruta inexistente con sesion iniciada: 404, no 500. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> rutaInexistente(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("mensaje", "Recurso no encontrado."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> inesperado(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("mensaje", "No se pudo procesar la solicitud. Intente de nuevo."));
    }
}
