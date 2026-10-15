package pa.amp.registro_naves.tramite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pa.amp.registro_naves.common.AccesoDenegadoException;
import pa.amp.registro_naves.nave.Nave;
import pa.amp.registro_naves.usuario.Rol;
import pa.amp.registro_naves.usuario.Usuario;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static pa.amp.registro_naves.tramite.AccionTramite.*;
import static pa.amp.registro_naves.tramite.EstadoTramite.*;

/**
 * Prueba unitaria pura de AutorizacionTramite: rol x accion x dueno (D1).
 * Sin Spring ni base; los usuarios y la nave son objetos sueltos.
 */
class AutorizacionTramiteTest {

    final AutorizacionTramite autorizacion = new AutorizacionTramite();

    final Usuario agenteDueno = usuario("dueno@navesitas.pa", Rol.AGENTE_NAVIERO);
    final Usuario otroAgente  = usuario("otro@navesitas.pa", Rol.AGENTE_NAVIERO);
    final Usuario dgmm        = usuario("revisor@navesitas.pa", Rol.FUNCIONARIO_DGMM);
    final Usuario auditor     = usuario("auditor@navesitas.pa", Rol.AUDITOR);
    final Usuario admin       = usuario("admin@navesitas.pa", Rol.ADMINISTRADOR);

    final Nave nave = naveDe(agenteDueno);

    static Usuario usuario(String correo, Rol rol) {
        Usuario u = new Usuario();
        u.setCorreo(correo);
        u.setRol(rol);
        return u;
    }

    static Nave naveDe(Usuario dueno) {
        Nave n = new Nave();
        n.setRegistradoPor(dueno);
        return n;
    }

    /** Quien tiene permiso para cada accion manual. */
    record Caso(String quien, AccionTramite accion, boolean permitido) {
        @Override public String toString() { return quien + " " + accion + " -> " + permitido; }
    }

    Usuario porNombre(String quien) {
        return switch (quien) {
            case "agenteDueno" -> agenteDueno;
            case "otroAgente" -> otroAgente;
            case "dgmm" -> dgmm;
            case "auditor" -> auditor;
            default -> admin;
        };
    }

