package com.security.dto.admin;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO de configuración del programador de mantenimiento automático.
 *
 * <p>
 * Se usa tanto para la respuesta GET como para el cuerpo del PUT.
 * Los campos de solo lectura ({@code lastAutoExecution},
 * {@code nextScheduledExecution}
 * y los formateados) son ignorados al deserializar el body del PUT.
 * </p>
 */
public record MaintenanceConfigDto(

        /** true = el scheduler está activo y ejecutará VACUUM automáticamente */
        boolean enabled,

        /** Cada cuántas horas se revisa si hay tablas que limpiar. Rango: 1–24 */
        @Min(value = 1, message = "La frecuencia mínima es 1 hora") @Max(value = 24, message = "La frecuencia máxima es 24 horas") int frequencyHours,

        /** Hora preferida del día (0-23) para ejecutar el mantenimiento */
        @Min(value = 0, message = "La hora mínima es 0") @Max(value = 23, message = "La hora máxima es 23") int preferredHour,

        /** Mínimo de dead tuples para disparar VACUUM. Rango: 1–10 000 */
        @Min(value = 1, message = "El umbral mínimo de obsoletos es 1") @Max(value = 10000, message = "El umbral máximo de obsoletos es 10 000") int vacuumThresholdDeadTuples,

        /**
         * Porcentaje mínimo de bloat para disparar VACUUM (junto con dead tuples).
         * Rango: 1–100
         */
        @NotNull(message = "El umbral de bloat es obligatorio") @DecimalMin(value = "1", message = "El umbral de bloat mínimo es 1 %") @DecimalMax(value = "100", message = "El umbral de bloat máximo es 100 %") BigDecimal vacuumThresholdBloatPct,

        /** Última vez que el scheduler ejecutó VACUUM automáticamente (read-only) */
        LocalDateTime lastAutoExecution,

        /** Próxima ejecución programada (read-only) */
        LocalDateTime nextScheduledExecution,

        /** Texto relativo de la próxima ejecución, p.ej. "en 3h 42min" (read-only) */
        String nextExecutionFormatted,

        /** Texto relativo de la última ejecución, p.ej. "hace 2 horas" (read-only) */
        String lastExecutionFormatted) {
}
