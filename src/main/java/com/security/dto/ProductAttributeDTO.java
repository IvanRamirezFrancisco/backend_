package com.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para atributos dinámicos de productos
 * Se utiliza para transferir datos entre Angular y Spring Boot
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductAttributeDTO {

    private Long id;

    /**
     * Nombre del atributo (key)
     * Ejemplos: "Material", "Calibre", "Tipo", "Grosor"
     */
    @NotBlank(message = "El nombre del atributo (key) es obligatorio")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
    private String key;

    /**
     * Valor del atributo (value)
     * Ejemplos: "Caoba", ".009-.042", "Eléctrica", "1.14mm"
     */
    @NotBlank(message = "El valor del atributo (value) es obligatorio")
    @Size(max = 255, message = "El valor no puede exceder 255 caracteres")
    private String value;

    /**
     * Orden de visualización
     */
    private Integer displayOrder;

    /**
     * Constructor de conveniencia sin ID
     */
    public ProductAttributeDTO(String key, String value, Integer displayOrder) {
        this.key = key;
        this.value = value;
        this.displayOrder = displayOrder;
    }
}
