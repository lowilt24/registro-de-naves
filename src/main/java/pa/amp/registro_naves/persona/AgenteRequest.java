package pa.amp.registro_naves.persona;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

/** HU-06 — designacion del agente residente de una nave. */
public record AgenteRequest(

        @NotBlank(message = "El nombre del abogado o firma es obligatorio.")
        @Size(max = 150, message = "El nombre no puede exceder 150 caracteres.")
        String nombre,

        /** Solo aplica a un abogado individual; una firma puede no tenerlo. */
        @Size(max = 50)
        String idoneidad,

        @NotBlank(message = "El telefono es obligatorio.")
        @Size(max = 40)
        @Pattern(regexp = "^[0-9 +().\\-]{6,40}$",
                 message = "El telefono solo admite numeros, espacios y los signos + ( ) . -")
        String telefono,

        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "El correo no tiene un formato valido.")
        @Size(max = 120)
        String correo,

        // --- Referencia del poder. El archivo se adjunta en HU-07. ---

        @Size(max = 60)
        String poderNumero,

        @PastOrPresent(message = "La fecha del poder no puede ser futura.")
        LocalDate poderFecha,

        @Size(max = 150)
        String poderLugar
) {}
