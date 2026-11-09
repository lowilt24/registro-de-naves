package pa.amp.registro_naves.constancia;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;
import pa.amp.registro_naves.common.ReglaDeNegocioException;
import pa.amp.registro_naves.nave.Nave;
import pa.amp.registro_naves.persona.*;
import pa.amp.registro_naves.tramite.HistorialEstado;
import java.awt.Color;
import java.io.*;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Maquetacion paginada, con texto seleccionable y fuente Unicode embebida. */
@Component
public class ConstanciaPdf {
    private static final Color PETROLEO = new Color(16, 59, 73);
    private static final Color TURQUESA = new Color(8, 127, 140);
    private static final float MARGEN = 48, TAMANO = 11, INTERLINEA = 17;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss XXX")
            .withZone(ZoneId.of("America/Panama"));

    public byte[] generar(Nave nave, List<NavePropietario> propietarios,
            AgenteResidente agente, HistorialEstado aprobacion) {
        try (PDDocument documento = new PDDocument();
             InputStream fuente = PDDocument.class.getResourceAsStream(
                     "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf");
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            if (fuente == null) throw new IOException("Falta la fuente del PDF");
            documento.getDocumentInformation().setTitle("Constancia de registro de la nave " + nave.getId());
            documento.getDocumentInformation().setSubject("Constancia digital del trámite de registro, sin firma digital");
            PDType0Font font = PDType0Font.load(documento, fuente);
            try (Pagina pagina = new Pagina(documento, font, nave.getId())) {
                pagina.seccion("Datos de la nave");
                pagina.linea("Número de registro: " + nave.getId());
                pagina.linea("Nombre: " + nave.getNombre());
                pagina.linea("Tipo: " + nave.getTipo());
                pagina.linea("Arqueo bruto: " + numero(nave.getTonelajeBruto()) + " GT");
                pagina.linea("Tonelaje neto: " + numero(nave.getTonelajeNeto()) + " TRN");
                pagina.linea("Eslora: " + numero(nave.getEslora()) + " m");
                pagina.linea("Manga: " + numero(nave.getManga()) + " m");
                pagina.linea("Puntal: " + numero(nave.getPuntal()) + " m");
                pagina.seccion("Propietarios y participación");
                for (NavePropietario propietario : propietarios) {
                    pagina.linea(propietario.getPropietario().getNombre() + " - "
                            + numero(propietario.getPorcentaje()) + " %");
                }
                pagina.seccion("Agente residente vigente");
                pagina.linea("Nombre: " + agente.getNombre());
                if (agente.getIdoneidad() != null && !agente.getIdoneidad().isBlank()) {
                    pagina.linea("Idoneidad: " + agente.getIdoneidad());
                }
                pagina.seccion("Aprobación del trámite de registro");
                pagina.linea("Estado del trámite: Aprobada. Nave registrada.");
                pagina.linea("Fecha de aprobación: " + FECHA.format(aprobacion.getFecha()) + " (America/Panama)");
                pagina.linea("Funcionario que aprobó: " + aprobacion.getUsuario().getNombreCompleto());
                pagina.seccion("Alcance de esta constancia");
                pagina.linea("Constancia digital del trámite de registro. No constituye patente de navegación "
                        + "ni certificación oficial. Sin firma digital.");
            }
            documento.save(salida);
            return salida.toByteArray();
        } catch (IllegalArgumentException e) {
            // Nunca sustituir silenciosamente letras de nombres que la fuente no representa.
            throw new ReglaDeNegocioException("El expediente contiene caracteres que no se pueden representar "
                    + "en la constancia. Revise los datos antes de emitirla.");
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar la constancia PDF", e);
        }
    }

    private static String numero(BigDecimal valor) { return valor.toPlainString(); }

    private static final class Pagina implements AutoCloseable {
        private final PDDocument documento;
        private final PDType0Font fuente;
        private final Long naveId;
        private PDPageContentStream contenido;
        private float y;

        Pagina(PDDocument documento, PDType0Font fuente, Long naveId) throws IOException {
            this.documento = documento; this.fuente = fuente; this.naveId = naveId;
            nueva();
        }
        private void nueva() throws IOException {
            if (contenido != null) contenido.close();
            PDPage pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);
            contenido = new PDPageContentStream(documento, pagina);
            contenido.setNonStrokingColor(PETROLEO);
            contenido.addRect(0, pagina.getMediaBox().getHeight() - 96, pagina.getMediaBox().getWidth(), 96);
            contenido.fill();
            texto("Constancia de registro de la nave", 19, MARGEN, pagina.getMediaBox().getHeight() - 43, Color.WHITE);
            texto("Registro de Naves | Número " + naveId, 11, MARGEN, pagina.getMediaBox().getHeight() - 67, Color.WHITE);
            texto("Constancia de registro | Sin firma digital | Página " + documento.getNumberOfPages(),
                    9, MARGEN, 28, PETROLEO);
            y = pagina.getMediaBox().getHeight() - 124;
        }
        void seccion(String titulo) throws IOException {
            if (y < 112) nueva();
            y -= 9;
            texto(titulo, 13, MARGEN, y, TURQUESA);
            y -= 24;
        }
        void linea(String valor) throws IOException {
            // Evita operadores de control; el contenido nunca se interpreta como HTML o PDF crudo.
            String limpio = valor.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
            float ancho = PDRectangle.A4.getWidth() - 2 * MARGEN;
            StringBuilder linea = new StringBuilder();
            // Corte por puntos Unicode: tambien admite nombres largos sin espacios.
            for (int i = 0; i < limpio.length();) {
                int cp = limpio.codePointAt(i);
                String siguiente = new String(Character.toChars(cp));
                if (!linea.isEmpty() && fuente.getStringWidth(linea + siguiente) * TAMANO / 1000 > ancho) {
                    escribir(linea.toString());
                    linea.setLength(0);
                }
                if (!linea.isEmpty() || !siguiente.isBlank()) linea.append(siguiente);
                i += Character.charCount(cp);
            }
            if (!linea.isEmpty()) escribir(linea.toString());
        }
        private void escribir(String valor) throws IOException {
            if (y < 62) nueva();
            texto(valor, TAMANO, MARGEN, y, PETROLEO);
            y -= INTERLINEA;
        }
        private void texto(String valor, float tamano, float x, float altura, Color color) throws IOException {
            contenido.beginText();
            contenido.setFont(fuente, tamano);
            contenido.setNonStrokingColor(color);
            contenido.newLineAtOffset(x, altura);
            contenido.showText(valor);
            contenido.endText();
        }
        @Override public void close() throws IOException { if (contenido != null) contenido.close(); }
    }
}
