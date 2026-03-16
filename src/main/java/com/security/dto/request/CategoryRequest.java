package com.security.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para crear/actualizar categorías
 * Incluye parentId para manejar jerarquías
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
    private String name;

    private String description;

    private String imageUrl;

    private Boolean active;

    /**
     * ID de la categoría padre (para crear subcategorías)
     * Si es null, la categoría será raíz/principal
     */
    private Long parentId;
}
