package com.security.repository;

import com.security.entity.Order;
import com.security.enums.OrderStatus;
import com.security.enums.PaymentStatus;
import com.security.enums.ShippingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 📦 Repositorio de Órdenes con búsqueda avanzada
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

        Optional<Order> findByOrderNumber(String orderNumber);

        List<Order> findByUserId(Long userId);

        Page<Order> findByUserId(Long userId, Pageable pageable);

        Page<Order> findByStatus(OrderStatus status, Pageable pageable);

        Page<Order> findByPaymentStatus(PaymentStatus paymentStatus, Pageable pageable);

        /**
         * 🚚 Buscar por estado de envío
         */
        Page<Order> findByShippingStatus(ShippingStatus shippingStatus, Pageable pageable);

        @Query("SELECT o FROM Order o WHERE " +
                        "(o.orderNumber LIKE %:keyword% OR " +
                        "LOWER(o.user.firstName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "LOWER(o.user.lastName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
        Page<Order> searchOrders(@Param("keyword") String keyword, Pageable pageable);

        @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :startDate AND :endDate")
        List<Order> findOrdersByDateRange(@Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        @Query("SELECT SUM(o.total) FROM Order o WHERE o.status != 'CANCELLED' AND o.createdAt BETWEEN :startDate AND :endDate")
        BigDecimal calculateTotalSales(@Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt BETWEEN :startDate AND :endDate")
        Long countOrdersByDateRange(@Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        @Query("SELECT o FROM Order o ORDER BY o.createdAt DESC")
        List<Order> findRecentOrders(Pageable pageable);

        Long countByStatus(OrderStatus status);

        boolean existsByOrderNumber(String orderNumber);
}
