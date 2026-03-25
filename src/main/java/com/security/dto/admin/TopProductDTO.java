package com.security.dto.admin;

import java.math.BigDecimal;

/**
 * Proyección segura para el endpoint /api/admin/dashboard/top-products.
 *
 * <p>
 * Contiene únicamente los campos escalares necesarios para la lista del
 * dashboard, evitando cualquier referencia al proxy Hibernate de
 * {@code Category}
 * que causaría {@code LazyInitializationException} al serializar fuera de la
 * sesión JPA.
 * </p>
 */
public record TopProductDTO(
        Long id,
        String name,
        String sku,
        BigDecimal price,
        Long salesCount,
        String imageUrl) {
}
