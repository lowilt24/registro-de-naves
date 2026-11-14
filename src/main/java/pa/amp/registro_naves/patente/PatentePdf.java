package pa.amp.registro_naves.patente;

import com.google.zxing.*;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pa.amp.registro_naves.common.ReglaDeNegocioException;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.util.*;

/** Presentación de la instantánea inmutable; no calcula numeración ni vigencia. */
@Component
public class PatentePdf {
    private final String urlBase, logoRuta, firmaRuta;
    public PatentePdf(@Value("${navesitas.url-base:}") String urlBase,
                      @Value("${navesitas.patente.logo-ruta:}") String logoRuta,
                      @Value("${navesitas.patente.firma-ruta:}") String firmaRuta) {
        this.urlBase=urlBase; this.logoRuta=logoRuta; this.firmaRuta=firmaRuta;
    }
    public String urlVerificacion(UUID identificador) {
        try {
            URI base=URI.create(urlBase.strip());
            if(!("http".equals(base.getScheme()) || "https".equals(base.getScheme())) || base.getHost()==null
                    || base.getRawQuery()!=null || base.getRawFragment()!=null || base.getUserInfo()!=null)
                throw new IllegalArgumentException();
            return base.toString().replaceAll("/+$","")+"/verificar-patente.html#"+identificador;
        } catch(IllegalArgumentException e) {
            throw new ReglaDeNegocioException("Configure navesitas.url-base con la URL pública del sistema para generar la verificación.");
        }
    }
    public byte[] generar(String numero,String instantanea,UUID identificador) {
        String url=urlVerificacion(identificador);
        try(var doc=new PDDocument();var fuente=PDDocument.class.getResourceAsStream(
                "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf");
                var fuenteNegrita=PDDocument.class.getResourceAsStream(
                "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf");
                var salida=new ByteArrayOutputStream()) {
            if(fuente==null || fuenteNegrita==null) throw new IOException("Fuente PDF no disponible");
            doc.getDocumentInformation().setTitle("PATENTE PROVISIONAL DE NAVEGACIÓN — "+numero);
            doc.getDocumentInformation().setSubject("Modelo académico — sin validez oficial. Sin firma digital criptográfica.");
            var font=PDType0Font.load(doc,fuente);var bold=PDType0Font.load(doc,fuenteNegrita);
            try(var pagina=new Pagina(doc,font,bold,numero,logoRuta)) {
                List<String> nave=new ArrayList<>(),titulares=new ArrayList<>(),emision=new ArrayList<>();
                boolean titular=false;
                String funcionario="";
                for(String linea:instantanea.split("\n",-1)) {
                    if(linea.isBlank()) continue;
                    if(linea.startsWith("Funcionario: ")) funcionario=linea.substring(13);
                    if(linea.startsWith("Propietarios:")) titular=true;
                    if(linea.startsWith("Simulación académica") || linea.startsWith("Número:")
                            || linea.startsWith("Solicitud:") || linea.startsWith("Fecha de emisión:")
                            || linea.startsWith("Funcionario:") || linea.startsWith("Acto de aprobación")
                            || linea.startsWith("Envío aprobado:") || linea.startsWith("Sin firma oficial.")) emision.add(linea);
                    else if(titular) titulares.add(linea);
                    else nave.add(linea);
                }
                pagina.seccion("01. Datos de la nave");
                for(String linea:nave) pagina.fila(linea);
                pagina.seccion("02. Titular y representante");
                for(String linea:titulares) pagina.fila(linea);
                pagina.seccion("03. Emisión y vigencia");
                for(String linea:emision) pagina.fila(linea);
                pagina.firmaYQr(funcionario,firmaRuta,url);
            }
            // Segunda pasada: total de páginas real, también en documentos largos.
            for(int i=0;i<doc.getNumberOfPages();i++) {
                try(var c=new PDPageContentStream(doc,doc.getPage(i),PDPageContentStream.AppendMode.APPEND,true,true)) {
                    Pagina.texto(c,font,"Página "+(i+1)+" de "+doc.getNumberOfPages(),8,470,31,Pagina.MARINO);
                }
            }
            doc.save(salida);return salida.toByteArray();
        } catch(IllegalArgumentException e) {
            throw new ReglaDeNegocioException("Hay caracteres no representables en el PDF. La emisión se conserva y puede reintentarse.");
        } catch(WriterException e) {
            throw new ReglaDeNegocioException("No se pudo generar el QR. La emisión se conserva y puede reintentarse.");
        } catch(IOException e) { throw new UncheckedIOException("No se pudo generar el PDF; la emisión se conserva.",e); }
    }
    private static final class Pagina implements AutoCloseable {
        static final Color MARINO=new Color(10,48,70), TURQUESA=new Color(0,145,158),
            CLARO=new Color(225,241,245), BORDE=new Color(180,207,218), TINTA=new Color(30,45,54);
        final PDDocument doc;final PDType0Font font,bold;final String numero,logoRuta;
        PDPageContentStream c;float y;String seccionActual="";
        static final float X=34,W=527,LABEL=160,TAM=9.2f,LINEA=10.5f;
        Pagina(PDDocument doc,PDType0Font font,PDType0Font bold,String numero,String logoRuta)throws IOException {
            this.doc=doc;this.font=font;this.bold=bold;this.numero=numero;this.logoRuta=logoRuta;nueva();
        }
        void nueva()throws IOException {
            if(c!=null)c.close();
            var hoja=new PDPage(PDRectangle.A4);doc.addPage(hoja);c=new PDPageContentStream(doc,hoja);
            rect(0,758,596,84,MARINO);
            c.setNonStrokingColor(TURQUESA);c.moveTo(0,765);c.curveTo(190,725,400,795,596,756);
            c.lineTo(596,748);c.curveTo(390,785,190,718,0,757);c.closePath();c.fill();
            float marcaX=34;
            if(!logoRuta.isBlank()) { imagen(logoRuta,34,779,46,46);marcaX=94; }
            textoNegrita(c,bold,"REGISTRO DE NAVES",22,marcaX,808,Color.WHITE);
            texto(c,font,"Sistema de gestión marítima",13,marcaX,786,Color.WHITE);
            textoNegrita(c,bold,"PATENTE PROVISIONAL DE NAVEGACIÓN",19,X,717,MARINO);
            rect(X,680,W,23,CLARO);
            textoNegrita(c,bold,"MODELO ACADÉMICO · SIN VALIDEZ OFICIAL",12,113,687,MARINO);
            // Pie y ola discretos, fuera del área de contenido.
            rect(X,48,W,0.7f,BORDE);
            texto(c,font,"Sin validez oficial · "+numero,8,X,31,MARINO);
            c.setNonStrokingColor(TURQUESA);c.moveTo(0,0);c.lineTo(0,10);
            c.curveTo(180,26,370,-8,596,14);c.lineTo(596,0);c.closePath();c.fill();
            y=667;
        }
        void seccion(String titulo)throws IOException {
            if(y<130)nueva();seccionActual=titulo;
            y-=4;rect(X,y-20,W,20,MARINO);rect(X,y-20,5,20,TURQUESA);
            textoNegrita(c,bold,titulo,11,X+12,y-14,Color.WHITE);y-=24;
        }
        void fila(String original)throws IOException {
            String limpio=original.replaceAll("[\\p{Cntrl}]"," ").strip();
            int separador=limpio.indexOf(':');
            String etiqueta=separador>=0?limpio.substring(0,separador+1):"Detalle";
            String valor=separador>=0?limpio.substring(separador+1).strip():limpio;
            var izquierda=envolver(etiqueta,bold,TAM,LABEL-16);
            var derecha=envolver(valor,font,TAM,W-LABEL-16);
            int total=Math.max(izquierda.size(),derecha.size()),desde=0;
            while(desde<total) {
                if(y<89) {nueva();seccion(seccionActual.replace(" (continuación)","")+" (continuación)");}
                int capacidad=Math.max(1,(int)((y-65-8)/LINEA));
                int cantidad=Math.min(total-desde,capacidad);float alto=cantidad*LINEA+8;
                rect(X,y-alto,LABEL,alto,CLARO);rect(X+LABEL,y-alto,W-LABEL,alto,Color.WHITE);
                c.setStrokingColor(BORDE);c.setLineWidth(.5f);c.addRect(X,y-alto,W,alto);
                c.moveTo(X+LABEL,y);c.lineTo(X+LABEL,y-alto);c.stroke();
                for(int i=0;i<cantidad;i++) {
                    int indice=desde+i;float base=y-12-i*LINEA;
                    if(indice<izquierda.size())textoNegrita(c,bold,izquierda.get(indice),TAM,X+8,base,TINTA);
                    if(indice<derecha.size())texto(c,font,derecha.get(indice),TAM,X+LABEL+8,base,TINTA);
                }
                y-=alto;desde+=cantidad;
            }
        }
        void firmaYQr(String funcionario,String firmaRuta,String url)throws IOException,WriterException {
            var nombreLineas=envolver(funcionario,bold,9,285);
            float reserva=Math.max(160,132+nombreLineas.size()*12);
            if(y<65+reserva)nueva();y-=12;
            var m=new QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,300,300,
                Map.of(EncodeHintType.ERROR_CORRECTION,ErrorCorrectionLevel.M,EncodeHintType.MARGIN,4,
                    EncodeHintType.CHARACTER_SET,"UTF-8"));
            var qr=new BufferedImage(m.getWidth(),m.getHeight(),BufferedImage.TYPE_INT_RGB);
            for(int j=0;j<m.getHeight();j++)for(int i=0;i<m.getWidth();i++)qr.setRGB(i,j,m.get(i,j)?0xff000000:0xffffffff);
            c.drawImage(LosslessFactory.createFromImage(doc,qr),441,y-102,100,100);
            texto(c,font,"Verificar autenticidad",9,444,y-116,MARINO);
            texto(c,font,"y vigencia académica",9,442,y-128,MARINO);
            if(!firmaRuta.isBlank()) imagen(firmaRuta,65,y-69,220,48);
            else texto(c,font,"Firma pendiente",11,104,y-55,TINTA);
            rect(52,y-78,285,.7f,MARINO);
            float base=y-92;
            for(String linea:nombreLineas) {textoNegrita(c,bold,linea,9,52,base,MARINO);base-=12;}
            texto(c,font,"Funcionario DGMM · Responsable de la emisión",8,52,base,TINTA);base-=12;
            texto(c,font,"Sin firma digital criptográfica",8,52,base,TINTA);
            y=Math.min(y-145,base-16);
            for(String linea:envolver(url,font,7,W)) {if(y<65)nueva();texto(c,font,linea,7,X,y,TINTA);y-=10;}
        }
        void imagen(String ruta,float x,float base,float ancho,float alto)throws IOException {
            var img=PDImageXObject.createFromFile(ruta,doc);
            float escala=Math.min(ancho/img.getWidth(),alto/img.getHeight());
            c.drawImage(img,x,base,img.getWidth()*escala,img.getHeight()*escala);
        }
        static List<String> envolver(String valor,PDType0Font fuente,float tam,float ancho)throws IOException {
            List<String> filas=new ArrayList<>();String resto=valor;
            while(!resto.isEmpty()) {
                int fin=0,espacio=-1;
                for(int i=0;i<resto.length();) {
                    int proximo=i+Character.charCount(resto.codePointAt(i));
                    if(fuente.getStringWidth(resto.substring(0,proximo))*tam/1000>ancho)break;
                    fin=proximo;if(Character.isWhitespace(resto.codePointAt(i)))espacio=i;i=proximo;
                }
                if(fin==0)throw new IllegalArgumentException("Carácter demasiado ancho");
                if(fin<resto.length()&&espacio>0)fin=espacio;
                filas.add(resto.substring(0,fin).stripTrailing());resto=resto.substring(fin).stripLeading();
            }
            if(filas.isEmpty())filas.add("");return filas;
        }
        void rect(float x,float base,float ancho,float alto,Color color)throws IOException {
            c.setNonStrokingColor(color);c.addRect(x,base,ancho,alto);c.fill();
        }
        static void textoNegrita(PDPageContentStream c,PDType0Font fuente,String valor,float tam,float x,float base,Color color)throws IOException {
            c.setRenderingMode(org.apache.pdfbox.pdmodel.graphics.state.RenderingMode.FILL_STROKE);
            texto(c,fuente,valor,tam,x,base,color);
            c.setRenderingMode(org.apache.pdfbox.pdmodel.graphics.state.RenderingMode.FILL);
        }
        static void texto(PDPageContentStream c,PDType0Font fuente,String valor,float tam,float x,float base,Color color)throws IOException {
            c.setLineWidth(.22f);c.setStrokingColor(color);c.beginText();c.setFont(fuente,tam);c.setNonStrokingColor(color);c.newLineAtOffset(x,base);c.showText(valor);c.endText();
        }
        public void close()throws IOException {if(c!=null)c.close();}
    }
}
