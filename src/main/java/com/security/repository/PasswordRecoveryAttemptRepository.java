package com.security.repository;

import com.security.entity.PasswordRecoveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio para manejar intentos de recuperación de contraseña
 * Implementa consultas para rate limiting y auditoría
 */
@Repository
public interface PasswordRecoveryAttemptRepository extends JpaRepository<PasswordRecoveryAttempt, Long> {

        /**
         * Busca intentos por email e IP
         */
        Optional<PasswordRecoveryAttempt> findByEmailAndIpAddress(String email, String ipAddress);

        /**
         * ✅ Busca el intento más reciente solo por email (para usuarios reales)
         * Esto permite que el bloqueo sea consistente entre navegadores/IPs
         */
        @Query("SELECT p FROM PasswordRecoveryAttempt p WHERE p.email = :email " +
                        "ORDER BY p.lastAttempt DESC")
        Optional<PasswordRecoveryAttempt> findMostRecentByEmail(@Param("email") String email);

        /**
         * ✅ Busca el intento más reciente solo por IP (para rate limiting de emails
         * inexistentes)
         */
        @Query("SELECT p FROM PasswordRecoveryAttempt p WHERE p.ipAddress = :ipAddress " +
                        "ORDER BY p.lastAttempt DESC")
        Optional<PasswordRecoveryAttempt> findMostRecentByIp(@Param("ipAddress") String ipAddress);

        /**
         * Busca intentos activos por email en la última hora
         */
        @Query("SELECT p FROM PasswordRecoveryAttempt p WHERE p.email = :email " +
                        "AND p.lastAttempt > :since ORDER BY p.lastAttempt DESC")
        List<PasswordRecoveryAttempt> findActiveAttemptsByEmail(@Param("email") String email,
                        @Param("since") LocalDateTime since);

        /**
         * Busca intentos activos por IP en la última hora
         */
        @Query("SELECT p FROM PasswordRecoveryAttempt p WHERE p.ipAddress = :ipAddress " +
                        "AND p.lastAttempt > :since ORDER BY p.lastAttempt DESC")
        List<PasswordRecoveryAttempt> findActiveAttemptsByIp(@Param("ipAddress") String ipAddress,
                        @Param("since") LocalDateTime since);

        /**
         * Cuenta intentos por email en un período específico
         */
        @Query("SELECT COUNT(p) FROM PasswordRecoveryAttempt p WHERE p.email = :email " +
                        "AND p.lastAttempt BETWEEN :start AND :end")
        int countAttemptsByEmailInPeriod(@Param("email") String email,
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

        /**
         * Cuenta intentos por IP en un período específico
         */
        @Query("SELECT COUNT(p) FROM PasswordRecoveryAttempt p WHERE p.ipAddress = :ipAddress " +
                        "AND p.lastAttempt BETWEEN :start AND :end")
        int countAttemptsByIpInPeriod(@Param("ipAddress") String ipAddress,
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

        /**
         * Busca registros bloqueados actualmente
         */
        @Query("SELECT p FROM PasswordRecoveryAttempt p WHERE p.blocked = true " +
                        "AND (p.blockedUntil IS NULL OR p.blockedUntil > :now)")
        List<PasswordRecoveryAttempt> findCurrentlyBlocked(@Param("now") LocalDateTime now);

        /**
         * Busca registros antiguos para limpieza (más de 24 horas sin actividad)
         */
        @Query("SELECT p FROM PasswordRecoveryAttempt p WHERE p.lastAttempt < :cutoff")
        List<PasswordRecoveryAttempt> findOldRecordsForCleanup(@Param("cutoff") LocalDateTime cutoff);

        /**
         * Elimina registros antiguos para limpieza automática
         */
        @Query("DELETE FROM PasswordRecoveryAttempt p WHERE p.lastAttempt < :cutoff")
        void deleteOldRecords(@Param("cutoff") LocalDateTime cutoff);

        /**
         * Busca todos los intentos de un email específico para auditoría
         */
        @Query("SELECT p FROM PasswordRecoveryAttempt p WHERE p.email = :email " +
                        "ORDER BY p.lastAttempt DESC")
        List<PasswordRecoveryAttempt> findAllByEmailOrderByLastAttemptDesc(@Param("email") String email);

        /**
         * Busca todos los registros de recuperación activos (bloqueados o no) para un
         * email dado — usado por el administrador para resetear el bloqueo.
         */
        @Query("SELECT p FROM PasswordRecoveryAttempt p WHERE p.email = :email")
        List<PasswordRecoveryAttempt> findAllByEmail(@Param("email") String email);

        /**
         * Busca todos los intentos de una IP específica para auditoría
         */
        @Query("SELECT p FROM PasswordRecoveryAttempt p WHERE p.ipAddress = :ipAddress " +
                        "ORDER BY p.lastAttempt DESC")
        List<PasswordRecoveryAttempt> findAllByIpAddressOrderByLastAttemptDesc(@Param("ipAddress") String ipAddress);
}