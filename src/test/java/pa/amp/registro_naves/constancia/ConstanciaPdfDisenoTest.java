package pa.amp.registro_naves.constancia;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import pa.amp.registro_naves.nave.*;
import pa.amp.registro_naves.persona.*;
import pa.amp.registro_naves.tramite.*;
import pa.amp.registro_naves.usuario.Usuario;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.nio.file.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ConstanciaPdfDisenoTest {
    byte[] generar(int cantidad) {
        Nave nave=mock(Nave.class);
        when(nave.getId()).thenReturn(73L);when(nave.getNombre()).thenReturn("Océano de prueba");
        when(nave.getTipo()).thenReturn(TipoNave.CARGA);
        when(nave.getTonelajeBruto()).thenReturn(new BigDecimal("499.00"));
        when(nave.getTonelajeNeto()).thenReturn(new BigDecimal("250.00"));
        when(nave.getEslora()).thenReturn(new BigDecimal("24.00"));
        when(nave.getManga()).thenReturn(new BigDecimal("8.00"));
        when(nave.getPuntal()).thenReturn(new BigDecimal("4.00"));
        List<NavePropietario> propietarios=new ArrayList<>();
        for(int i=0;i<cantidad;i++) {
            Propietario p=new Propietario();p.setNombre("Titular Muñoz "+i+(cantidad>1?"L".repeat(180):""));
            NavePropietario np=new NavePropietario();np.setPropietario(p);np.setPorcentaje(new BigDecimal("100.00"));
            propietarios.add(np);
        }
        AgenteResidente agente=new AgenteResidente();agente.setNombre("Representante de prueba");agente.setIdoneidad("PRUEBA-73");
        HistorialEstado aprobacion=mock(HistorialEstado.class);Usuario usuario=new Usuario();usuario.setNombreCompleto("Responsable de prueba");
        when(aprobacion.getUsuario()).thenReturn(usuario);
        when(aprobacion.getFecha()).thenReturn(OffsetDateTime.parse("2026-11-14T10:00:00-05:00"));
        return new ConstanciaPdf().generar(nave,propietarios,agente,aprobacion);
    }
    @Test void conservaCamposTextoSeleccionableYA4() throws Exception {
        byte[] bytes=generar(1);
        try(var doc=Loader.loadPDF(bytes)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            assertThat(doc.getPage(0).getMediaBox().getWidth()).isEqualTo(PDRectangle.A4.getWidth());
            assertThat(new PDFTextStripper().getText(doc).replaceAll("\\s+"," ")).contains(
                "Registro completado","Océano de prueba","499.00 GT","250.00 TRN","24.00 m",
                "8.00 m","4.00 m","Titular Muñoz 0","100.00 %","Representante de prueba",
                "PRUEBA-73","14/11/2026 10:00:00 -05:00","Responsable de prueba","No constituye patente");
            Files.write(Path.of("target/constancia-diseno-prueba.pdf"),bytes);
            javax.imageio.ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(0,110),"png",Path.of("target/constancia-diseno-prueba.png").toFile());
        }
    }
    @Test void propietariosLargosNoSePierdenEnSaltosDePagina() throws Exception {
        try(var doc=Loader.loadPDF(generar(60))) {
            assertThat(doc.getNumberOfPages()).isGreaterThan(1);
            String texto=new PDFTextStripper().getText(doc).replaceAll("\\s+"," ");
            for(int i=0;i<60;i++)assertThat(texto).contains("Titular Muñoz "+i);
            assertThat(texto).contains("Responsable de prueba","Sin firma digital");
        }
    }
}
