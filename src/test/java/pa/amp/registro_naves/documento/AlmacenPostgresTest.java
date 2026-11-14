package pa.amp.registro_naves.documento;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.dao.EmptyResultDataAccessException;
import pa.amp.registro_naves.common.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class AlmacenPostgresTest {
    @TempDir Path temporal;
    @Test void escrituraYLecturaNoDependenDelDiscoNiDeLaInstanciaDelServidor() throws Exception {
        JdbcTemplate db=mock(JdbcTemplate.class);
        Map<String,byte[]> filas=new HashMap<>();
        when(db.update(startsWith("INSERT"),anyString(),anyLong(),any(byte[].class))).thenAnswer(i->{
            filas.put(i.getArgument(1),i.getArgument(3));return 1;
        });
        when(db.queryForObject(anyString(),eq(byte[].class),anyString())).thenAnswer(i->filas.get(i.getArgument(2)));
        byte[] contenido="%PDF-1.7\narchivo de prueba".getBytes(StandardCharsets.UTF_8);
        Path sinDisco=temporal.resolve("no-creado");
        var almacen=new AlmacenArchivos(sinDisco.toString(),db,"postgres");
        var g=almacen.guardar(42L,new MockMultipartFile("archivo","../../fuera.pdf","application/pdf",contenido));
        assertThat(g.rutaRelativa()).matches("42/[0-9a-f-]{36}\\.pdf");
        assertThat(g.hashSha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido)));
        assertThat(g.tamanoBytes()).isEqualTo(contenido.length);
        assertThat(Files.exists(sinDisco)).isFalse();
        var reiniciado=new AlmacenArchivos(temporal.resolve("otra-raiz").toString(),db,"postgres");
        assertThat(reiniciado.leer(g.rutaRelativa())).isEqualTo(contenido);
        assertThatThrownBy(()->reiniciado.ubicar(g.rutaRelativa())).isInstanceOf(IllegalStateException.class);
    }
    @Test void archivoAusenteRecorridoDeRutaYTopeSiguenControlados() {
        JdbcTemplate db=mock(JdbcTemplate.class);
        var almacen=new AlmacenArchivos(temporal.toString(),db,"postgres");
        assertThatThrownBy(()->almacen.recurso("../../fuera.pdf")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(db);
        when(db.queryForObject(anyString(),eq(byte[].class),anyString())).thenThrow(new EmptyResultDataAccessException(1));
        assertThatThrownBy(()->almacen.recurso("42/ausente.pdf")).isInstanceOf(RecursoNoEncontradoException.class);
        clearInvocations(db);
        assertThatThrownBy(()->almacen.guardar(42L,new MockMultipartFile("archivo","grande.pdf","application/pdf",new byte[10485761])))
            .isInstanceOf(ArchivoDemasiadoGrandeException.class);
        verifyNoInteractions(db);
    }
    @Test void modoDiscoSigueDisponibleSinCambiarLasDescargasLocales() throws Exception {
        var almacen=new AlmacenArchivos(temporal.toString());
        byte[] contenido="%PDF-1.7\nlocal".getBytes(StandardCharsets.UTF_8);
        var g=almacen.guardar(7L,new MockMultipartFile("archivo","local.pdf","application/pdf",contenido));
        assertThat(Files.readAllBytes(almacen.ubicar(g.rutaRelativa()))).isEqualTo(contenido);
        assertThat(almacen.leer(g.rutaRelativa())).isEqualTo(contenido);
        almacen.borrarSilencioso(g.rutaRelativa());
        assertThat(Files.exists(almacen.ubicar(g.rutaRelativa()))).isFalse();
    }
}
