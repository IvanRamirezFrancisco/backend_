package com.security.dto.response;

import com.security.entity.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.Hibernate;

import java.time.LocalDateTime;

/**
 * DTO para respuesta de categoría
 * Evita problemas de serialización JSON con relaciones bidireccionales
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDTO {

    private Long id;
    private String name;
    private String description;
    private String imageUrl;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Campos para jerarquía
    private Long parentId;
    private String parentName;
    private Integer subcategoryCount;

    // Contador de productos
    private Integer productCount;

    /**
     * Convertir entidad Category a DTO
     * IMPORTANTE: Maneja las relaciones LAZY de forma segura
     */
    public static CategoryDTO fromEntity(Category category) {
        if (category == null) {
            return null;
        }

        // 🔒 Extraer datos de forma segura sin tocar colecciones LAZY
        Long parentId = null;
        String parentName = null;

        // Solo acceder al parent si está inicializado
        if (category.getParent() != null && Hibernate.isInitialized(category.getParent())) {
            parentId = category.getParent().getId();
            parentName = category.getParent().getName();
        }

        // Para subcategories: verificar si está inicializada (JOIN FETCH en el repo)
        Integer subcategoryCount = 0;
        if (Hibernate.isInitialized(category.getSubcategories())) {
            subcategoryCount = category.getSubcategories() != null ? category.getSubcategories().size() : 0;
        }

        // productCount viene del campo @Formula de Hibernate — siempre disponible,
        // calculado como subquery SQL sin necesidad de cargar la colección LAZY.
        Integer productCount = category.getProductCount();

        return CategoryDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .active(category.getActive())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .parentId(parentId)
                .parentName(parentName)
                .subcategoryCount(subcategoryCount)
                .productCount(productCount)
                .build();
    }
}
