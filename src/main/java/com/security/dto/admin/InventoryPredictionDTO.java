package com.security.dto.admin;

/**
 * DTO de predicción de agotamiento de inventario.
 *
 * <p>
 * Representa el resultado del modelo de decaimiento exponencial
 * {@code I(t) = I₀ · e^(−k·t)} para una sección del catálogo.
 * </p>
 *
 * @param sectionName  Nombre legible de la sección (ej. "Jaranas Huastecas")
 * @param sectionKey   Clave corta para el frontend (ej. "jaranas")
 * @param i0           Stock inicial del período (I₀)
 * @param iCurrent     Stock actual (I_actual)
 * @param iCrit        Nivel crítico configurado (I_crit)
 * @param k            Constante de agotamiento calculada k = −ln(I_actual/I₀)/t
 * @param daysToAlert  Días proyectados hasta alcanzar el nivel crítico
 * @param currentStock Stock actual en unidades enteras
 * @param status       Estado semáforo: CRITICAL / WARNING / STABLE
 */
public record InventoryPredictionDTO(
        String sectionName,
        String sectionKey,
        double i0,
        double iCurrent,
        double iCrit,
        double k,
        double daysToAlert,
        int currentStock,
        String status) {
}
