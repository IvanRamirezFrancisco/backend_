package com.security.repository;

import com.security.entity.TwoFactorToken;
import com.security.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TwoFactorTokenRepository extends JpaRepository<TwoFactorToken, Long> {

        // Método que usa tu servicio
        @Query("SELECT tft FROM TwoFactorToken tft WHERE tft.token = :token AND tft.user.id = :userId")
        Optional<TwoFactorToken> findByTokenAndUserId(@Param("token") String token, @Param("userId") Long userId);

        // Otros métodos útiles
        Optional<TwoFactorToken> findByToken(String token);

        List<TwoFactorToken> findByUser(User user);

        List<TwoFactorToken> findByUserId(Long userId);

        // Token válido (no usado y no expirado)
        @Query("SELECT tft FROM TwoFactorToken tft WHERE tft.token = :token AND tft.user.id = :userId AND tft.used = false AND tft.expiryDate > :currentTime")
        Optional<TwoFactorToken> findValidToken(@Param("token") String token, @Param("userId") Long userId,
                        @Param("currentTime") LocalDateTime currentTime);

        // Marcar token como usado - método que usa tu servicio
        @Modifying
        @Query("UPDATE TwoFactorToken tft SET tft.used = true, tft.usedAt = :usedAt WHERE tft.id = :tokenId")
        void markTokenAsUsed(@Param("tokenId") Long tokenId, @Param("usedAt") LocalDateTime usedAt);

        // Eliminar tokens expirados - método que usa tu servicio
        @Modifying
        @Query("DELETE FROM TwoFactorToken tft WHERE tft.expiryDate < :currentTime")
        void deleteExpiredTokens(@Param("currentTime") LocalDateTime currentTime);

        // Eliminar por usuario - método que usa tu servicio
        @Modifying
        @Query("DELETE FROM TwoFactorToken tft WHERE tft.user.id = :userId")
        void deleteByUserId(@Param("userId") Long userId);

        // Eliminar tokens antiguos usados - método que usa tu servicio
        @Modifying
        @Query("DELETE FROM TwoFactorToken tft WHERE tft.used = true AND tft.usedAt < :cutoff")
        void deleteOldUsedTokens(@Param("cutoff") LocalDateTime cutoff);

        // Verificaciones útiles
        boolean existsByToken(String token);

        @Query("SELECT COUNT(tft) FROM TwoFactorToken tft WHERE tft.user.id = :userId AND tft.expiryDate > :currentTime AND tft.used = false")
        long countActiveTokensByUserId(@Param("userId") Long userId, @Param("currentTime") LocalDateTime currentTime);

        // Tokens recientes por usuario
        @Query("SELECT tft FROM TwoFactorToken tft WHERE tft.user.id = :userId ORDER BY tft.createdAt DESC")
        List<TwoFactorToken> findRecentTokensByUserId(@Param("userId") Long userId);

        // Limpiar tokens antiguos
        @Query("DELETE FROM TwoFactorToken tft WHERE tft.createdAt < :cutoffDate")
        @Modifying
        void deleteOldTokens(@Param("cutoffDate") LocalDateTime cutoffDate);
}