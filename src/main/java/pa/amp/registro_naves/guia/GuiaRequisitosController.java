package pa.amp.registro_naves.guia;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pa.amp.registro_naves.common.*;
import pa.amp.registro_naves.documento.RequisitosDocumentoService;
import pa.amp.registro_naves.nave.*;
import pa.amp.registro_naves.usuario.*;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/guia-requisitos")
public class GuiaRequisitosController {
    private final RequisitosDocumentoService requisitos;
    private final UsuarioRepository usuarios;
    public GuiaRequisitosController(RequisitosDocumentoService requisitos,UsuarioRepository usuarios) {
        this.requisitos=requisitos; this.usuarios=usuarios;
    }
    public record Consulta(@NotNull TipoNave tipo,@NotNull TipoServicio servicio,
            @DecimalMin("0.01") @Digits(integer=6,fraction=2) BigDecimal eslora,
            @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal tonelajeBruto) {}
    public record Guia(List<RequisitosDocumentoService.Requisito> documentos,boolean requiereRevision,List<String> pasos) {}

    @PostMapping
    public Guia consultar(@Valid @RequestBody Consulta consulta,Authentication auth) {
        if(usuarios.findByCorreoIgnoreCase(auth.getName()).map(Usuario::getRol).orElse(null)!=Rol.AGENTE_NAVIERO)
            throw new AccesoDenegadoException("La guía de requisitos está disponible para el agente naviero.");
        if(consulta.tipo()!=TipoNave.CARGA && consulta.tipo()!=TipoNave.PASAJEROS && consulta.tipo()!=TipoNave.MIXTA)
            throw new ValidacionCampoException("tipo","La guía cubre naves comerciales de carga, pasajeros y mixtas.");
        Nave nave=new Nave(); nave.setTipo(consulta.tipo()); nave.setServicio(consulta.servicio());
        nave.setEslora(consulta.eslora()); nave.setTonelajeBruto(consulta.tonelajeBruto());
        var documentos=requisitos.evaluar(nave);
        return new Guia(documentos,documentos.stream().anyMatch(r->r.estado()==RequisitosDocumentoService.Aplicabilidad.REQUIERE_REVISION),
                List.of("Consulte la disponibilidad del nombre de su nave.",
                        "Registre los datos de la nave para abrir su trámite.",
                        "Vincule los propietarios y su participación.",
                        "Designe al agente residente.",
                        "Cargue los documentos obligatorios aplicables; los casos pendientes requieren aclaración.",
                        "La DGMM revisa el expediente. Corrija los documentos o el trámite si le solicita subsanar.",
                        "Cuando se apruebe el trámite, descargue la constancia de registro de la nave."));
    }
}
