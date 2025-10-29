package com.security.repository;

import com.security.entity.RefreshToken;
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
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // Búsqueda por token
    Optional<RefreshToken> findByToken(String token);

    // Búsqueda por usuario
    List<RefreshToken> findByUser(User user);

    Optional<RefreshToken> findByUserId(Long userId);

    // Verificar existencia
    boolean existsByToken(String token);

    boolean existsByUserId(Long userId);

    // Eliminación por usuario
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.user = :user")
    void deleteByUser(@Param("user") User user);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    // Limpieza de tokens expirados
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiryDate < :currentTime")
    void deleteExpiredTokens(@Param("currentTime") LocalDateTime currentTime);

    // Búsqueda de tokens expirados
    List<RefreshToken> findByExpiryDateBefore(LocalDateTime currentTime);

    // Tokens válidos (no expirados)
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.expiryDate > :currentTime")
    List<RefreshToken> findValidTokens(@Param("currentTime") LocalDateTime currentTime);

    // Contar tokens por usuario
    long countByUserId(Long userId);

    // Últimos tokens creados
    @Query("SELECT rt FROM RefreshToken rt ORDER BY rt.createdAt DESC")
    List<RefreshToken> findRecentTokens();

    // Tokens por rango de fechas
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.createdAt BETWEEN :startDate AND :endDate")
    List<RefreshToken> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}
