package com.security.enums;

/**
 * 📊 Estados del ciclo de vida de una orden
 * 
 * Flujo normal:
 * PENDING → CONFIRMED → PROCESSING → COMPLETED
 * 
 * Flujo alternativo:
 * ... → CANCELLED (en cualquier momento antes de COMPLETED)
 * 
 * Nota: Los estados de envío se manejan en ShippingStatus
 */
public enum OrderStatus {

    /**
     * ⏳ Pendiente - La orden fue creada pero no confirmada
     */
    PENDING("Pendiente"),

    /**
     * ✅ Confirmada - La orden fue confirmada (pago recibido)
     */
    CONFIRMED("Confirmada"),

    /**
     * 🔄 En Proceso - La orden está siendo preparada
     */
    PROCESSING("En Proceso"),

    /**
     * ✅ Completada - La orden fue entregada exitosamente
     */
    COMPLETED("Completada"),

    /**
     * ❌ Cancelada - La orden fue cancelada
     */
    CANCELLED("Cancelada");

    private final String displayName;

    OrderStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
