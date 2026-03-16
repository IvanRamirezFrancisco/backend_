package com.security.enums;

/**
 * Define la estrategia a aplicar cuando un SKU del CSV ya existe en la base de
 * datos.
 *
 * <ul>
 * <li>{@link #UPDATE} – Sobrescribe precio, stock y campos opcionales del
 * producto existente.</li>
 * <li>{@link #SKIP} – Ignora la fila; el producto existente no se
 * modifica.</li>
 * </ul>
 */
public enum CollisionRule {
    UPDATE,
    SKIP
}