    static Stream<Arguments> matriz() {
        List<Arguments> casos = new ArrayList<>();
        for (String quien : List.of("agenteDueno", "otroAgente", "dgmm", "auditor", "admin")) {
            for (AccionTramite a : AccionTramite.values()) {
                boolean permitido = switch (quien) {
                    case "agenteDueno" -> a == SUBSANAR || a == DESISTIR;
                    case "dgmm" -> a == ADMITIR || a == OBSERVAR || a == APROBAR || a == RECHAZAR;
                    default -> false;
                };
                casos.add(Arguments.of(new Caso(quien, a, permitido)));
            }
        }
        return casos.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matriz")
    void rolPorAccionPorDueno(Caso caso) {
        assertThat(autorizacion.puedeEjecutar(porNombre(caso.quien()), nave, caso.accion()))
                .isEqualTo(caso.permitido());
    }

    @Test
    void elDuenoSeReconoceSinImportarMayusculas() {
        Usuario mismoConMayusculas = usuario("DUENO@navesitas.pa", Rol.AGENTE_NAVIERO);
        assertThat(autorizacion.puedeEjecutar(mismoConMayusculas, nave, DESISTIR)).isTrue();
    }

    @Test
    void unaDgmmQueRegistroLaNaveNoSubsana() {
        // La relacion con la nave no le da acciones de agente a otro rol.
        Nave naveDeDgmm = naveDe(dgmm);
        assertThat(autorizacion.puedeEjecutar(dgmm, naveDeDgmm, SUBSANAR)).isFalse();
        assertThat(autorizacion.puedeEjecutar(dgmm, naveDeDgmm, ADMITIR)).isTrue();
    }

    @Test
    void lecturaPorRol() {
        assertThat(autorizacion.puedeLeer(agenteDueno, nave)).isTrue();
        assertThat(autorizacion.puedeLeer(dgmm, nave)).isTrue();
        assertThat(autorizacion.puedeLeer(auditor, nave)).isTrue();
        assertThat(autorizacion.puedeLeer(otroAgente, nave)).isFalse();
        assertThat(autorizacion.puedeLeer(admin, nave)).isFalse();
        assertThat(autorizacion.puedeLeer(null, nave)).isFalse();
        assertThat(autorizacion.puedeLeer(agenteDueno, null)).isFalse();
    }

    @Test
    void sinDuenoNadieEsAgenteDeLaNave() {
        Nave huerfana = new Nave();
        assertThat(autorizacion.puedeEjecutar(agenteDueno, huerfana, DESISTIR)).isFalse();
        assertThat(autorizacion.puedeLeer(agenteDueno, huerfana)).isFalse();
    }

    @Test
    void accionesPermitidasCruzanRolYEstado() {
        assertThat(autorizacion.accionesPermitidas(agenteDueno, nave, SOLICITUD_PRESENTADA))
                .containsExactly(DESISTIR);
        assertThat(autorizacion.accionesPermitidas(dgmm, nave, SOLICITUD_PRESENTADA))
                .containsExactly(ADMITIR);

        assertThat(autorizacion.accionesPermitidas(agenteDueno, nave, EN_REVISION)).isEmpty();
        assertThat(autorizacion.accionesPermitidas(dgmm, nave, EN_REVISION))
                .containsExactly(OBSERVAR, APROBAR, RECHAZAR);

        assertThat(autorizacion.accionesPermitidas(agenteDueno, nave, OBSERVADA))
                .containsExactly(SUBSANAR, DESISTIR);
        assertThat(autorizacion.accionesPermitidas(dgmm, nave, OBSERVADA)).isEmpty();

        for (EstadoTramite e : EstadoTramite.values()) {
            assertThat(autorizacion.accionesPermitidas(auditor, nave, e)).isEmpty();
            assertThat(autorizacion.accionesPermitidas(otroAgente, nave, e)).isEmpty();
        }
        for (EstadoTramite e : List.of(APROBADA, RECHAZADA, DESISTIDA)) {
            assertThat(autorizacion.accionesPermitidas(dgmm, nave, e)).isEmpty();
            assertThat(autorizacion.accionesPermitidas(agenteDueno, nave, e)).isEmpty();
        }
        assertThat(autorizacion.accionesPermitidas(dgmm, nave, null)).isEmpty();
    }

    @Test
    void exigirAccionExplicaQuienPuede() {
        assertThatThrownBy(() -> autorizacion.exigirAccion(agenteDueno, nave, APROBAR))
                .isInstanceOf(AccesoDenegadoException.class)
                .hasMessageContaining("DGMM");
        assertThatThrownBy(() -> autorizacion.exigirAccion(dgmm, nave, SUBSANAR))
                .isInstanceOf(AccesoDenegadoException.class)
                .hasMessageContaining("agente que registró la nave");
        // A quien no ve la nave no se le dice que accion le faltaria.
        assertThatThrownBy(() -> autorizacion.exigirAccion(otroAgente, nave, DESISTIR))
                .isInstanceOf(AccesoDenegadoException.class)
                .hasMessage("La nave indicada no le pertenece.");
        assertThatThrownBy(() -> autorizacion.exigirAccion(dgmm, nave, PRESENTAR))
                .isInstanceOf(AccesoDenegadoException.class)
                .hasMessageContaining("a mano");
        assertThatNoException().isThrownBy(() -> autorizacion.exigirAccion(dgmm, nave, ADMITIR));
    }

    @Test
    void exigirLecturaRechazaAOtroAgente() {
        assertThatThrownBy(() -> autorizacion.exigirLectura(otroAgente, nave))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThatNoException().isThrownBy(() -> autorizacion.exigirLectura(auditor, nave));
    }
}
