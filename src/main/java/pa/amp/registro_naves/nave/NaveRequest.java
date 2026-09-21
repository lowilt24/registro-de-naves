package pa.amp.registro_naves.nave;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * HU-03 — datos de la nave.
 *
 * Cambio del Sprint 3: ya no pide propietarioId ni agenteResidenteId. La
 * nave se registra sola y despues se le vinculan propietarios (HU-05) y
 * se le designa agente residente (HU-06).
 */
public record NaveRequest(

        /*
         * Remediacion del hallazgo SEC-02 del informe de seguridad.
         *
         * El patron anterior admitia "admin'--", que es la forma canonica
         * de un comentario SQL. Ahora se exige al menos una letra y se
         * rechazan las secuencias -- , '- y -' , sin dejar de aceptar
         * nombres legitimos como O'Brien o Mar-Azul.
         */
        @NotBlank(message = "El nombre de la nave es obligatorio.")
        @Size(max = 120, message = "El nombre no puede exceder 120 caracteres.")
        @Pattern(regexp = "^(?=.*\\p{L})(?!.*(--|'-|-'))[\\p{L}\\p{N} .'\\-]+$",
                 message = "El nombre debe contener letras y no admite secuencias como -- o '-.")
        String nombre,

        @NotNull(message = "El tipo de nave es obligatorio.")
        TipoNave tipo,

        @NotNull(message = "El tipo de servicio es obligatorio.")
        TipoServicio servicio,

        @NotNull(message = "El tonelaje bruto es obligatorio.")
        @DecimalMin(value = "0.01", message = "El tonelaje bruto debe ser mayor que cero.")
        @Digits(integer = 10, fraction = 2, message = "El tonelaje bruto admite hasta 2 decimales.")
        BigDecimal tonelajeBruto,

        @NotNull(message = "El tonelaje neto es obligatorio.")
        @DecimalMin(value = "0.01", message = "El tonelaje neto debe ser mayor que cero.")
        @Digits(integer = 10, fraction = 2, message = "El tonelaje neto admite hasta 2 decimales.")
        BigDecimal tonelajeNeto,

        @NotNull(message = "La eslora es obligatoria.")
        @DecimalMin(value = "0.01", message = "La eslora debe ser mayor que cero.")
        @Digits(integer = 6, fraction = 2, message = "La eslora admite hasta 2 decimales.")
        BigDecimal eslora,

        @NotNull(message = "La manga es obligatoria.")
        @DecimalMin(value = "0.01", message = "La manga debe ser mayor que cero.")
        @Digits(integer = 6, fraction = 2, message = "La manga admite hasta 2 decimales.")
        BigDecimal manga,

        @NotNull(message = "El puntal es obligatorio.")
        @DecimalMin(value = "0.01", message = "El puntal debe ser mayor que cero.")
        @Digits(integer = 6, fraction = 2, message = "El puntal admite hasta 2 decimales.")
        BigDecimal puntal,

        @NotNull(message = "El anio de construccion es obligatorio.")
        @Min(value = 1900, message = "El anio de construccion no puede ser anterior a 1900.")
        @Max(value = 2100, message = "El anio de construccion no es valido.")
        Integer anioConstruccion,

        @NotBlank(message = "El lugar de construccion es obligatorio.")
        @Size(max = 120)
        String lugarConstruccion,

        @NotBlank(message = "El material del casco es obligatorio.")
        @Size(max = 60)
        String materialCasco,

        @NotBlank(message = "El tipo de propulsion es obligatorio.")
        @Size(max = 60)
        String tipoPropulsion,

        @NotNull(message = "La potencia es obligatoria.")
        @DecimalMin(value = "0.01", message = "La potencia debe ser mayor que cero.")
        @Digits(integer = 8, fraction = 2, message = "La potencia admite hasta 2 decimales.")
        BigDecimal potenciaKw
) {}
