package pa.amp.registro_naves.documento;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.transaction.annotation.Transactional;
import pa.amp.registro_naves.common.RecursoNoEncontradoException;
import pa.amp.registro_naves.common.ArchivoDemasiadoGrandeException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Guarda los PDF en disco fuera de static/, o en PostgreSQL con el perfil gratis.
 * La lectura siempre pasa primero por los permisos de DocumentoService.
 *
 * Por que fuera de static/: SecurityConfig solo publica /css/ y /js/, y
 * Spring solo sirve lo que esta en classpath:/static. Una carpeta en la
 * raiz del proyecto no tiene URL; la unica forma de leer un archivo es el
 * endpoint de descarga, que es donde se verifica la propiedad de la nave.
 *
 * HU-07, criterio 5: la ruta la arma el servidor con el id de la nave y
 * un UUID. El nombre que manda el cliente nunca toca el sistema de
 * archivos, asi que "../../etc/passwd" no tiene a donde ir.
 */
@Component
public class AlmacenArchivos {

    private final Path raiz;
    private final JdbcTemplate db;
    private final boolean postgres;

    public AlmacenArchivos(@Value("${navesitas.almacen.ruta:almacen}") String ruta) {
        this(ruta, null, "disco");
    }

    @Autowired
    public AlmacenArchivos(@Value("${navesitas.almacen.ruta:almacen}") String ruta,
                           JdbcTemplate db,
                           @Value("${navesitas.almacen.tipo:disco}") String tipo) {
        if (!tipo.equals("disco") && !tipo.equals("postgres")) {
            throw new IllegalArgumentException("Tipo de almacén no válido: use disco o postgres.");
        }
        this.raiz = Path.of(ruta).toAbsolutePath().normalize();
        this.db = db;
        this.postgres = tipo.equals("postgres");
        if (postgres && db == null) throw new IllegalArgumentException("Falta PostgreSQL para los adjuntos.");
    }

    /** Resultado de guardar: ruta relativa (para la base) y hash del contenido. */
    public record Guardado(String rutaRelativa, String hashSha256, long tamanoBytes) {}

    @Transactional
    public Guardado guardar(Long naveId, MultipartFile archivo) {
        String relativa = naveId + "/" + UUID.randomUUID() + ".pdf";
        if (postgres) {
            try (var entrada = archivo.getInputStream()) {
                byte[] bytes = entrada.readNBytes(10 * 1024 * 1024 + 1);
                if (bytes.length > 10 * 1024 * 1024) throw new ArchivoDemasiadoGrandeException("Máximo 10 MB por PDF.");
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                db.update("INSERT INTO archivos_documentales(ruta,nave_id,contenido) VALUES(?,?,?)", relativa, naveId, bytes);
                return new Guardado(relativa, hash, bytes.length);
            } catch (IOException e) { throw new UncheckedIOException("No se pudo leer el documento", e);
            } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 no disponible", e); }
        }
        Path destino = resolver(relativa);

        try {
            Files.createDirectories(destino.getParent());

            // Se escribe primero a un temporal y se mueve al final: si la
            // copia se corta, no queda un PDF a medias con nombre definitivo.
            Path temporal = Files.createTempFile(destino.getParent(), "carga-", ".tmp");
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            long tamano;
            try (InputStream entrada = new DigestInputStream(archivo.getInputStream(), sha256)) {
                tamano = Files.copy(entrada, temporal, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE);

            return new Guardado(relativa, HexFormat.of().formatHex(sha256.digest()), tamano);

        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    public Path ubicar(String rutaRelativa) {
        if (postgres) throw new IllegalStateException("El almacén PostgreSQL no tiene rutas de disco: use recurso().");
        return resolver(rutaRelativa);
    }

    public Resource recurso(String rutaRelativa) {
        resolver(rutaRelativa); // Misma barrera de recorrido de rutas en ambos modos.
        if (postgres) {
            try {
                byte[] bytes = db.queryForObject("SELECT contenido FROM archivos_documentales WHERE ruta=?", byte[].class, rutaRelativa);
                if (bytes == null) throw new EmptyResultDataAccessException(1);
                return new ByteArrayResource(bytes);
            } catch (EmptyResultDataAccessException e) {
                throw new RecursoNoEncontradoException("El archivo del documento no está disponible.");
            }
        }
        Path archivo = resolver(rutaRelativa);
        if (!Files.isReadable(archivo)) throw new RecursoNoEncontradoException("El archivo del documento no está disponible.");
        return new FileSystemResource(archivo);
    }

    public byte[] leer(String rutaRelativa) throws IOException {
        try (var entrada = recurso(rutaRelativa).getInputStream()) {
            byte[] bytes = entrada.readNBytes(10 * 1024 * 1024 + 1);
            if (bytes.length > 10 * 1024 * 1024) throw new ArchivoDemasiadoGrandeException("Máximo 10 MB por PDF.");
            return bytes;
        }
    }

    /** Se usa para deshacer la escritura si la base rechaza el registro. */
    public void borrarSilencioso(String rutaRelativa) {
        try {
            if (postgres) {
                resolver(rutaRelativa);
                db.update("DELETE FROM archivos_documentales WHERE ruta=?", rutaRelativa);
                return;
            }
            Files.deleteIfExists(resolver(rutaRelativa));
        } catch (IOException | RuntimeException ignorada) {
            // Queda un archivo huerfano en disco; no es motivo para tapar el error original.
        }
    }

    /**
     * Segunda barrera contra recorrido de rutas: aunque la ruta la genera
     * el servidor, se verifica que el resultado siga dentro del almacen.
     */
    private Path resolver(String rutaRelativa) {
        Path ruta = raiz.resolve(rutaRelativa).normalize();
        if (!ruta.startsWith(raiz)) {
            throw new IllegalArgumentException("Ruta fuera del almacen");
        }
        return ruta;
    }
}
