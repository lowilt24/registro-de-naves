package pa.amp.registro_naves.nave;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * Campos obligatorios de HU-03. Las anotaciones son el contrato de validacion
 * que ejercitan los casos negativos TC-02 y TC-03 de Katalon.
 */
public record NaveRequest(

        @NotBlank(message = "El nombre de la nave es obligatorio.")
        @Size(max = 120, message = "El nombre no puede exceder 120 caracteres.")
        @Pattern(regexp = "^[\\p{L}\\p{N} .'\\-]+$",
                 message = "El nombre solo admite letras, numeros, espacios, punto, guion y apostrofe.")
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
        BigDecimal potenciaKw,

        @NotNull(message = "Debe seleccionar el propietario de la nave.")
        Long propietarioId,

        @NotNull(message = "Debe seleccionar el agente residente.")
        Long agenteResidenteId
) {}
