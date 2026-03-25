package com.security.service;

import com.security.dto.OrderDTO;
import com.security.dto.OrderItemDTO;
import com.security.entity.Order;
import com.security.entity.OrderItem;
import com.security.entity.User;
import com.security.enums.OrderStatus;
import com.security.enums.PaymentStatus;
import com.security.enums.ShippingStatus;
import com.security.repository.OrderRepository;
import com.security.service.AuditLogService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Servicio de Órdenes — Lógica de negocio completa.
 *
 * Funcionalidades:
 * - CRUD de órdenes
 * - Búsqueda avanzada con filtros combinados
 * - Cambio de estados con validaciones de transición
 * - Conversión de entidades a DTOs
 * - Logs de auditoría sin datos sensibles
 */
@Service
@Transactional
@Slf4j
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AuditLogService auditLogService;

    // ==================== LISTAR Y BUSCAR ====================

    /**
     * Obtener todas las órdenes con paginación.
     */
    public Page<OrderDTO> getAllOrders(Pageable pageable) {
        log.info("getAllOrders — página: {}, tamaño: {}", pageable.getPageNumber(), pageable.getPageSize());
        Page<Order> ordersPage = orderRepository.findAll(pageable);
        log.info("getAllOrders — {} de {} órdenes encontradas", ordersPage.getNumberOfElements(),
                ordersPage.getTotalElements());
        return ordersPage.map(this::convertToDTO);
    }

    /**
     * Búsqueda avanzada con filtros múltiples combinados.
     *
     * Usa JpaSpecificationExecutor para construir los predicados de forma
     * dinámica en Java. Esto evita el error "bytea → timestamp" que ocurre
     * en Hibernate 6.3 + PostgreSQL cuando se pasa null como LocalDateTime
     * en un parámetro JPQL con CAST.
     *
     * Cada filtro sólo se añade al predicado si el valor está presente (no null),
     * por lo que nunca se envía un parámetro null de tipo temporal al JDBC.
     */
    public Page<OrderDTO> searchOrders(
            String search,
            OrderStatus orderStatus,
            PaymentStatus paymentStatus,
            ShippingStatus shippingStatus,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {

        log.info("searchOrders — search={}, orderStatus={}, paymentStatus={}, shippingStatus={}, page={}, size={}",
                search != null ? "[provided]" : "null", orderStatus, paymentStatus, shippingStatus,
                pageable.getPageNumber(), pageable.getPageSize());

        Specification<Order> spec = buildSearchSpecification(
                search, orderStatus, paymentStatus, shippingStatus, startDate, endDate);

        Page<Order> ordersPage = orderRepository.findAll(spec, pageable);

        log.info("searchOrders — {} de {} resultados", ordersPage.getNumberOfElements(), ordersPage.getTotalElements());
        return ordersPage.map(this::convertToDTO);
    }

    /**
     * Construye la Specification de búsqueda con predicados condicionales.
     * Cada predicado sólo se añade si el parámetro correspondiente es no-null.
     */
    private Specification<Order> buildSearchSpecification(
            String search,
            OrderStatus orderStatus,
            PaymentStatus paymentStatus,
            ShippingStatus shippingStatus,
            LocalDateTime startDate,
            LocalDateTime endDate) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Búsqueda de texto: número de orden, nombre/apellido/email del usuario
            if (search != null && !search.trim().isEmpty()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                Join<Order, User> user = root.join("user", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("orderNumber")), pattern),
                        cb.like(cb.lower(user.get("firstName")), pattern),
                        cb.like(cb.lower(user.get("lastName")), pattern),
                        cb.like(cb.lower(user.get("email")), pattern)));
            }

            // Estado de la orden
            if (orderStatus != null) {
                predicates.add(cb.equal(root.get("status"), orderStatus));
            }

            // Estado de pago
            if (paymentStatus != null) {
                predicates.add(cb.equal(root.get("paymentStatus"), paymentStatus));
            }

            // Estado de envío
            if (shippingStatus != null) {
                predicates.add(cb.equal(root.get("shippingStatus"), shippingStatus));
            }

            // Fecha desde — sólo se añade si el valor no es null (sin CAST, sin JDBC null)
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }

            // Fecha hasta — ídem
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            // Evitar JOIN duplicado en count query
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                query.distinct(true);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Obtener órdenes de un cliente específico.
     */
    public Page<OrderDTO> getOrdersByUserId(Long userId, Pageable pageable) {
        log.info("getOrdersByUserId — userId={}", userId);

        Page<Order> ordersPage = orderRepository.findByUserId(userId, pageable);

        log.info("getOrdersByUserId — {} órdenes encontradas para usuario {}", ordersPage.getTotalElements(), userId);
        return ordersPage.map(this::convertToDTO);
    }

    /**
     * Obtener una orden por su ID.
     */
    public OrderDTO getOrderById(Long id) {
        log.info("getOrderById — id={}", id);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("getOrderById — orden no encontrada con id={}", id);
                    return new RuntimeException("Orden no encontrada con ID: " + id);
                });
        log.info("getOrderById — orden encontrada: {}", order.getOrderNumber());
        return convertToDTO(order);
    }

    /**
     * Obtener orden por número de orden.
     */
    public OrderDTO getOrderByNumber(String orderNumber) {
        log.info("getOrderByNumber — orderNumber={}", orderNumber);
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> {
                    log.warn("getOrderByNumber — no encontrada: {}", orderNumber);
                    return new RuntimeException("Orden no encontrada: " + orderNumber);
                });
        return convertToDTO(order);
    }

    // ==================== ACTUALIZAR ESTADOS ====================

    /**
     * Actualizar el estado de la orden.
     */
    public OrderDTO updateOrderStatus(Long orderId, OrderStatus newStatus) {
        log.info("updateOrderStatus — orderId={}, newStatus={}", orderId, newStatus);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada"));

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);

        if (newStatus == OrderStatus.CANCELLED) {
            order.setCancelledAt(LocalDateTime.now());
        }

        Order savedOrder = orderRepository.save(order);
        log.info("updateOrderStatus — estado actualizado de {} a {} para orden {}", oldStatus, newStatus, orderId);

        // Auditoría de cambio de estado
        try {
            Map<String, Object> oldValues = new HashMap<>();
            oldValues.put("status", oldStatus != null ? oldStatus.name() : null);
            Map<String, Object> newValues = new HashMap<>();
            newValues.put("status", newStatus.name());

            auditLogService.log(
                    "ORDER_STATUS_CHANGE", "ORDER_STATUS_CHANGE", "ORDER",
                    orderId, oldValues, newValues, "INFO", true);
        } catch (Exception auditEx) {
            log.warn("⚠️ No se pudo registrar audit log para cambio de estado de orden {}: {}",
                    orderId, auditEx.getMessage());
        }

        return convertToDTO(savedOrder);
    }

    /**
     * Actualizar el estado de pago de la orden.
     * Si se marca como PAID y la orden está PENDING, se confirma automáticamente.
     */
    public OrderDTO updatePaymentStatus(Long orderId, PaymentStatus newStatus) {
        log.info("updatePaymentStatus — orderId={}, newStatus={}", orderId, newStatus);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada"));

        PaymentStatus oldStatus = order.getPaymentStatus();
        order.setPaymentStatus(newStatus);

        if (newStatus == PaymentStatus.PAID && order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CONFIRMED);
            log.info("updatePaymentStatus — orden {} confirmada automáticamente al recibir pago", orderId);
        }

        Order savedOrder = orderRepository.save(order);
        log.info("updatePaymentStatus — estado de pago actualizado de {} a {} para orden {}", oldStatus, newStatus,
                orderId);

        // Auditoría de cambio de estado de pago
        try {
            Map<String, Object> oldValues = new HashMap<>();
            oldValues.put("paymentStatus", oldStatus != null ? oldStatus.name() : null);
            Map<String, Object> newValues = new HashMap<>();
            newValues.put("paymentStatus", newStatus.name());

            auditLogService.log(
                    "ORDER_PAYMENT_CHANGE", "ORDER_STATUS_CHANGE", "ORDER",
                    orderId, oldValues, newValues, "INFO", true);
        } catch (Exception auditEx) {
            log.warn("⚠️ No se pudo registrar audit log para cambio de pago de orden {}: {}",
                    orderId, auditEx.getMessage());
        }

        return convertToDTO(savedOrder);
    }

    /**
     * Actualizar el estado de envío de la orden.
     * Valida que el pago esté confirmado antes de marcar como SHIPPED.
     */
    public OrderDTO updateShippingStatus(Long orderId, ShippingStatus newStatus, String trackingNumber) {
        log.info("updateShippingStatus — orderId={}, newStatus={}", orderId, newStatus);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada"));

        if (newStatus == ShippingStatus.SHIPPED && order.getPaymentStatus() != PaymentStatus.PAID) {
            log.warn("updateShippingStatus — intento de envío sin pago confirmado en orden {}", orderId);
            throw new RuntimeException("La orden debe estar pagada antes de marcar como enviada");
        }

        ShippingStatus oldStatus = order.getShippingStatus();
        order.setShippingStatus(newStatus);

        if (trackingNumber != null && !trackingNumber.trim().isEmpty()) {
            order.setTrackingNumber(trackingNumber.trim());
        }

        if (newStatus == ShippingStatus.SHIPPED) {
            order.setShippedAt(LocalDateTime.now());
        } else if (newStatus == ShippingStatus.DELIVERED) {
            order.setDeliveredAt(LocalDateTime.now());
            order.setStatus(OrderStatus.COMPLETED);
            log.info("updateShippingStatus — orden {} marcada como completada al entregar", orderId);
        }

        Order savedOrder = orderRepository.save(order);
        log.info("updateShippingStatus — estado de envío actualizado de {} a {} para orden {}", oldStatus, newStatus,
                orderId);

        // Auditoría de cambio de estado de envío
        try {
            Map<String, Object> oldValues = new HashMap<>();
            oldValues.put("shippingStatus", oldStatus != null ? oldStatus.name() : null);
            Map<String, Object> newValues = new HashMap<>();
            newValues.put("shippingStatus", newStatus.name());
            if (trackingNumber != null && !trackingNumber.trim().isEmpty()) {
                newValues.put("trackingNumber", trackingNumber.trim());
            }

            auditLogService.log(
                    "ORDER_SHIPPING_CHANGE", "ORDER_STATUS_CHANGE", "ORDER",
                    orderId, oldValues, newValues, "INFO", true);
        } catch (Exception auditEx) {
            log.warn("⚠️ No se pudo registrar audit log para cambio de envío de orden {}: {}",
                    orderId, auditEx.getMessage());
        }

        return convertToDTO(savedOrder);
    }

    // ==================== ESTADÍSTICAS ====================

    /**
     * Obtener contadores de órdenes por cada estado.
     */
    public OrderStatsDTO getOrderStats() {
        log.info("getOrderStats — calculando estadísticas de órdenes");
        OrderStatsDTO stats = new OrderStatsDTO();
        stats.setTotalOrders(orderRepository.count());
        stats.setPendingOrders(orderRepository.countByStatus(OrderStatus.PENDING));
        stats.setConfirmedOrders(orderRepository.countByStatus(OrderStatus.CONFIRMED));
        stats.setProcessingOrders(orderRepository.countByStatus(OrderStatus.PROCESSING));
        stats.setCompletedOrders(orderRepository.countByStatus(OrderStatus.COMPLETED));
        stats.setCancelledOrders(orderRepository.countByStatus(OrderStatus.CANCELLED));
        log.info("getOrderStats — {} órdenes totales", stats.getTotalOrders());
        return stats;
    }

    // ==================== CONVERSIÓN DTO ====================

    /**
     * Convierte una entidad Order a OrderDTO.
     */
    private OrderDTO convertToDTO(Order order) {
        OrderDTO dto = new OrderDTO();

        // Datos básicos
        dto.setId(order.getId());
        dto.setOrderNumber(order.getOrderNumber());
        dto.setOrderDate(order.getCreatedAt());

        // Cliente
        User user = order.getUser();
        if (user != null) {
            dto.setUserId(user.getId());
            dto.setCustomerName(user.getFirstName() + " " + user.getLastName());
            dto.setCustomerEmail(user.getEmail());
            dto.setCustomerPhone(user.getPhone());
        }

        // Totales
        dto.setSubtotal(order.getSubtotal());
        dto.setTax(order.getTax());
        dto.setShipping(order.getShipping());
        dto.setDiscount(order.getDiscount());
        dto.setTotal(order.getTotal());

        // Estados
        dto.setStatus(order.getStatus());
        dto.setStatusDisplayName(order.getStatus().name());

        dto.setShippingStatus(order.getShippingStatus());
        dto.setShippingStatusDisplayName(order.getShippingStatus().getDisplayName());

        dto.setPaymentStatus(order.getPaymentStatus());
        dto.setPaymentStatusDisplayName(order.getPaymentStatus().name());

        // Pago
        dto.setPaymentMethod(order.getPaymentMethod());
        dto.setTransactionId(order.getTransactionId());

        // Envío
        dto.setShippingAddress(order.getShippingAddress());
        dto.setBillingAddress(order.getBillingAddress());
        dto.setTrackingNumber(order.getTrackingNumber());
        dto.setShippedAt(order.getShippedAt());
        dto.setDeliveredAt(order.getDeliveredAt());

        // Items
        List<OrderItemDTO> itemDTOs = order.getItems().stream()
                .map(this::convertItemToDTO)
                .collect(Collectors.toList());
        dto.setItems(itemDTOs);
        dto.setTotalItems(dto.calculateTotalItems());

        // Notas
        dto.setNotes(order.getNotes());
        dto.setCustomerNotes(order.getCustomerNotes());
        dto.setCancellationReason(order.getCancellationReason());

        // Fechas
        dto.setCreatedAt(order.getCreatedAt());
        dto.setUpdatedAt(order.getUpdatedAt());
        dto.setCancelledAt(order.getCancelledAt());

        return dto;
    }

    /**
     * Convierte una entidad OrderItem a OrderItemDTO.
     */
    private OrderItemDTO convertItemToDTO(OrderItem item) {
        OrderItemDTO dto = new OrderItemDTO();
        dto.setId(item.getId());
        dto.setProductId(item.getProduct().getId());
        dto.setProductName(item.getProductName()); // Usamos el nombre guardado
        dto.setProductSku(item.getProductSku()); // Usamos el SKU guardado
        dto.setProductImage(item.getProduct().getImageUrl());
        dto.setQuantity(item.getQuantity());
        dto.setPrice(item.getUnitPrice()); // ✅ CORREGIDO: unitPrice no price
        dto.setSubtotal(item.getSubtotal());
        return dto;
    }

    // ==================== DTO AUXILIARES ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderStatsDTO {
        private Long totalOrders;
        private Long pendingOrders;
        private Long confirmedOrders;
        private Long processingOrders;
        private Long completedOrders;
        private Long cancelledOrders;
    }
}
