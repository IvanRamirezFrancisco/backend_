package com.security.repository;

import com.security.entity.ActiveSession;
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
public interface ActiveSessionRepository extends JpaRepository<ActiveSession, Long> {

    // Búsqueda por token JWT
    Optional<ActiveSession> findByJwtTokenId(String jwtTokenId);

    // Búsqueda por usuario
    List<ActiveSession> findByUser(User user);

    List<ActiveSession> findByUserId(Long userId);

    // Sesiones activas (no revocadas y no expiradas)
    @Query("SELECT ase FROM ActiveSession ase WHERE ase.user = :user AND ase.revoked = false AND ase.expiresAt > :currentTime ORDER BY ase.createdAt ASC")
    List<ActiveSession> findActiveSessionsByUser(@Param("user") User user, @Param("currentTime") LocalDateTime currentTime);

    default List<ActiveSession> findActiveSessionsByUser(User user) {
        return findActiveSessionsByUser(user, LocalDateTime.now());
    }

    // Sesiones válidas (no revocadas y no expiradas)
    @Query("SELECT ase FROM ActiveSession ase WHERE ase.user = :user AND ase.revoked = false AND ase.expiresAt > :currentTime")
    List<ActiveSession> findValidSessionsByUser(@Param("user") User user,
            @Param("currentTime") LocalDateTime currentTime);

    // Sesiones válidas con verificación de inactividad
    @Query("SELECT ase FROM ActiveSession ase WHERE ase.user = :user AND ase.revoked = false " +
            "AND ase.expiresAt > :currentTime AND ase.lastActivity > :inactivityThreshold")
    List<ActiveSession> findValidAndActiveSessionsByUser(@Param("user") User user,
            @Param("currentTime") LocalDateTime currentTime,
            @Param("inactivityThreshold") LocalDateTime inactivityThreshold);

    // Buscar sesiones expiradas o inactivas
    @Query("SELECT ase FROM ActiveSession ase WHERE ase.revoked = false AND " +
            "(ase.expiresAt <= :currentTime OR ase.lastActivity <= :inactivityThreshold)")
    List<ActiveSession> findExpiredOrInactiveSessions(@Param("currentTime") LocalDateTime currentTime,
            @Param("inactivityThreshold") LocalDateTime inactivityThreshold);

    // Buscar sesiones más antiguas de un usuario (para límite de sesiones)
    @Query("SELECT ase FROM ActiveSession ase WHERE ase.user = :user AND ase.revoked = false " +
            "ORDER BY ase.lastActivity ASC")
    List<ActiveSession> findOldestSessionsByUser(@Param("user") User user);

    // Sesiones por IP
    List<ActiveSession> findByIpAddress(String ipAddress);

    // Revocar sesión
    @Modifying
    @Query("UPDATE ActiveSession ase SET ase.revoked = true WHERE ase.id = :sessionId")
    void revokeSession(@Param("sessionId") Long sessionId);

    // Revocar todas las sesiones de un usuario
    @Modifying
    @Query("UPDATE ActiveSession ase SET ase.revoked = true WHERE ase.user = :user")
    void revokeAllUserSessions(@Param("user") User user);

    // Actualizar última actividad
    @Modifying
    @Query("UPDATE ActiveSession ase SET ase.lastActivity = :currentTime WHERE ase.jwtTokenId = :tokenId AND ase.revoked = false")
    void updateLastActivity(@Param("tokenId") String tokenId, @Param("currentTime") LocalDateTime currentTime);

    // Revocar por token ID
    @Modifying
    @Query("UPDATE ActiveSession ase SET ase.revoked = true WHERE ase.jwtTokenId = :tokenId")
    void revokeByTokenId(@Param("tokenId") String tokenId);

    // Eliminar sesiones expiradas
    @Modifying
    @Query("DELETE FROM ActiveSession ase WHERE ase.expiresAt < :currentTime")
    void deleteExpiredSessions(@Param("currentTime") LocalDateTime currentTime);

    // Eliminar sesiones revocadas antigas
    @Modifying
    @Query("DELETE FROM ActiveSession ase WHERE ase.revoked = true AND ase.createdAt < :cutoffDate")
    void deleteOldRevokedSessions(@Param("cutoffDate") LocalDateTime cutoffDate);

    // Contar sesiones activas con inactividad
    @Query("SELECT COUNT(ase) FROM ActiveSession ase WHERE ase.user.id = :userId AND ase.revoked = false " +
            "AND ase.expiresAt > :currentTime AND ase.lastActivity > :inactivityThreshold")
    long countActiveAndNotInactiveSessionsByUserId(@Param("userId") Long userId,
            @Param("currentTime") LocalDateTime currentTime,
            @Param("inactivityThreshold") LocalDateTime inactivityThreshold);

    // Verificar si el token está revocado
    @Query("SELECT CASE WHEN COUNT(ase) > 0 THEN true ELSE false END FROM ActiveSession ase WHERE ase.jwtTokenId = :tokenId AND ase.revoked = true")
    boolean isTokenRevoked(@Param("tokenId") String tokenId);

    // Sesiones recientes
    @Query("SELECT ase FROM ActiveSession ase WHERE ase.user = :user ORDER BY ase.createdAt DESC")
    List<ActiveSession> findRecentSessionsByUser(@Param("user") User user);
}