package com.security.repository;

import com.security.entity.ReviewHelpfulness;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository para votos de utilidad en reseñas
 */
@Repository
public interface ReviewHelpfulnessRepository extends JpaRepository<ReviewHelpfulness, Long> {

    /**
     * Encuentra voto de un usuario en una reseña
     */
    @Query("SELECT rh FROM ReviewHelpfulness rh WHERE rh.review.id = :reviewId AND rh.user.id = :userId")
    Optional<ReviewHelpfulness> findByReviewIdAndUserId(@Param("reviewId") Long reviewId, @Param("userId") Long userId);

    /**
     * Verifica si un usuario ya votó en una reseña
     */
    @Query("SELECT COUNT(rh) > 0 FROM ReviewHelpfulness rh WHERE rh.review.id = :reviewId AND rh.user.id = :userId")
    boolean existsByReviewIdAndUserId(@Param("reviewId") Long reviewId, @Param("userId") Long userId);

    /**
     * Cuenta votos útiles de una reseña
     */
    @Query("SELECT COUNT(rh) FROM ReviewHelpfulness rh WHERE rh.review.id = :reviewId AND rh.isHelpful = true")
    Long countHelpfulByReviewId(@Param("reviewId") Long reviewId);

    /**
     * Cuenta votos no útiles de una reseña
     */
    @Query("SELECT COUNT(rh) FROM ReviewHelpfulness rh WHERE rh.review.id = :reviewId AND rh.isHelpful = false")
    Long countNotHelpfulByReviewId(@Param("reviewId") Long reviewId);

    /**
     * Elimina votos de una reseña
     */
    void deleteByReviewId(Long reviewId);
}
