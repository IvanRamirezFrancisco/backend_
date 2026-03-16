package com.security.repository;

import com.security.entity.Product;
import com.security.entity.ProductPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio para historial de precios de productos
 */
@Repository
public interface ProductPriceHistoryRepository extends JpaRepository<ProductPriceHistory, Long> {

    /**
     * Buscar historial de un producto ordenado por fecha
     */
    List<ProductPriceHistory> findByProductOrderByEffectiveFromDesc(Product product);

    /**
     * Buscar precio actual (vigente) de un producto
     */
    @Query("SELECT p FROM ProductPriceHistory p WHERE p.product = :product AND p.effectiveTo IS NULL")
    Optional<ProductPriceHistory> findCurrentPriceByProduct(@Param("product") Product product);

    /**
     * Buscar historial de precios en un rango de fechas
     */
    @Query("SELECT p FROM ProductPriceHistory p WHERE p.product = :product " +
            "AND p.effectiveFrom BETWEEN :startDate AND :endDate " +
            "ORDER BY p.effectiveFrom DESC")
    List<ProductPriceHistory> findByProductAndDateRange(
            @Param("product") Product product,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Buscar cambios de precio realizados por un usuario específico
     */
    @Query("SELECT p FROM ProductPriceHistory p WHERE p.changedBy.id = :userId ORDER BY p.createdAt DESC")
    List<ProductPriceHistory> findByChangedByUserId(@Param("userId") Long userId);

    /**
     * Buscar últimos N cambios de precio de un producto
     */
    @Query("SELECT p FROM ProductPriceHistory p WHERE p.product = :product ORDER BY p.effectiveFrom DESC")
    List<ProductPriceHistory> findTopNByProduct(@Param("product") Product product);

    /**
     * Contar cambios de precio de un producto
     */
    long countByProduct(Product product);

    /**
     * Buscar productos con cambios de precio recientes (últimas 24 horas)
     */
    @Query("SELECT DISTINCT p.product FROM ProductPriceHistory p WHERE p.createdAt >= :since")
    List<Product> findProductsWithRecentPriceChanges(@Param("since") LocalDateTime since);
}
