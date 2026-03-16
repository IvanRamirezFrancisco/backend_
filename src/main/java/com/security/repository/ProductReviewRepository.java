package com.security.repository;

import com.security.entity.ProductReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository para reseñas de productos
 */
@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

    /**
     * Encuentra reseñas de un producto (solo aprobadas)
     */
    @Query("SELECT r FROM ProductReview r WHERE r.product.id = :productId AND r.status = 'APPROVED' ORDER BY r.createdAt DESC")
    Page<ProductReview> findApprovedByProductId(@Param("productId") Long productId, Pageable pageable);

    /**
     * Encuentra todas las reseñas de un producto (para admin)
     */
    Page<ProductReview> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);

    /**
     * Encuentra reseñas por usuario
     */
    Page<ProductReview> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Encuentra reseña específica de usuario para producto
     */
    @Query("SELECT r FROM ProductReview r WHERE r.product.id = :productId AND r.user.id = :userId")
    Optional<ProductReview> findByProductIdAndUserId(@Param("productId") Long productId, @Param("userId") Long userId);

    /**
     * Verifica si un usuario ya reseñó un producto
     */
    @Query("SELECT COUNT(r) > 0 FROM ProductReview r WHERE r.product.id = :productId AND r.user.id = :userId")
    boolean existsByProductIdAndUserId(@Param("productId") Long productId, @Param("userId") Long userId);

    /**
     * Encuentra reseñas por calificación
     */
    @Query("SELECT r FROM ProductReview r WHERE r.product.id = :productId AND r.rating = :rating AND r.status = 'APPROVED' ORDER BY r.createdAt DESC")
    Page<ProductReview> findByProductIdAndRating(@Param("productId") Long productId, @Param("rating") Integer rating,
            Pageable pageable);

    /**
     * Encuentra reseñas verificadas (con compra confirmada)
     */
    @Query("SELECT r FROM ProductReview r WHERE r.product.id = :productId AND r.verifiedPurchase = true AND r.status = 'APPROVED' ORDER BY r.createdAt DESC")
    Page<ProductReview> findVerifiedByProductId(@Param("productId") Long productId, Pageable pageable);

    /**
     * Encuentra reseñas pendientes de aprobación
     */
    @Query("SELECT r FROM ProductReview r WHERE r.status = 'PENDING' ORDER BY r.createdAt ASC")
    Page<ProductReview> findPendingReviews(Pageable pageable);

    /**
     * Encuentra reseñas más útiles de un producto
     */
    @Query("SELECT r FROM ProductReview r WHERE r.product.id = :productId AND r.status = 'APPROVED' ORDER BY r.helpfulCount DESC, r.createdAt DESC")
    Page<ProductReview> findMostHelpfulByProductId(@Param("productId") Long productId, Pageable pageable);

    /**
     * Cuenta reseñas por calificación para un producto
     */
    @Query("SELECT r.rating, COUNT(r) FROM ProductReview r WHERE r.product.id = :productId AND r.status = 'APPROVED' GROUP BY r.rating")
    List<Object[]> countByRatingForProduct(@Param("productId") Long productId);

    /**
     * Calcula rating promedio de un producto
     */
    @Query("SELECT AVG(r.rating) FROM ProductReview r WHERE r.product.id = :productId AND r.status = 'APPROVED'")
    Double calculateAverageRating(@Param("productId") Long productId);

    /**
     * Cuenta total de reseñas aprobadas de un producto
     */
    @Query("SELECT COUNT(r) FROM ProductReview r WHERE r.product.id = :productId AND r.status = 'APPROVED'")
    Long countApprovedByProductId(@Param("productId") Long productId);

    /**
     * Encuentra reseñas con respuesta del vendedor
     */
    @Query("SELECT r FROM ProductReview r WHERE r.product.id = :productId AND r.sellerResponse IS NOT NULL AND r.status = 'APPROVED' ORDER BY r.sellerResponseAt DESC")
    Page<ProductReview> findWithSellerResponse(@Param("productId") Long productId, Pageable pageable);

    /**
     * Elimina reseñas de un producto
     */
    void deleteByProductId(Long productId);
}
