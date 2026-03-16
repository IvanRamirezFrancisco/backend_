package com.security.repository;

import com.security.entity.CouponUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Repository para registro de uso de cupones
 */
@Repository
public interface CouponUsageRepository extends JpaRepository<CouponUsage, Long> {

    /**
     * Encuentra usos de un cupón
     */
    List<CouponUsage> findByCouponIdOrderByUsedAtDesc(Long couponId);

    /**
     * Encuentra usos de un usuario
     */
    List<CouponUsage> findByUserIdOrderByUsedAtDesc(Long userId);

    /**
     * Cuenta cuántas veces un usuario usó un cupón específico
     */
    @Query("SELECT COUNT(cu) FROM CouponUsage cu WHERE cu.coupon.id = :couponId AND cu.user.id = :userId")
    Long countByCouponIdAndUserId(@Param("couponId") Long couponId, @Param("userId") Long userId);

    /**
     * Verifica si un usuario ya usó un cupón
     */
    @Query("SELECT COUNT(cu) > 0 FROM CouponUsage cu WHERE cu.coupon.id = :couponId AND cu.user.id = :userId")
    boolean existsByCouponIdAndUserId(@Param("couponId") Long couponId, @Param("userId") Long userId);

    /**
     * Calcula el descuento total dado por un cupón
     */
    @Query("SELECT COALESCE(SUM(cu.discountApplied), 0) FROM CouponUsage cu WHERE cu.coupon.id = :couponId")
    BigDecimal sumDiscountByCouponId(@Param("couponId") Long couponId);

    /**
     * Calcula el ingreso total de órdenes con un cupón
     */
    @Query("SELECT COALESCE(SUM(cu.orderTotal), 0) FROM CouponUsage cu WHERE cu.coupon.id = :couponId")
    BigDecimal sumRevenueByCouponId(@Param("couponId") Long couponId);

    /**
     * Top usuarios que más usan un cupón
     */
    @Query("SELECT cu.user.id, cu.user.firstName, cu.user.lastName, COUNT(cu), SUM(cu.discountApplied) " +
            "FROM CouponUsage cu WHERE cu.coupon.id = :couponId " +
            "GROUP BY cu.user.id, cu.user.firstName, cu.user.lastName " +
            "ORDER BY COUNT(cu) DESC")
    List<Object[]> findTopUsersByCouponId(@Param("couponId") Long couponId);

    /**
     * Encuentra usos por orden
     */
    List<CouponUsage> findByOrderId(Long orderId);

    /**
     * Encuentra usos por carrito
     */
    List<CouponUsage> findByCartIdOrderByUsedAtDesc(Long cartId);

    /**
     * Elimina registros de uso de un cupón
     */
    void deleteByCouponId(Long couponId);
}
