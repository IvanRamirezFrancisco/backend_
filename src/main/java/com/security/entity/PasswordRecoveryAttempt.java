package com.security.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entidad para controlar intentos de recuperación de contraseña por IP/Email
 * Implementa rate limiting y retrasos progresivos
 */
@Entity
@Table(name = "password_recovery_attempts")
public class PasswordRecoveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "last_attempt", nullable = false)
    private LocalDateTime lastAttempt;

    @Column(name = "blocked_until")
    private LocalDateTime blockedUntil;

    @Column(name = "is_blocked", nullable = false)
    private boolean blocked = false;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Constructor principal
     */
    public PasswordRecoveryAttempt(String email, String ipAddress, String userAgent) {
        this.email = email;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.attemptCount = 1;
        this.lastAttempt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.blocked = false;
    }

    /**
     * Incrementa el contador de intentos
     */
    public void incrementAttempt() {
        this.attemptCount++;
        this.lastAttempt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Aplica bloqueo con retraso progresivo
     */
    public void applyProgressiveBlock() {
        this.blocked = true;
        this.updatedAt = LocalDateTime.now();

        // Retraso progresivo basado en el número de intentos
        int delayMinutes;
        switch (attemptCount) {
            case 1, 2, 3:
                delayMinutes = 5; // 5 minutos para los primeros 3 intentos
                break;
            case 4, 5:
                delayMinutes = 15; // 15 minutos para intentos 4-5
                break;
            case 6, 7, 8:
                delayMinutes = 60; // 1 hora para intentos 6-8
                break;
            default:
                delayMinutes = 240; // 4 horas para 9+ intentos
        }

        this.blockedUntil = LocalDateTime.now().plusMinutes(delayMinutes);
    }

    /**
     * Verifica si el bloqueo ha expirado
     */
    public boolean isBlockExpired() {
        if (blockedUntil == null)
            return true;
        return LocalDateTime.now().isAfter(blockedUntil);
    }

    /**
     * Verifica si está actualmente bloqueado
     */
    public boolean isCurrentlyBlocked() {
        return blocked && !isBlockExpired();
    }

    /**
     * Desbloquea y resetea si ha pasado el tiempo
     */
    public void resetIfExpired() {
        if (blocked && isBlockExpired()) {
            this.blocked = false;
            this.blockedUntil = null;
            this.attemptCount = 0; // CRUCIAL: Resetear contador cuando expira el bloqueo
            this.updatedAt = LocalDateTime.now();
        }
    }

    /**
     * Verifica si necesita ser limpiado (más de 24 horas sin actividad)
     */
    public boolean shouldBeCleanedUp() {
        return lastAttempt.isBefore(LocalDateTime.now().minusHours(24));
    }

    /**
     * Resetea completamente los intentos
     */
    public void reset() {
        this.attemptCount = 0;
        this.blocked = false;
        this.blockedUntil = null;
        this.lastAttempt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Constructors
    public PasswordRecoveryAttempt() {
        this.createdAt = LocalDateTime.now();
        this.lastAttempt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public LocalDateTime getLastAttempt() {
        return lastAttempt;
    }

    public void setLastAttempt(LocalDateTime lastAttempt) {
        this.lastAttempt = lastAttempt;
    }

    public LocalDateTime getBlockedUntil() {
        return blockedUntil;
    }

    public void setBlockedUntil(LocalDateTime blockedUntil) {
        this.blockedUntil = blockedUntil;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}