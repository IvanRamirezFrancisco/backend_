package com.security.dto.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.security.entity.AutomationExecutionLog;

import java.time.LocalDateTime;

/**
 * DTO de solo lectura para enviar el historial de ejecuciones al frontend.
 *
 * <p>
 * Se construye desde {@link AutomationExecutionLog} para evitar exponer
 * la entidad JPA y las relaciones Lazy directamente.
 * </p>
 */
public record ExecutionLogDto(
        Long id,
        Long automationId,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime startedAt,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime finishedAt,

        String status,
        String triggeredBy,
        Long durationMs,
        String resultSummary,
        String errorMessage) {

    /**
     * Factory method desde la entidad JPA.
     */
    public static ExecutionLogDto from(AutomationExecutionLog entity) {
        return new ExecutionLogDto(
                entity.getId(),
                entity.getAutomation().getId(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getStatus(),
                entity.getTriggeredBy(),
                entity.getDurationMs(),
                entity.getResultSummary(),
                entity.getErrorMessage());
    }
}
