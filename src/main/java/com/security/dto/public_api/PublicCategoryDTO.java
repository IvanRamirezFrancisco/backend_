package com.security.dto.public_api;

/**
 * DTO de solo-lectura para categorías en la API pública del Storefront.
 *
 * <p>
 * Solo expone datos que el visitante necesita para navegar el catálogo.
 * Omite campos de auditoría ({@code createdAt}, {@code updatedAt}) y datos
 * internos.
 * </p>
 */
public record PublicCategoryDTO(

        Long id,

        String name,

        String description,

        /** URL de imagen representativa de la categoría (puede ser null). */
        String imageUrl,

        /** ID de la categoría padre (null si es raíz). */
        Long parentId,

        /** Nombre de la categoría padre (null si es raíz). */
        String parentName,

        /**
         * Cantidad de productos <strong>activos</strong> en esta categoría.
         * Calculado por {@code @Formula} en la entidad o en el servicio.
         */
        Integer activeProductCount

) {
}
