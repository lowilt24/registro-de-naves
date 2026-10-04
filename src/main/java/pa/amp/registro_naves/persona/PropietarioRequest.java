package pa.amp.registro_naves.persona;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** HU-05 — datos del propietario y de su vinculo con la nave. */
public record PropietarioRequest(

        @NotBlank(message = "El nombre o razon social es obligatorio.")
        @Size(max = 150, message = "El nombre no puede exceder 150 caracteres.")
        String nombre,

        @NotNull(message = "Debe indicar si es persona natural o juridica.")
        TipoPersona tipo,

        @NotBlank(message = "La nacionalidad es obligatoria.")
        @Size(max = 80)
        String nacionalidad,

        @NotBlank(message = "El domicilio es obligatorio.")
        @Size(max = 250)
        String domicilio,

        /** Obligatorio solo si el tipo es JURIDICA; lo verifica el servicio. */
        @Size(max = 80)
        String paisConstitucion,

        /** Cedula, pasaporte o RUC. Opcional. */
        @Size(max = 50)
        String identificacion,

        @NotNull(message = "Debe indicar la nave a la que se vincula el propietario.")
        Long naveId,

        /** Cuota de participacion. Si se omite, se asume 100. */
        @DecimalMin(value = "0.01", message = "El porcentaje debe ser mayor que cero.")
        @DecimalMax(value = "100.00", message = "El porcentaje no puede superar 100.")
        @Digits(integer = 3, fraction = 2, message = "El porcentaje admite hasta 2 decimales.")
        BigDecimal porcentaje
) {}
