package com.security.repository;

import com.security.entity.SmsVerificationCode;
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
public interface SmsVerificationCodeRepository extends JpaRepository<SmsVerificationCode, Long> {

    // Búsqueda por código y teléfono
    Optional<SmsVerificationCode> findByCodeAndPhone(String code, String phone);

    // Búsqueda por usuario
    List<SmsVerificationCode> findByUser(User user);

    List<SmsVerificationCode> findByUserId(Long userId);

    // Búsqueda por teléfono
    List<SmsVerificationCode> findByPhone(String phone);

    // Código válido
    @Query("SELECT svc FROM SmsVerificationCode svc WHERE svc.code = :code AND svc.phone = :phone AND svc.expiryDate > :currentTime AND svc.used = false")
    Optional<SmsVerificationCode> findValidCode(@Param("code") String code, @Param("phone") String phone,
            @Param("currentTime") LocalDateTime currentTime);

    // Marcar como usado
    @Modifying
    @Query("UPDATE SmsVerificationCode svc SET svc.used = true WHERE svc.id = :codeId")
    void markCodeAsUsed(@Param("codeId") Long codeId);

    // Eliminar códigos no usados por usuario y teléfono
    @Modifying
    @Query("DELETE FROM SmsVerificationCode svc WHERE svc.user = :user AND svc.phone = :phone AND svc.used = false")
    void deleteByUserAndPhoneAndUsedFalse(@Param("user") User user, @Param("phone") String phone);

    // Eliminar códigos expirados
    @Modifying
    @Query("DELETE FROM SmsVerificationCode svc WHERE svc.expiryDate < :currentTime")
    void deleteExpiredCodes(@Param("currentTime") LocalDateTime currentTime);

    // Incrementar intentos
    @Modifying
    @Query("UPDATE SmsVerificationCode svc SET svc.attempts = svc.attempts + 1 WHERE svc.id = :codeId")
    void incrementAttempts(@Param("codeId") Long codeId);

    // Eliminar por usuario
    @Modifying
    @Query("DELETE FROM SmsVerificationCode svc WHERE svc.user = :user")
    void deleteByUser(@Param("user") User user);

    // Contar intentos recientes
    @Query("SELECT COUNT(svc) FROM SmsVerificationCode svc WHERE svc.phone = :phone AND svc.createdAt > :timeLimit")
    long countRecentAttempts(@Param("phone") String phone, @Param("timeLimit") LocalDateTime timeLimit);

    // Códigos activos por usuario
    @Query("SELECT COUNT(svc) FROM SmsVerificationCode svc WHERE svc.user.id = :userId AND svc.expiryDate > :currentTime AND svc.used = false")
    long countActiveCodesByUserId(@Param("userId") Long userId, @Param("currentTime") LocalDateTime currentTime);
}