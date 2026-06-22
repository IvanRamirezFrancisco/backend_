package com.security.dto.admin;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO de solo lectura para enviar datos de automatización al frontend.
 *
 * <p>
 * Se construye desde {@link com.security.entity.SystemAutomation}
 * en la capa de servicio para evitar exponer la entidad JPA directamente.
 * </p>
 */
public record AutomationDto(
        Long id,
        String jobName,
        String jobGroup,
        String displayName,
        String description,
        String iconName,
        boolean enabled,
        String cronExpression,
        String timezone,
        Map<String, Object> parameters,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime lastExecution,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime nextExecution,

        Long lastDurationMs,
        String lastStatus,
        String errorMessage,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime createdAt,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime updatedAt) {

    /**
     * Factory method desde la entidad JPA.
     */
    public static AutomationDto from(com.security.entity.SystemAutomation entity) {
        return new AutomationDto(
                entity.getId(),
                entity.getJobName(),
                entity.getJobGroup(),
                entity.getDisplayName(),
                entity.getDescription(),
                entity.getIconName(),
                entity.isEnabled(),
                entity.getCronExpression(),
                entity.getTimezone(),
                entity.getParameters(),
                entity.getLastExecution(),
                entity.getNextExecution(),
                entity.getLastDurationMs(),
                entity.getLastStatus(),
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
