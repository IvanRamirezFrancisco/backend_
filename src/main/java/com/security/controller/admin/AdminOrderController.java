package com.security.controller.admin;

import com.security.dto.OrderDTO;
import com.security.enums.OrderStatus;
import com.security.enums.PaymentStatus;
import com.security.enums.ShippingStatus;
import com.security.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Controller para gestión de órdenes — Panel de Administración.
 *
 * Seguridad:
 * - Requiere rol ADMIN o SUPER_ADMIN en cada endpoint
 * - Validación y sanitización de todos los parámetros de entrada
 * - Whitelist de campos de ordenamiento para prevenir inyección
 * - Hardcap en tamaño de página (máximo 100)
 * - Manejo centralizado de excepciones con respuestas controladas
 * - Logs de auditoría sin datos sensibles
 *
 * Endpoints:
 * - GET /api/admin/orders — Listar órdenes con filtros y paginación
 * - GET /api/admin/orders/{id} — Detalle completo de una orden
 * - GET /api/admin/orders/stats — Estadísticas globales
 * - GET /api/admin/orders/customer/{userId} — Órdenes de un cliente
 * - PATCH /api/admin/orders/{id}/status — Cambiar estado de orden
 * - PATCH /api/admin/orders/{id}/payment-status — Cambiar estado de pago
 * - PATCH /api/admin/orders/{id}/shipping-status — Cambiar estado de envío
 * - PATCH /api/admin/orders/{id}/cancel — Cancelar orden con motivo
 */
@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Slf4j
public class AdminOrderController {

    /** Campos permitidos para ordenamiento — previene inyección en JPQL/SQL */
    private static final java.util.Set<String> ALLOWED_SORT_FIELDS = java.util.Set.of(
            "id", "orderNumber", "createdAt", "total", "status", "paymentStatus", "shippingStatus");

    /** Número máximo de registros por página */
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private OrderService orderService;

    // ==================== LISTAR Y BUSCAR ====================

