package pa.amp.registro_naves.documento;

import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pa.amp.registro_naves.nave.AccesoNave;
import java.util.List;

/** HU-13: usa exactamente el acceso de lectura del expediente. */
@RestController
@RequestMapping("/api/naves/{naveId}/documentos/requisitos")
public class RequisitosDocumentoController {
    private final AccesoNave acceso;
    private final RequisitosDocumentoService requisitos;

    public RequisitosDocumentoController(AccesoNave acceso, RequisitosDocumentoService requisitos) {
        this.acceso = acceso;
        this.requisitos = requisitos;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<RequisitosDocumentoService.Requisito> consultar(@PathVariable Long naveId,
                                                               Authentication autenticacion) {
        return requisitos.evaluar(acceso.exigirLectura(naveId, autenticacion.getName()));
    }
}
