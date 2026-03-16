package com.security.repository;

import com.security.entity.Coupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository para cupones de descuento
 */
@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /**
     * Encuentra cupón por código
     */
    Optional<Coupon> findByCode(String code);

    /**
     * Encuentra cupón por código (case insensitive)
     */
    @Query("SELECT c FROM Coupon c WHERE UPPER(c.code) = UPPER(:code)")
    Optional<Coupon> findByCodeIgnoreCase(@Param("code") String code);

    /**
     * Verifica si existe un código
     */
    boolean existsByCode(String code);

    /**
     * Encuentra cupones activos
     */
    @Query("SELECT c FROM Coupon c WHERE c.isActive = true AND " +
            "(c.validFrom IS NULL OR c.validFrom <= :now) AND " +
            "(c.validUntil IS NULL OR c.validUntil >= :now) AND " +
            "(c.usageLimit IS NULL OR c.timesUsed < c.usageLimit)")
    List<Coupon> findActiveCoupons(@Param("now") LocalDateTime now);

    /**
     * Encuentra cupones activos paginados
     */
    @Query("SELECT c FROM Coupon c WHERE c.isActive = true ORDER BY c.createdAt DESC")
    Page<Coupon> findActiveCoupons(Pageable pageable);

    /**
     * Encuentra cupones por tipo de descuento
     */
    Page<Coupon> findByDiscountTypeOrderByCreatedAtDesc(String discountType, Pageable pageable);

    /**
     * Encuentra cupones expirados que siguen activos
     */
    @Query("SELECT c FROM Coupon c WHERE c.isActive = true AND c.validUntil < :now")
    List<Coupon> findExpiredButActive(@Param("now") LocalDateTime now);

    /**
     * Encuentra cupones que alcanzaron el límite de uso
     */
    @Query("SELECT c FROM Coupon c WHERE c.isActive = true AND c.usageLimit IS NOT NULL AND c.timesUsed >= c.usageLimit")
    List<Coupon> findReachedUsageLimit();

    /**
     * Desactiva cupones expirados
     */
    @Modifying
    @Query("UPDATE Coupon c SET c.isActive = false WHERE c.isActive = true AND c.validUntil < :now")
    int deactivateExpiredCoupons(@Param("now") LocalDateTime now);

    /**
     * Encuentra cupones próximos a expirar
     */
    @Query("SELECT c FROM Coupon c WHERE c.isActive = true AND c.validUntil BETWEEN :now AND :threshold ORDER BY c.validUntil ASC")
    List<Coupon> findExpiringCoupons(@Param("now") LocalDateTime now, @Param("threshold") LocalDateTime threshold);

    /**
     * Encuentra cupones para primera compra
     */
    @Query("SELECT c FROM Coupon c WHERE c.isActive = true AND c.firstPurchaseOnly = true")
    List<Coupon> findFirstPurchaseCoupons();

    /**
     * Busca cupones por descripción
     */
    @Query("SELECT c FROM Coupon c WHERE LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Coupon> searchByDescription(@Param("keyword") String keyword, Pageable pageable);

    /**
     * Cuenta cupones activos
     */
    @Query("SELECT COUNT(c) FROM Coupon c WHERE c.isActive = true")
    Long countActiveCoupons();

    /**
     * Estadísticas de uso de cupones
     */
    @Query("SELECT c.discountType, COUNT(c), SUM(c.timesUsed) FROM Coupon c GROUP BY c.discountType")
    List<Object[]> getCouponStatistics();
}
