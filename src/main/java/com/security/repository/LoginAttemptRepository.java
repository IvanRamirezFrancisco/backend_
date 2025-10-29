package com.security.repository;

import com.security.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    // Búsqueda por email
    List<LoginAttempt> findByEmail(String email);

    // Búsqueda por IP
    List<LoginAttempt> findByIpAddress(String ipAddress);

    // Intentos fallidos recientes por email
    @Query("SELECT la FROM LoginAttempt la WHERE la.email = :email AND la.successful = false AND la.attemptTime > :timeLimit ORDER BY la.attemptTime DESC")
    List<LoginAttempt> findRecentFailedAttemptsByEmail(@Param("email") String email,
            @Param("timeLimit") LocalDateTime timeLimit);

    // Intentos fallidos recientes por IP
    @Query("SELECT la FROM LoginAttempt la WHERE la.ipAddress = :ipAddress AND la.successful = false AND la.attemptTime > :timeLimit ORDER BY la.attemptTime DESC")
    List<LoginAttempt> findRecentFailedAttemptsByIp(@Param("ipAddress") String ipAddress,
            @Param("timeLimit") LocalDateTime timeLimit);

    // Contar intentos fallidos recientes
    @Query("SELECT COUNT(la) FROM LoginAttempt la WHERE la.email = :email AND la.successful = false AND la.attemptTime > :timeLimit")
    long countRecentFailedAttemptsByEmail(@Param("email") String email, @Param("timeLimit") LocalDateTime timeLimit);

    @Query("SELECT COUNT(la) FROM LoginAttempt la WHERE la.ipAddress = :ipAddress AND la.successful = false AND la.attemptTime > :timeLimit")
    long countRecentFailedAttemptsByIp(@Param("ipAddress") String ipAddress,
            @Param("timeLimit") LocalDateTime timeLimit);

    // Intentos exitosos
    List<LoginAttempt> findByEmailAndSuccessfulTrue(String email);

    // Últimos intentos
    @Query("SELECT la FROM LoginAttempt la WHERE la.email = :email ORDER BY la.attemptTime DESC")
    List<LoginAttempt> findRecentAttemptsByEmail(@Param("email") String email);

    // Estadísticas por rango de fechas
    @Query("SELECT COUNT(la) FROM LoginAttempt la WHERE la.attemptTime BETWEEN :startDate AND :endDate AND la.successful = :successful")
    long countAttemptsByDateRangeAndStatus(@Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("successful") Boolean successful);

    // Limpiar intentos antiguos
    void deleteByAttemptTimeBefore(LocalDateTime cutoffDate);
}