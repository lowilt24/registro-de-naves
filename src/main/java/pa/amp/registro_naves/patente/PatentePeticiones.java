package pa.amp.registro_naves.patente;

import jakarta.validation.constraints.*;
import pa.amp.registro_naves.documento.TipoDocumento;

public final class PatentePeticiones {
    private PatentePeticiones() {}
    public enum Procedencia { REGISTRO_ANTERIOR, CONSTRUCCION, VENTA_JUDICIAL }
    public enum Clase { TECNICO, ADICIONAL }
    public record Crear(@NotNull Procedencia procedencia) {}
    public record Requisito(@NotBlank @Size(max=180) String nombre,@NotNull Clase clase,
            @NotBlank @Size(max=1000) String motivo,TipoDocumento tipoOrigen) {}
    public record Reutilizacion(@NotNull TipoDocumento tipoOrigen,@NotBlank @Size(max=1000) String motivo) {}
    public record Confirmacion(boolean sinTecnicosAdicionales,@NotBlank @Size(max=1000) String motivo) {}
    public record Referencia(@NotNull @Positive Long documentoId) {}
    public record Revision(@NotNull @Positive Long envioId,@NotBlank String decision,@Size(max=1000) String observacion) {}
    public record Devolucion(@NotNull @Positive Long envioId,@NotBlank @Size(max=1000) String observacion) {}
    public record Contexto(@NotNull @Positive Long envioId) {}
}
