package com.security.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * DTO para actualizar la configuración de una automatización.
 *
 * <p>
 * Valida que la expresión cron tenga el formato Spring de 6 campos
 * y que la zona horaria sea un identificador IANA válido.
 * </p>
 */
public record UpdateAutomationRequest(

                @NotBlank(message = "La expresión cron es obligatoria") @Size(max = 100, message = "La expresión cron no puede superar 100 caracteres") String cronExpression,

                @NotBlank(message = "La zona horaria es obligatoria") @Size(max = 50, message = "La zona horaria no puede superar 50 caracteres") @Pattern(regexp = "^[A-Za-z]+/[A-Za-z_]+(/[A-Za-z_]+)?$", message = "Formato de zona horaria inválido (ej. America/Mexico_City)") String timezone,

                /**
                 * Parámetros de configuración del job (puede ser null para mantener los
                 * actuales).
                 */
                Map<String, Object> parameters) {
}
