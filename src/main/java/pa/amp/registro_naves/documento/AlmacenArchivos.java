package pa.amp.registro_naves.documento;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
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
 * Guarda los PDF en disco, en una carpeta fuera de static/.
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

    public AlmacenArchivos(@Value("${navesitas.almacen.ruta:almacen}") String ruta) {
        this.raiz = Path.of(ruta).toAbsolutePath().normalize();
    }

    /** Resultado de guardar: ruta relativa (para la base) y hash del contenido. */
    public record Guardado(String rutaRelativa, String hashSha256, long tamanoBytes) {}

    public Guardado guardar(Long naveId, MultipartFile archivo) {
        String relativa = naveId + "/" + UUID.randomUUID() + ".pdf";
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
        return resolver(rutaRelativa);
    }

    /** Se usa para deshacer la escritura si la base rechaza el registro. */
    public void borrarSilencioso(String rutaRelativa) {
        try {
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
