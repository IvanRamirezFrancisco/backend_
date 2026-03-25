package com.security.dto.admin;

import com.security.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Proyección segura para el endpoint /api/admin/dashboard/recent-orders.
 *
 * <p>
 * Contiene únicamente los campos escalares necesarios para la tabla del
 * dashboard, evitando cualquier referencia a proxies Hibernate de {@code User}
 * o {@code OrderItem} que causarían {@code LazyInitializationException} al
 * serializar fuera de la sesión JPA.
 * </p>
 */
public record RecentOrderDTO(
        Long id,
        String orderNumber,
        Long customerId,
        String customerName, // firstName + " " + lastName del User
        BigDecimal total,
        OrderStatus status,
        LocalDateTime createdAt) {
}
