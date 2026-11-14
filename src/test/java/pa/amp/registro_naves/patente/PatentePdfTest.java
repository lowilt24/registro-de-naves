package pa.amp.registro_naves.patente;

import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.*;
import org.junit.jupiter.api.Test;
import pa.amp.registro_naves.common.ReglaDeNegocioException;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PatentePdfTest {
    static final UUID ID=UUID.fromString("0d94be8d-4e42-4ad8-8ba8-9f55e48df0b4");
    final PatentePdf pdf=new PatentePdf("https://registro.example.test/sistema/","","");
    // Datos sintéticos exclusivos de pruebas; nunca se usan para emitir una patente.
    static final String DATOS="""
        Simulación académica de patente provisional — sin validez oficial
        Número: PRUEBA-42
        Solicitud: 42 | Nave registrada: 73
        Fecha de emisión: 2026-11-14T10:20:00-05:00
        Funcionario: Responsable de prueba
        Acto de aprobación del registro: 12
        Envío aprobado: 21
        Registro de nave: 73
        Nombre: Nave de prueba Muñoz
        Tipo: CARGA | Servicio: INTERNACIONAL
        Estado de nave: REGISTRADA
        Tonelaje bruto: 499 | Neto: 250
        Eslora: 24 m | Manga: 8 m | Puntal: 4 m
        Construcción: 2020 | Panamá
        Casco: Acero | Propulsión: Diésel | Potencia: 750 kW
        Propietarios:
        - Titular de prueba | 100 %
        Agente residente: Representante de prueba | Idoneidad: 321
        Sin firma oficial. Sin vencimiento definido. No habilita navegación real.
        """;

    @Test void a4TextoSeleccionableDatosCompletosYQrLegible() throws Exception {
        byte[] bytes=pdf.generar("PRUEBA-42",DATOS,ID);
        try(var doc=Loader.loadPDF(bytes)) {
            String texto=new PDFTextStripper().getText(doc).replaceAll("\\s+"," ");
            for(String linea:DATOS.split("\n"))
                assertThat(texto).contains(linea.replaceAll("\\s+"," ").strip());
            assertThat(texto).contains("PATENTE PROVISIONAL DE NAVEGACIÓN","Firma pendiente",
                "Sin firma digital criptográfica","Sin vencimiento definido");
            assertThat(doc.getSignatureDictionaries()).isEmpty();
            boolean leido=false;
            var renderer=new PDFRenderer(doc);
            for(int p=0;p<doc.getNumberOfPages();p++) {
                assertThat(doc.getPage(p).getMediaBox().getWidth()).isEqualTo(PDRectangle.A4.getWidth());
                assertThat(doc.getPage(p).getMediaBox().getHeight()).isEqualTo(PDRectangle.A4.getHeight());
                var img=renderer.renderImageWithDPI(p,180);
                int[] pixeles=img.getRGB(0,0,img.getWidth(),img.getHeight(),null,0,img.getWidth());
                try {
                    String url=new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(
                        new RGBLuminanceSource(img.getWidth(),img.getHeight(),pixeles)))).getText();
                    assertThat(url).isEqualTo(pdf.urlVerificacion(ID));leido=true;
                } catch(NotFoundException ignorada) { /* El QR aparece solo en la última página. */ }
                Path salida=Path.of("target/patente-diseno-pagina-"+(p+1)+".png");
                javax.imageio.ImageIO.write(img,"png",salida.toFile());
            }
            assertThat(leido).as("QR leído desde el PDF renderizado").isTrue();
            Files.write(Path.of("target/patente-diseno-prueba.pdf"),bytes);
        }
    }

    @Test void textosLargosContinuanSinPerderDatosNiInvadirPie() throws Exception {
        StringBuilder datos=new StringBuilder(DATOS);
        for(int i=0;i<100;i++) datos.append("\nCampo conservado ").append(i).append(": ")
            .append("Texto extenso de prueba ".repeat(7)).append("FIN_").append(i);
        datos.append("\nCampo sin espacios: ").append("Z".repeat(3500)).append("FIN_LARGO");
        try(var doc=Loader.loadPDF(pdf.generar("PRUEBA-42",datos.toString(),ID))) {
            assertThat(doc.getNumberOfPages()).isGreaterThan(2);
            String texto=new PDFTextStripper().getText(doc);
            for(int i=0;i<100;i++) assertThat(texto).contains("FIN_"+i);
            assertThat(texto).contains("FIN_LARGO");
            var limites=new PDFTextStripper() {
                @Override protected void processTextPosition(TextPosition p) {
                    assertThat(p.getXDirAdj()).isBetween(30f,562f);
                    assertThat(p.getYDirAdj()).isBetween(20f,815f);
                    super.processTextPosition(p);
                }
            };
            limites.getText(doc);
        }
    }

    @Test void urlConfiguradaYRecursosAutorizados() {
        assertThat(pdf.urlVerificacion(ID)).isEqualTo("https://registro.example.test/sistema/verificar-patente.html#"+ID);
        for(String base:List.of("","localhost:8080","javascript:alert(1)","https://user:pass@example.test","https://example.test?dato=x"))
            assertThatThrownBy(()->new PatentePdf(base,"","").generar("42",DATOS,ID)).isInstanceOf(ReglaDeNegocioException.class);
    }
}
