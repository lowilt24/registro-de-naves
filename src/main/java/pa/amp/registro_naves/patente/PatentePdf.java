package pa.amp.registro_naves.patente;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;
import pa.amp.registro_naves.common.ReglaDeNegocioException;
import java.awt.Color;
import java.io.*;

/** PDF academico creado exclusivamente a partir de la instantanea de emision. */
@Component
public class PatentePdf {
    public byte[] generar(String numero,String instantanea) {
        try(var doc=new PDDocument();var fuente=PDDocument.class.getResourceAsStream(
                "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf");var salida=new ByteArrayOutputStream()) {
            if(fuente==null) throw new IOException("Fuente PDF no disponible");
            doc.getDocumentInformation().setTitle("Patente provisional simulada "+numero);
            doc.getDocumentInformation().setSubject("Simulación académica — sin validez oficial");
            var font=PDType0Font.load(doc,fuente);
            try(var pagina=new Pagina(doc,font,numero)) {
                for(String linea:instantanea.split("\n",-1)) pagina.linea(linea);
            }
            doc.save(salida);return salida.toByteArray();
        } catch(IllegalArgumentException e) {
            throw new ReglaDeNegocioException("Hay caracteres no representables en el PDF. La emisión se conserva y puede reintentarse.");
        } catch(IOException e) { throw new UncheckedIOException("No se pudo generar el PDF; la emisión se conserva.",e); }
    }
    private static final class Pagina implements AutoCloseable {
        private final PDDocument doc;
        private final PDType0Font font;
        private final String numero;
        private PDPageContentStream contenido;
        private float y;
        Pagina(PDDocument doc,PDType0Font font,String numero)throws IOException {
            this.doc=doc;this.font=font;this.numero=numero;nueva();
        }
        private void nueva()throws IOException {
            if(contenido!=null)contenido.close();
            var hoja=new PDPage(PDRectangle.A4);doc.addPage(hoja);contenido=new PDPageContentStream(doc,hoja);
            contenido.setNonStrokingColor(new Color(16,59,73));contenido.addRect(0,742,596,100);contenido.fill();
            texto("Patente provisional simulada",19,48,805,Color.WHITE);
            texto(numero,12,48,780,Color.WHITE);
            texto("Simulación académica de patente provisional — sin validez oficial",10,48,720,new Color(16,59,73));
            texto("Sin firma oficial | Sin vencimiento definido | Página "+doc.getNumberOfPages(),9,48,27,new Color(16,59,73));
            y=690;
        }
        void linea(String valor)throws IOException {
            String limpio=valor.replaceAll("[\\p{Cntrl}\\s]+"," ").trim();
            if(limpio.isEmpty()){y-=9;return;}
            StringBuilder fila=new StringBuilder();
            for(int i=0;i<limpio.length();) {
                int cp=limpio.codePointAt(i);String letra=new String(Character.toChars(cp));
                if(!fila.isEmpty()&&font.getStringWidth(fila+letra)*11/1000>499){escribir(fila.toString());fila.setLength(0);}
                if(!fila.isEmpty()||!letra.isBlank())fila.append(letra);
                i+=Character.charCount(cp);
            }
            if(!fila.isEmpty())escribir(fila.toString());
        }
        private void escribir(String valor)throws IOException {
            if(y<65)nueva();texto(valor,11,48,y,new Color(32,55,67));y-=17;
        }
        private void texto(String valor,float tamano,float x,float altura,Color color)throws IOException {
            contenido.beginText();contenido.setFont(font,tamano);contenido.setNonStrokingColor(color);
            contenido.newLineAtOffset(x,altura);contenido.showText(valor);contenido.endText();
        }
        public void close()throws IOException {if(contenido!=null)contenido.close();}
    }
}
