package com.security.dto.public_api;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO de solo-lectura para la API pública del Storefront.
 *
 * <p>
 * <strong>Seguridad:</strong> No expone ningún campo administrativo
 * ({@code active}, {@code salesCount}, {@code views}, {@code createdAt}, etc.).
 * Solo contiene la información que un visitante anónimo necesita.
 * </p>
 *
 * <p>
 * Registro inmutable (Java record) para prevenir modificaciones accidentales
 * en la capa de presentación.
 * </p>
 */
public record PublicProductDTO(

        Long id,

        /** Código de referencia único visible al cliente (Ej: FEN-STRAT-01). */
        String sku,

        String name,

        String description,

        /** Descripción enriquecida con HTML (puede ser null). */
        String detailedDescription,

        /** Precio base en moneda local. */
        BigDecimal price,

        /** Precio con descuento aplicado (null = sin descuento). */
        BigDecimal discountPrice,

        /** Unidades disponibles (>= 0; el cliente solo necesita saber si hay stock). */
        Integer stock,

        /** URL de la imagen principal. */
        String imageUrl,

        /** Galería de imágenes adicionales. */
        List<PublicImageDTO> images,

        /** Resumen de la categoría asignada. */
        PublicCategoryRefDTO category,

        /** Resumen de la marca asignada (puede ser null). */
        PublicBrandRefDTO brand,

        /** Atributos técnicos / especificaciones del producto. */
        List<PublicAttributeDTO> attributes,

        /** Modelo del producto (Ej: "Stratocaster", "Les Paul Standard"). */
        String model,

        /** Peso en kilogramos (puede ser null). */
        Double weight,

        /** Dimensiones como texto libre (Ej: "30x40x15 cm"). */
        String dimensions,

        /** ¿Producto marcado como destacado? */
        Boolean featured,

        /** Calificación promedio (0.00 – 5.00). */
        BigDecimal averageRating,

        /** Total de reseñas aprobadas. */
        Integer reviewCount

) {

    // ── Records anidados ─────────────────────────────────────────────────────

    public record PublicImageDTO(String url, Integer displayOrder) {
    }

    public record PublicCategoryRefDTO(Long id, String name, Long parentId, String parentName) {
    }

    public record PublicBrandRefDTO(Long id, String name, String logoUrl) {
    }

    public record PublicAttributeDTO(String name, String value, Integer displayOrder) {
    }
}
