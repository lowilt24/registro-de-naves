package pa.amp.registro_naves.documento;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import pa.amp.registro_naves.nave.AccesoNave;
import pa.amp.registro_naves.common.*;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class DescargaProtegidaTest {
    @Test void nuncaLeeAdjuntosSinPermisoOSiSonDeOtraNave() {
        var documentos=mock(DocumentoRepository.class);var almacen=mock(AlmacenArchivos.class);
        var acceso=mock(AccesoNave.class);
        var servicio=new DocumentoService(documentos,almacen,acceso,null,null,null,null,null,null);
        doThrow(new AccesoDenegadoException("Sin permiso")).when(acceso).exigirLectura(42L,"ajeno@example.test");
        assertThatThrownBy(()->servicio.descargar(42L,7L,"ajeno@example.test")).isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(documentos,almacen);
        when(documentos.findByIdAndNaveId(7L,42L)).thenReturn(Optional.empty());
        assertThatThrownBy(()->servicio.descargar(42L,7L,"permitido@example.test")).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(almacen);
    }
    @Test void descargaAutorizadaAceptaRecursoDePostgresqlSinRutaPublica() throws Exception {
        var documentos=mock(DocumentoRepository.class);var almacen=mock(AlmacenArchivos.class);
        var acceso=mock(AccesoNave.class);
        var servicio=new DocumentoService(documentos,almacen,acceso,null,null,null,null,null,null);
        Documento documento=new Documento();documento.setRuta("42/uuid.pdf");documento.setNombreArchivo("documento.pdf");
        when(documentos.findByIdAndNaveId(7L,42L)).thenReturn(Optional.of(documento));
        byte[] bytes="%PDF-1.7\nprueba".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(almacen.recurso("42/uuid.pdf")).thenReturn(new ByteArrayResource(bytes));
        var descarga=servicio.descargar(42L,7L,"permitido@example.test");
        assertThat(descarga.nombre()).isEqualTo("documento.pdf");
        assertThat(descarga.archivo().getInputStream().readAllBytes()).isEqualTo(bytes);
        var orden=inOrder(acceso,documentos,almacen);
        orden.verify(acceso).exigirLectura(42L,"permitido@example.test");
        orden.verify(documentos).findByIdAndNaveId(7L,42L);
        orden.verify(almacen).recurso("42/uuid.pdf");
    }
}
