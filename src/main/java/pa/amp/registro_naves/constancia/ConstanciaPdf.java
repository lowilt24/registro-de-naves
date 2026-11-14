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
    private static final float MARGEN = 36, TAMANO = 10, INTERLINEA = 13;
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
            texto("REGISTRO DE NAVES", 22, MARGEN, 805, Color.WHITE);
            texto("Sistema de gestión marítima", 12, MARGEN, 784, Color.WHITE);
            contenido.setNonStrokingColor(TURQUESA);
            contenido.addRect(0, 742, 596, 5); contenido.fill();
            texto("Documento generado por Registro de Naves | Página " + documento.getNumberOfPages(),
                    8, MARGEN, 28, PETROLEO);
            y = 715;
            if (documento.getNumberOfPages() == 1) {
                texto("Constancia de registro de nave", 24, MARGEN, y, PETROLEO); y -= 24;
                texto("El registro se ha guardado correctamente en el sistema.", 11, MARGEN, y, PETROLEO); y -= 40;
                contenido.setNonStrokingColor(new Color(211, 241, 235));
                contenido.addRect(MARGEN, y - 13, 523, 35); contenido.fill();
                texto("Registro completado", 18, MARGEN + 14, y, TURQUESA); y -= 33;
            } else {
                texto("Constancia de registro de nave | Número " + naveId, 14, MARGEN, y, PETROLEO); y -= 24;
            }
        }
        void seccion(String titulo) throws IOException {
            if (y < 125) nueva();
            y -= 8;
            contenido.setNonStrokingColor(PETROLEO);
            contenido.addRect(MARGEN, y - 22, 523, 22); contenido.fill();
            contenido.setNonStrokingColor(TURQUESA);
            contenido.addRect(MARGEN, y - 22, 5, 22); contenido.fill();
            texto(titulo, 12, MARGEN + 12, y - 15, Color.WHITE);
            y -= 28;
        }
        void linea(String valor) throws IOException {
            String limpio = valor.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
            int separador = limpio.indexOf(':');
            String etiqueta = separador < 0 ? "Detalle" : limpio.substring(0, separador + 1);
            String dato = separador < 0 ? limpio : limpio.substring(separador + 1).trim();
            var etiquetas = envolver(etiqueta, 155);
            var datos = envolver(dato, 336);
            int total = Math.max(etiquetas.size(), datos.size()), desde = 0;
            float altoFila = total * INTERLINEA + 10;
            if (altoFila <= 610 && y - altoFila < 65) nueva();
            while (desde < total) {
                if (y < 90) nueva();
                int cantidad = Math.min(total - desde, Math.max(1, (int)((y - 65 - 10) / INTERLINEA)));
                float alto = cantidad * INTERLINEA + 10;
                contenido.setNonStrokingColor(new Color(225, 240, 245));
                contenido.addRect(MARGEN, y - alto, 171, alto); contenido.fill();
                contenido.setStrokingColor(new Color(190, 214, 225)); contenido.setLineWidth(.5f);
                contenido.addRect(MARGEN, y - alto, 523, alto);
                contenido.moveTo(MARGEN + 171, y); contenido.lineTo(MARGEN + 171, y - alto); contenido.stroke();
                for (int i = 0; i < cantidad; i++) {
                    int indice = desde + i; float base = y - 14 - i * INTERLINEA;
                    if (indice < etiquetas.size()) texto(etiquetas.get(indice), TAMANO, MARGEN + 8, base, PETROLEO);
                    if (indice < datos.size()) texto(datos.get(indice), TAMANO, MARGEN + 179, base, PETROLEO);
                }
                y -= alto; desde += cantidad;
            }
        }
        private java.util.List<String> envolver(String valor, float ancho) throws IOException {
            var filas = new java.util.ArrayList<String>();
            StringBuilder fila = new StringBuilder();
            for (int i = 0; i < valor.length();) {
                int cp = valor.codePointAt(i); String letra = new String(Character.toChars(cp));
                if (!fila.isEmpty() && fuente.getStringWidth(fila + letra) * TAMANO / 1000 > ancho) {
                    int espacio = fila.lastIndexOf(" ");
                    if (espacio > 0) {
                        filas.add(fila.substring(0, espacio)); fila.delete(0, espacio + 1);
                    } else {
                        filas.add(fila.toString()); fila.setLength(0);
                    }
                }
                if (!fila.isEmpty() || !letra.isBlank()) fila.append(letra);
                i += Character.charCount(cp);
            }
            if (!fila.isEmpty()) filas.add(fila.toString());
            if (filas.isEmpty()) filas.add("");
            return filas;
        }
        private void texto(String valor, float tamano, float x, float altura, Color color) throws IOException {
            contenido.beginText(); contenido.setFont(fuente, tamano); contenido.setNonStrokingColor(color);
            contenido.newLineAtOffset(x, altura); contenido.showText(valor); contenido.endText();
        }
        @Override public void close() throws IOException { if (contenido != null) contenido.close(); }
    }
}
