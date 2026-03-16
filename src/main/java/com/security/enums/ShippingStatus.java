package com.security.enums;

/**
 * 🚚 Estados posibles del envío de una orden
 * 
 * Flujo normal:
 * PENDING → PREPARING → SHIPPED → IN_TRANSIT → DELIVERED
 * 
 * Flujo alternativo:
 * ... → RETURNED (si hay devolución)
 */
public enum ShippingStatus {

    /**
     * ⏳ Pendiente - La orden aún no ha sido preparada para envío
     */
    PENDING("Pendiente de Envío"),

    /**
     * 📦 Preparando - El paquete está siendo preparado
     */
    PREPARING("Preparando Paquete"),

    /**
     * 🚚 Enviado - El paquete ha sido enviado
     */
    SHIPPED("Enviado"),

    /**
     * 🛫 En Tránsito - El paquete está en camino
     */
    IN_TRANSIT("En Tránsito"),

    /**
     * ✅ Entregado - El paquete fue entregado al cliente
     */
    DELIVERED("Entregado"),

    /**
     * ↩️ Devuelto - El paquete fue devuelto
     */
    RETURNED("Devuelto");

    private final String displayName;

    ShippingStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
