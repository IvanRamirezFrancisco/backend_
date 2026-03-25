package com.security.dto.admin;

/**
 * Alerta activa generada automáticamente a partir de las métricas de la BD.
 *
 * @param id       Identificador único de la alerta (generado en servicio)
 * @param level    "warning" | "critical"
 * @param category Categoría legible ("Tablas", "Índices", "Conexiones",
 *                 "Rendimiento")
 * @param message  Descripción del problema en lenguaje natural
 * @param value    Valor actual que dispara la alerta
 * @param hint     Recomendación de acción para el administrador
 */
public record DbAlertDto(
        String id,
        String level,
        String category,
        String message,
        String value,
        String hint) {
}
