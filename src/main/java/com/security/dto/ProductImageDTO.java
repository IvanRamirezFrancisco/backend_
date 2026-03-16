package com.security.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para imágenes de productos
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductImageDTO {

    private Long id;

    @NotBlank(message = "La URL de la imagen es obligatoria")
    private String imageUrl;

    private String altText;

    @NotNull(message = "El orden de visualización es obligatorio")
    @Min(value = 1, message = "El orden debe ser al menos 1")
    private Integer displayOrder;

    /**
     * Constructor de conveniencia (sin ID, para creación)
     */
    public ProductImageDTO(String imageUrl, String altText, Integer displayOrder) {
        this.imageUrl = imageUrl;
        this.altText = altText;
        this.displayOrder = displayOrder;
    }
}
