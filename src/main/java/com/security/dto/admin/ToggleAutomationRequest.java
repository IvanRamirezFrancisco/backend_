package com.security.dto.admin;

import jakarta.validation.constraints.NotNull;

/**
 * DTO para encender/apagar una automatización vía PATCH.
 */
public record ToggleAutomationRequest(

                @NotNull(message = "El campo 'enabled' es obligatorio") Boolean enabled) {
}
