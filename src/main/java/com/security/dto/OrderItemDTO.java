package com.security.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 📦 DTO para items individuales de una orden
 * Representa un producto dentro de una orden
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemDTO {

    private Long id;

    // ==================== PRODUCTO ====================

    private Long productId;
    private String productName;
    private String productSku;
    private String productImage;

    // ==================== CANTIDAD Y PRECIOS ====================

    private Integer quantity;
    private BigDecimal price; // Precio unitario en el momento de la compra
    private BigDecimal subtotal; // quantity * price

    // ==================== MÉTODOS AUXILIARES ====================

    /**
     * Calcula el subtotal (cantidad * precio)
     */
    public void calculateSubtotal() {
        if (quantity != null && price != null) {
            this.subtotal = price.multiply(BigDecimal.valueOf(quantity));
        }
    }
}
