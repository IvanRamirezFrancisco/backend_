package com.security.service;

import com.security.entity.ProductReview;
import com.security.entity.ReviewHelpfulness;
import com.security.entity.User;
import com.security.exception.DuplicateActionException;
import com.security.exception.ResourceNotFoundException;
import com.security.repository.ProductReviewRepository;
import com.security.repository.ReviewHelpfulnessRepository;
import com.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service para gestión de votos de utilidad en reseñas
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReviewHelpfulnessService {

    private final ReviewHelpfulnessRepository helpfulnessRepository;
    private final ProductReviewRepository reviewRepository;
    private final UserRepository userRepository;

    /**
     * Vota por utilidad de una reseña
     */
    @Transactional
    public void voteHelpful(Long reviewId, Long userId, Boolean isHelpful) {
        log.info("Usuario {} votando {} en reseña {}", userId, isHelpful ? "útil" : "no útil", reviewId);

        ProductReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Reseña no encontrada"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // Verificar si ya votó
        if (helpfulnessRepository.existsByReviewIdAndUserId(reviewId, userId)) {
            throw new DuplicateActionException("Ya has votado por esta reseña");
        }

        // Crear voto
        ReviewHelpfulness vote = new ReviewHelpfulness();
        vote.setReview(review);
        vote.setUser(user);
        vote.setIsHelpful(isHelpful);

        helpfulnessRepository.save(vote);

        // Actualizar contadores en la reseña
        if (isHelpful) {
            review.incrementHelpfulCount();
        } else {
            review.incrementNotHelpfulCount();
        }

        reviewRepository.save(review);

        log.info("Voto registrado para reseña {}", reviewId);
    }

    /**
     * Cambia el voto de un usuario
     */
    @Transactional
    public void changeVote(Long reviewId, Long userId, Boolean newVote) {
        log.info("Usuario {} cambiando voto en reseña {}", userId, reviewId);

        ReviewHelpfulness vote = helpfulnessRepository.findByReviewIdAndUserId(reviewId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Voto no encontrado"));

        ProductReview review = vote.getReview();

        // Revertir voto anterior
        if (vote.getIsHelpful()) {
            review.setHelpfulCount(review.getHelpfulCount() - 1);
        } else {
            review.setNotHelpfulCount(review.getNotHelpfulCount() - 1);
        }

        // Aplicar nuevo voto
        vote.setIsHelpful(newVote);
        if (newVote) {
            review.incrementHelpfulCount();
        } else {
            review.incrementNotHelpfulCount();
        }

        helpfulnessRepository.save(vote);
        reviewRepository.save(review);

        log.info("Voto actualizado para reseña {}", reviewId);
    }

    /**
     * Elimina el voto de un usuario
     */
    @Transactional
    public void removeVote(Long reviewId, Long userId) {
        log.info("Usuario {} eliminando voto de reseña {}", userId, reviewId);

        ReviewHelpfulness vote = helpfulnessRepository.findByReviewIdAndUserId(reviewId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Voto no encontrado"));

        ProductReview review = vote.getReview();

        // Decrementar contador correspondiente
        if (vote.getIsHelpful()) {
            review.setHelpfulCount(Math.max(0, review.getHelpfulCount() - 1));
        } else {
            review.setNotHelpfulCount(Math.max(0, review.getNotHelpfulCount() - 1));
        }

        reviewRepository.save(review);
        helpfulnessRepository.delete(vote);

        log.info("Voto eliminado de reseña {}", reviewId);
    }

    /**
     * Verifica si un usuario ya votó por una reseña
     */
    @Transactional(readOnly = true)
    public boolean hasUserVoted(Long reviewId, Long userId) {
        return helpfulnessRepository.existsByReviewIdAndUserId(reviewId, userId);
    }

    /**
     * Obtiene el voto de un usuario en una reseña
     */
    @Transactional(readOnly = true)
    public Boolean getUserVote(Long reviewId, Long userId) {
        return helpfulnessRepository.findByReviewIdAndUserId(reviewId, userId)
                .map(ReviewHelpfulness::getIsHelpful)
                .orElse(null);
    }

    /**
     * Obtiene contadores de una reseña
     */
    @Transactional(readOnly = true)
    public VoteCounters getVoteCounters(Long reviewId) {
        Long helpfulCount = helpfulnessRepository.countHelpfulByReviewId(reviewId);
        Long notHelpfulCount = helpfulnessRepository.countNotHelpfulByReviewId(reviewId);

        return new VoteCounters(
                helpfulCount != null ? helpfulCount.intValue() : 0,
                notHelpfulCount != null ? notHelpfulCount.intValue() : 0);
    }

    /**
     * Clase interna para contadores de votos
     */
    public record VoteCounters(int helpfulCount, int notHelpfulCount) {
        public int getTotalVotes() {
            return helpfulCount + notHelpfulCount;
        }

        public double getHelpfulPercentage() {
            int total = getTotalVotes();
            return total > 0 ? (helpfulCount * 100.0 / total) : 0.0;
        }
    }
}
