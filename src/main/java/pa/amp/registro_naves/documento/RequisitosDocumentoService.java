package pa.amp.registro_naves.documento;

import org.springframework.stereotype.Service;
import pa.amp.registro_naves.common.ReglaDeNegocioException;
import pa.amp.registro_naves.nave.Nave;
import pa.amp.registro_naves.nave.TipoNave;
import pa.amp.registro_naves.nave.TipoServicio;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** HU-13, D6 aprobada por la PO. Unica tabla y evaluacion para API y transiciones. */
@Service
public class RequisitosDocumentoService {
    public enum Aplicabilidad { OBLIGATORIO, NO_APLICA, REQUIERE_REVISION }
    public record Requisito(TipoDocumento tipo, Aplicabilidad estado, String explicacion) {}
    private enum Medida { ESLORA, ARQUEO_CARGA }
    private record Regla(TipoDocumento tipo, Medida medida, BigDecimal umbral, String unidad) {}

    // Umbrales inclusivos. GT representa arqueo bruto, nunca peso.
    private static final List<Regla> REGLAS = List.of(
        new Regla(TipoDocumento.ITC, Medida.ESLORA, new BigDecimal("24"), "m de eslora"),
        new Regla(TipoDocumento.SMC, Medida.ARQUEO_CARGA, new BigDecimal("500"), "GT de arqueo bruto")
    );

    public List<Requisito> evaluar(Nave nave) {
        List<Requisito> resultado = new ArrayList<>();
        resultado.add(new Requisito(TipoDocumento.CERTIFICADO_PROPIEDAD,
                Aplicabilidad.OBLIGATORIO, "El expediente exige certificado de propiedad."));
        resultado.add(new Requisito(TipoDocumento.PODER_NOTARIAL,
                Aplicabilidad.OBLIGATORIO, "El expediente exige poder notarial."));
        for (Regla regla : REGLAS) resultado.add(evaluar(regla, nave));
        return List.copyOf(resultado);
    }

    private Requisito evaluar(Regla regla, Nave nave) {
        if (nave.getServicio() != TipoServicio.INTERNACIONAL) {
            return pendiente(regla, "Navegación nacional, mixta o sin confirmar: D6 solo cubre servicio INTERNACIONAL.");
        }
        if (nave.getTipo() != TipoNave.CARGA && nave.getTipo() != TipoNave.PASAJEROS) {
            return pendiente(regla, "Tipo de nave mixto, no clasificado o ausente: requiere confirmar los requisitos.");
        }
        if (regla.medida() == Medida.ARQUEO_CARGA && nave.getTipo() == TipoNave.PASAJEROS) {
            return new Requisito(regla.tipo(), Aplicabilidad.OBLIGATORIO,
                    "Pasajeros en navegación internacional: SMC obligatorio, independientemente del GT.");
        }
        BigDecimal valor = regla.medida() == Medida.ESLORA ? nave.getEslora() : nave.getTonelajeBruto();
        if (valor == null || valor.signum() <= 0) {
            return pendiente(regla, "Falta un dato válido de "
                    + (regla.medida() == Medida.ESLORA ? "eslora." : "arqueo bruto (GT)."));
        }
        boolean obligatorio = valor.compareTo(regla.umbral()) >= 0;
        return new Requisito(regla.tipo(), obligatorio ? Aplicabilidad.OBLIGATORIO : Aplicabilidad.NO_APLICA,
                "Navegación internacional: " + (obligatorio ? "alcanza" : "no alcanza")
                + " el umbral de " + regla.umbral().toPlainString() + " " + regla.unidad() + ".");
    }

    private Requisito pendiente(Regla regla, String motivo) {
        return new Requisito(regla.tipo(), Aplicabilidad.REQUIERE_REVISION, motivo);
    }

    /** Una evaluacion pendiente bloquea incluso si se cargaron todos los PDF. */
    public List<TipoDocumento> obligatoriosConfirmados(Nave nave) {
        List<Requisito> evaluacion = evaluar(nave);
        String pendientes = evaluacion.stream()
                .filter(r -> r.estado() == Aplicabilidad.REQUIERE_REVISION)
                .map(r -> r.tipo() + ": " + r.explicacion()).collect(Collectors.joining(" "));
        if (!pendientes.isEmpty()) {
            throw new ReglaDeNegocioException("Requisitos documentales pendientes (REQUIERE_REVISION). "
                    + pendientes + " No se puede avanzar hasta confirmar los requisitos; "
                    + "la resolución manual de excepciones aún no está definida.");
        }
        return evaluacion.stream().filter(r -> r.estado() == Aplicabilidad.OBLIGATORIO)
                .map(Requisito::tipo).toList();
    }
}
