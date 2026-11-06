package pa.amp.registro_naves.documento;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import pa.amp.registro_naves.nave.*;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static pa.amp.registro_naves.documento.RequisitosDocumentoService.Aplicabilidad.*;

class RequisitosDocumentoServiceTest {
    final RequisitosDocumentoService servicio = new RequisitosDocumentoService();

    Nave nave() {
        Nave n = new Nave();
        n.setTipo(TipoNave.CARGA);
        n.setServicio(TipoServicio.INTERNACIONAL);
        n.setEslora(new BigDecimal("24"));
        n.setTonelajeBruto(new BigDecimal("500"));
        return n;
    }

    RequisitosDocumentoService.Aplicabilidad estado(Nave n, TipoDocumento tipo) {
        return servicio.evaluar(n).stream().filter(r -> r.tipo() == tipo).findFirst().orElseThrow().estado();
    }

    @ParameterizedTest @CsvSource({"23.99,NO_APLICA", "24,OBLIGATORIO", "24.01,OBLIGATORIO"})
    void umbralItcInclusivo(String eslora, RequisitosDocumentoService.Aplicabilidad esperado) {
        Nave n = nave(); n.setEslora(new BigDecimal(eslora));
        assertThat(estado(n, TipoDocumento.ITC)).isEqualTo(esperado);
    }

    @ParameterizedTest @CsvSource({"499,NO_APLICA", "500,OBLIGATORIO", "501,OBLIGATORIO"})
    void umbralSmcCargaInclusivo(String gt, RequisitosDocumentoService.Aplicabilidad esperado) {
        Nave n = nave(); n.setTonelajeBruto(new BigDecimal(gt));
        assertThat(estado(n, TipoDocumento.SMC)).isEqualTo(esperado);
    }

    @Test void pasajerosNoDependenDeGtNiEsloraParaSmc() {
        Nave n = nave(); n.setTipo(TipoNave.PASAJEROS); n.setTonelajeBruto(new BigDecimal("100"));
        assertThat(estado(n, TipoDocumento.SMC)).isEqualTo(OBLIGATORIO);
        n.setTonelajeBruto(null); n.setEslora(null);
        assertThat(estado(n, TipoDocumento.SMC)).isEqualTo(OBLIGATORIO);
        assertThat(estado(n, TipoDocumento.ITC)).isEqualTo(REQUIERE_REVISION);
    }

    @ParameterizedTest @EnumSource(value=TipoNave.class, names={"MIXTA","TANQUERO","REMOLCADOR","PESQUERA"})
    void noInventaClasificaciones(TipoNave tipo) {
        Nave n = nave(); n.setTipo(tipo);
        assertThat(estado(n, TipoDocumento.ITC)).isEqualTo(REQUIERE_REVISION);
        assertThat(estado(n, TipoDocumento.SMC)).isEqualTo(REQUIERE_REVISION);
    }

    @ParameterizedTest @EnumSource(value=TipoServicio.class, names={"CABOTAJE","MIXTO"})
    void navegacionNoCubiertaNoEsNoAplica(TipoServicio servicioNave) {
        Nave n = nave(); n.setServicio(servicioNave);
        assertThat(estado(n, TipoDocumento.ITC)).isEqualTo(REQUIERE_REVISION);
        assertThat(estado(n, TipoDocumento.SMC)).isEqualTo(REQUIERE_REVISION);
    }

    @Test void faltaNavegacionOTipo() {
        Nave n = nave(); n.setServicio(null);
        assertThat(estado(n, TipoDocumento.ITC)).isEqualTo(REQUIERE_REVISION);
        assertThat(estado(n, TipoDocumento.SMC)).isEqualTo(REQUIERE_REVISION);
        n.setServicio(TipoServicio.INTERNACIONAL); n.setTipo(null);
        assertThat(estado(n, TipoDocumento.ITC)).isEqualTo(REQUIERE_REVISION);
        assertThat(estado(n, TipoDocumento.SMC)).isEqualTo(REQUIERE_REVISION);
    }

    @Test void datosAusentesAfectanSoloLaReglaQueLosNecesita() {
        Nave n = nave(); n.setEslora(null);
        assertThat(estado(n, TipoDocumento.ITC)).isEqualTo(REQUIERE_REVISION);
        assertThat(estado(n, TipoDocumento.SMC)).isEqualTo(OBLIGATORIO);
        n.setEslora(new BigDecimal("24")); n.setTonelajeBruto(null);
        assertThat(estado(n, TipoDocumento.ITC)).isEqualTo(OBLIGATORIO);
        assertThat(estado(n, TipoDocumento.SMC)).isEqualTo(REQUIERE_REVISION);
    }

    @ParameterizedTest @CsvSource({"0", "-1"})
    void datosNoValidosNoSeConviertenEnExencion(String valor) {
        Nave n = nave(); n.setEslora(new BigDecimal(valor)); n.setTonelajeBruto(new BigDecimal(valor));
        assertThat(estado(n, TipoDocumento.ITC)).isEqualTo(REQUIERE_REVISION);
        assertThat(estado(n, TipoDocumento.SMC)).isEqualTo(REQUIERE_REVISION);
    }

    @Test void itcObligatorioYSmcNoAplicaMismaNave() {
        Nave n = nave(); n.setTonelajeBruto(new BigDecimal("499"));
        assertThat(servicio.obligatoriosConfirmados(n)).containsExactly(
                TipoDocumento.CERTIFICADO_PROPIEDAD, TipoDocumento.PODER_NOTARIAL, TipoDocumento.ITC);
        assertThat(estado(n, TipoDocumento.SMC)).isEqualTo(NO_APLICA);
        assertThat(servicio.evaluar(n)).allSatisfy(r -> assertThat(r.explicacion()).isNotBlank());
    }

    @Test void pendienteBloqueaConMensajeDeNegocio() {
        Nave n = nave(); n.setServicio(null);
        assertThatThrownBy(() -> servicio.obligatoriosConfirmados(n))
            .isInstanceOf(pa.amp.registro_naves.common.ReglaDeNegocioException.class)
            .hasMessageContaining("REQUIERE_REVISION").hasMessageContaining("ITC").hasMessageContaining("SMC");
    }
}
