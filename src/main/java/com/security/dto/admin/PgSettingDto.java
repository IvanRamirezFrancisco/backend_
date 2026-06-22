package com.security.dto.admin;

/**
 * Parámetro de configuración de PostgreSQL (solo lectura).
 *
 * @param name      Nombre del parámetro (p.ej. log_min_duration_statement)
 * @param setting   Valor actual
 * @param unit      Unidad (ms, kB, etc.) — puede ser null
 * @param shortDesc Descripción corta del parámetro
 */
public record PgSettingDto(
        String name,
        String setting,
        String unit,
        String shortDesc) {
}
