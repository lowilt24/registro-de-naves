package pa.amp.registro_naves.persona;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Solo lectura por ahora. HU-05 y HU-06 (Sprint 3) agregan el alta de
 * propietarios y agentes; aqui los exponemos para poder llenar los selectores
 * del formulario de registro de nave.
 */
@RestController
@RequestMapping("/api")
public class PartesController {

    private final PropietarioRepository propietarios;
    private final AgenteResidenteRepository agentes;

    public PartesController(PropietarioRepository propietarios, AgenteResidenteRepository agentes) {
        this.propietarios = propietarios;
        this.agentes = agentes;
    }

    @GetMapping("/propietarios")
    public List<Map<String, Object>> listarPropietarios() {
        return propietarios.findAll().stream()
                .map(p -> Map.<String, Object>of(
                        "id", p.getId(),
                        "nombre", p.getNombre(),
                        "identificacion", p.getIdentificacion()))
                .toList();
    }

    @GetMapping("/agentes-residentes")
    public List<Map<String, Object>> listarAgentes() {
        return agentes.findAll().stream()
                .map(a -> Map.<String, Object>of(
                        "id", a.getId(),
                        "nombre", a.getNombre(),
                        "idoneidad", a.getIdoneidad()))
                .toList();
    }
}
