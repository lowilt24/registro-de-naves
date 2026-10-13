package pa.amp.registro_naves.tramite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static pa.amp.registro_naves.tramite.AccionTramite.*;
import static pa.amp.registro_naves.tramite.EstadoTramite.*;

/**
 * Prueba unitaria pura de la maquina de estados (D2). Sin Spring ni base:
 * corre en milisegundos.
 *
 * Recorre las 6 x 8 combinaciones de estado y accion: las 7 validas deben
 * llevar a su destino y las 41 restantes no deben tener destino, incluidas
 * todas las que salen de un estado final.
 */
class EstadoTramiteTest {

    /** Las siete transiciones del diagrama aprobado por la PO. */
    static final Map<String, EstadoTramite> VALIDAS = Map.of(
            clave(SOLICITUD_PRESENTADA, ADMITIR), EN_REVISION,
            clave(SOLICITUD_PRESENTADA, DESISTIR), DESISTIDA,
            clave(EN_REVISION, APROBAR), APROBADA,
            clave(EN_REVISION, RECHAZAR), RECHAZADA,
            clave(EN_REVISION, OBSERVAR), OBSERVADA,
            clave(OBSERVADA, SUBSANAR), EN_REVISION,
            clave(OBSERVADA, DESISTIR), DESISTIDA);

    static String clave(EstadoTramite estado, AccionTramite accion) {
        return estado + "+" + accion;
    }

    static Stream<Arguments> validas() {
        List<Arguments> casos = new ArrayList<>();
        for (EstadoTramite e : EstadoTramite.values()) {
            for (AccionTramite a : AccionTramite.values()) {
                EstadoTramite destino = VALIDAS.get(clave(e, a));
                if (destino != null) {
                    casos.add(Arguments.of(e, a, destino));
                }
            }
        }
        return casos.stream();
    }

    static Stream<Arguments> invalidas() {
        List<Arguments> casos = new ArrayList<>();
        for (EstadoTramite e : EstadoTramite.values()) {
            for (AccionTramite a : AccionTramite.values()) {
                if (!VALIDAS.containsKey(clave(e, a))) {
                    casos.add(Arguments.of(e, a));
                }
            }
        }
        return casos.stream();
    }

    @ParameterizedTest(name = "{0} --{1}--> {2}")
    @MethodSource("validas")
    void transicionValidaLlegaASuDestino(EstadoTramite desde, AccionTramite accion, EstadoTramite hacia) {
        assertThat(desde.destino(accion)).contains(hacia);
    }

    @ParameterizedTest(name = "{0} --{1}--> (ninguno)")
    @MethodSource("invalidas")
    void transicionInvalidaNoTieneDestino(EstadoTramite desde, AccionTramite accion) {
        assertThat(desde.destino(accion)).isEmpty();
    }

    @Test
    void haySieteValidasYCuarentaYUnaInvalidas() {
        assertThat(validas().count()).isEqualTo(7);
        assertThat(invalidas().count()).isEqualTo(6 * 8 - 7);
    }

    @Test
    void losEstadosFinalesNoTienenSalida() {
        for (EstadoTramite e : List.of(APROBADA, RECHAZADA, DESISTIDA)) {
            assertThat(e.esFinal()).as(e.name()).isTrue();
            assertThat(e.accionesPosibles()).as(e.name()).isEmpty();
        }
        for (EstadoTramite e : List.of(SOLICITUD_PRESENTADA, EN_REVISION, OBSERVADA)) {
            assertThat(e.esFinal()).as(e.name()).isFalse();
            assertThat(e.accionesPosibles()).as(e.name()).isNotEmpty();
        }
    }

    @Test
    void lasAccionesDelSistemaNuncaSonTransiciones() {
        for (EstadoTramite e : EstadoTramite.values()) {
            assertThat(e.destino(PRESENTAR)).isEmpty();
            assertThat(e.destino(MIGRACION)).isEmpty();
        }
        assertThat(PRESENTAR.esManual()).isFalse();
        assertThat(MIGRACION.esManual()).isFalse();
    }

    @Test
    void sinAccionNoHayDestino() {
        assertThat(EN_REVISION.destino(null)).isEmpty();
    }

    @Test
    void elTramiteNaceEnSolicitudPresentada() {
        assertThat(EstadoTramite.INICIAL).isEqualTo(SOLICITUD_PRESENTADA);
    }

    @Test
    void soloObservarYRechazarExigenObservacion() {
        for (AccionTramite a : AccionTramite.values()) {
            assertThat(a.exigeObservacion()).as(a.name())
                    .isEqualTo(a == OBSERVAR || a == RECHAZAR);
        }
    }
}
