package com.security.dto.admin;

import java.time.LocalDateTime;

/**
 * DTO de solo lectura que expone un registro del historial de mantenimiento
 * al frontend.
 *
 * <p>
 * El campo {@code executedAtRelative} se calcula en el servicio antes de
 * serializar, expresando el tiempo transcurrido en lenguaje natural
 * (ej: "hace 5 minutos").
 * </p>
 *
 * @param id                 identificador único del registro
 * @param operation          tipo de operación: VACUUM_ANALYZE, REINDEX, ANALYZE
 * @param targetName         nombre de la tabla o índice
 * @param targetType         TABLE o INDEX
 * @param executedBy         nombre del administrador que ejecutó la operación
 * @param executedAt         timestamp exacto de ejecución
 * @param executedAtRelative tiempo relativo legible (ej: "hace 5 minutos")
 * @param rowsBefore         tuplas muertas antes (null para REINDEX)
 * @param rowsAfter          tuplas muertas después (null para REINDEX)
 * @param rowsAffected       filas eliminadas (calculado por PostgreSQL)
 * @param durationMs         duración en milisegundos
 * @param status             IN_PROGRESS, SUCCESS o ERROR
 * @param errorMessage       mensaje de error si status = ERROR, null en otro
 *                           caso
 */
public record MaintenanceLogDto(
        Long id,
        String operation,
        String targetName,
        String targetType,
        String executedBy,
        LocalDateTime executedAt,
        String executedAtRelative,
        Integer rowsBefore,
        Integer rowsAfter,
        Integer rowsAffected,
        Integer durationMs,
        String status,
        String errorMessage) {
}