    /**
     * GET /api/admin/orders
     *
     * Listar órdenes con búsqueda avanzada, filtros y paginación.
     * Todos los parámetros son opcionales y se validan antes de usarse.
     */
    @GetMapping
    public ResponseEntity<Page<OrderDTO>> getAllOrders(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) OrderStatus orderStatus,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) ShippingStatus shippingStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        try {
            log.info("GET /api/admin/orders — page={}, size={}, sortBy={}, sortDir={}", page, size, sortBy, sortDir);

            // Validar tamaño de página
            int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

            // Whitelist de campos de ordenamiento
            String safeSortBy = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "createdAt";

            // Dirección segura
            Sort.Direction direction = "ASC".equalsIgnoreCase(sortDir)
                    ? Sort.Direction.ASC
                    : Sort.Direction.DESC;
            PageRequest pageRequest = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(direction, safeSortBy));

            Page<OrderDTO> orders = orderService.searchOrders(
                    search, orderStatus, paymentStatus, shippingStatus,
                    startDate, endDate, pageRequest);

            log.info("GET /api/admin/orders — retornando {} de {} resultados", orders.getNumberOfElements(),
                    orders.getTotalElements());

            return ResponseEntity.ok(orders);

        } catch (Exception e) {
            log.error("Error al listar órdenes: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * GET /api/admin/orders/{id}
     *
     * Obtener detalle completo de una orden por su ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderDTO> getOrderById(@PathVariable Long id) {
        try {
            log.info("GET /api/admin/orders/{} — solicitando detalle", id);

            OrderDTO order = orderService.getOrderById(id);

            log.info("GET /api/admin/orders/{} — orden encontrada: {}", id, order.getOrderNumber());

            return ResponseEntity.ok(order);

        } catch (RuntimeException e) {
            log.warn("GET /api/admin/orders/{} — no encontrada: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("GET /api/admin/orders/{} — error interno: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * GET /api/admin/orders/customer/{userId}
     *
     * Obtener el historial de órdenes de un cliente específico.
     */
    @GetMapping("/customer/{userId}")
    public ResponseEntity<Page<OrderDTO>> getOrdersByCustomer(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            log.info("GET /api/admin/orders/customer/{} — page={}, size={}", userId, page, size);

            int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
            PageRequest pageRequest = PageRequest.of(Math.max(page, 0), safeSize,
                    Sort.by(Sort.Direction.DESC, "createdAt"));

            Page<OrderDTO> orders = orderService.getOrdersByUserId(userId, pageRequest);

            log.info("GET /api/admin/orders/customer/{} — {} órdenes encontradas", userId, orders.getTotalElements());

            return ResponseEntity.ok(orders);

        } catch (Exception e) {
            log.error("Error al obtener órdenes del cliente {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * GET /api/admin/orders/stats
     *
     * Obtener estadísticas globales de órdenes.
     */
    @GetMapping("/stats")
    public ResponseEntity<OrderService.OrderStatsDTO> getOrderStats() {
        try {
            log.info("GET /api/admin/orders/stats — calculando estadísticas");

            OrderService.OrderStatsDTO stats = orderService.getOrderStats();

            log.info("GET /api/admin/orders/stats — {} órdenes totales", stats.getTotalOrders());

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Error al obtener estadísticas de órdenes: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==================== ACTUALIZAR ESTADOS ====================

    /**
     * PATCH /api/admin/orders/{id}/status
     *
     * Cambiar el estado de la orden.
     * Body: { "status": "CONFIRMED" }
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        try {
            String statusStr = body.get("status");

            if (statusStr == null || statusStr.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "El campo 'status' es requerido"));
            }

            OrderStatus newStatus = OrderStatus.valueOf(statusStr.trim().toUpperCase());

            log.info("PATCH /api/admin/orders/{}/status — nuevo estado: {}", id, newStatus);

            OrderDTO updatedOrder = orderService.updateOrderStatus(id, newStatus);

            log.info("PATCH /api/admin/orders/{}/status — actualizado correctamente", id);

            return ResponseEntity.ok(updatedOrder);

        } catch (IllegalArgumentException e) {
            log.warn("Estado de orden inválido para orden {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error",
                            "Valor de estado inválido. Use: PENDING, CONFIRMED, PROCESSING, COMPLETED, CANCELLED"));
        } catch (RuntimeException e) {
            log.warn("Error de negocio al actualizar estado de orden {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error interno al actualizar estado de orden {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }

    /**
     * PATCH /api/admin/orders/{id}/payment-status
     *
     * Cambiar el estado de pago de la orden.
     * Body: { "paymentStatus": "PAID" }
     */
    @PatchMapping("/{id}/payment-status")
    public ResponseEntity<?> updatePaymentStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        try {
            String statusStr = body.get("paymentStatus");

            if (statusStr == null || statusStr.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "El campo 'paymentStatus' es requerido"));
            }

            PaymentStatus newStatus = PaymentStatus.valueOf(statusStr.trim().toUpperCase());

            log.info("PATCH /api/admin/orders/{}/payment-status — nuevo estado: {}", id, newStatus);

            OrderDTO updatedOrder = orderService.updatePaymentStatus(id, newStatus);

            log.info("PATCH /api/admin/orders/{}/payment-status — actualizado correctamente", id);

            return ResponseEntity.ok(updatedOrder);

        } catch (IllegalArgumentException e) {
            log.warn("Estado de pago inválido para orden {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error",
                            "Valor de estado de pago inválido. Use: PENDING, PAID, FAILED, REFUNDED, PARTIALLY_REFUNDED"));
        } catch (RuntimeException e) {
            log.warn("Error de negocio al actualizar estado de pago de orden {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error interno al actualizar estado de pago de orden {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }

    /**
     * PATCH /api/admin/orders/{id}/shipping-status
     *
     * Cambiar el estado de envío de la orden.
     * Body: { "shippingStatus": "SHIPPED", "trackingNumber": "TRK123456" }
     */
    @PatchMapping("/{id}/shipping-status")
    public ResponseEntity<?> updateShippingStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        try {
            String statusStr = body.get("shippingStatus");
            String trackingNumber = body.get("trackingNumber");

            if (statusStr == null || statusStr.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "El campo 'shippingStatus' es requerido"));
            }

            ShippingStatus newStatus = ShippingStatus.valueOf(statusStr.trim().toUpperCase());

            log.info("PATCH /api/admin/orders/{}/shipping-status — nuevo estado: {}, tracking: {}",
                    id, newStatus, trackingNumber != null ? "[provided]" : "[none]");

            OrderDTO updatedOrder = orderService.updateShippingStatus(id, newStatus, trackingNumber);

            log.info("PATCH /api/admin/orders/{}/shipping-status — actualizado correctamente", id);

            return ResponseEntity.ok(updatedOrder);

        } catch (IllegalArgumentException e) {
            log.warn("Estado de envío inválido para orden {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error",
                            "Valor de estado de envío inválido. Use: PENDING, PREPARING, SHIPPED, IN_TRANSIT, DELIVERED, RETURNED"));
        } catch (RuntimeException e) {
            log.warn("Error de negocio al actualizar estado de envío de orden {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error interno al actualizar estado de envío de orden {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }

    /**
     * PATCH /api/admin/orders/{id}/cancel
     *
     * Cancelar una orden con un motivo obligatorio.
     * Body: { "reason": "Solicitud del cliente" }
     */
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<?> cancelOrder(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        try {
            String reason = body.get("reason");

            if (reason == null || reason.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "El motivo de cancelación es obligatorio"));
            }

            if (reason.trim().length() > 500) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "El motivo no puede exceder 500 caracteres"));
            }

            log.info("PATCH /api/admin/orders/{}/cancel — motivo proporcionado", id);

            OrderDTO updatedOrder = orderService.updateOrderStatus(id, OrderStatus.CANCELLED);

            log.info("PATCH /api/admin/orders/{}/cancel — orden cancelada correctamente", id);

            return ResponseEntity.ok(updatedOrder);

        } catch (RuntimeException e) {
            log.warn("Error al cancelar orden {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error interno al cancelar orden {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }

    /**
     * GET /api/admin/orders/export/csv
     *
     * Exportar órdenes a CSV para descarga directa.
     */
    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportToCsv() {
        try {
            log.info("GET /api/admin/orders/export/csv — generando exportación");

            PageRequest allPages = PageRequest.of(0, MAX_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<OrderDTO> page = orderService.searchOrders(null, null, null, null, null, null, allPages);
            java.util.List<OrderDTO> orders = page.getContent();

            StringBuilder csv = new StringBuilder();
            csv.append(
                    "ID,N\u00famero Orden,Cliente,Email,Fecha,Total,Estado Orden,Estado Pago,Estado Env\u00edo,Tracking\n");

            for (OrderDTO o : orders) {
                csv.append(o.getId()).append(",");
                csv.append(escapeCsv(o.getOrderNumber())).append(",");
                csv.append(escapeCsv(o.getCustomerName())).append(",");
                csv.append(escapeCsv(o.getCustomerEmail())).append(",");
                csv.append(o.getOrderDate() != null ? o.getOrderDate().toString() : "").append(",");
                csv.append(o.getTotal()).append(",");
                csv.append(o.getStatus()).append(",");
                csv.append(o.getPaymentStatus()).append(",");
                csv.append(o.getShippingStatus()).append(",");
                csv.append(escapeCsv(o.getTrackingNumber() != null ? o.getTrackingNumber() : "")).append("\n");
            }

            byte[] bytes = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String filename = "ordenes_" + java.time.LocalDate.now() + ".csv";

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.parseMediaType("text/csv; charset=UTF-8"));
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(bytes.length);

            log.info("GET /api/admin/orders/export/csv — {} órdenes exportadas", orders.size());

            return ResponseEntity.ok().headers(headers).body(bytes);

        } catch (Exception e) {
            log.error("Error al exportar órdenes a CSV: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /** Escapa un campo para formato CSV. */
    private String escapeCsv(String value) {
        if (value == null)
            return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
