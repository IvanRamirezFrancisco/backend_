package com.security.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTOs para operaciones de cupones
 */
public class CouponDTO {

    /**
     * Request para crear cupón
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateCouponRequest {

        @NotBlank(message = "El código del cupón es obligatorio")
        @Size(min = 3, max = 50, message = "El código debe tener entre 3 y 50 caracteres")
        @Pattern(regexp = "^[A-Z0-9_-]+$", message = "El código solo puede contener letras mayúsculas, números, guiones y guión bajo")
        private String code;

        @Size(max = 200, message = "La descripción no puede exceder 200 caracteres")
        private String description;

        @NotNull(message = "El tipo de descuento es obligatorio")
        @Pattern(regexp = "^(PERCENTAGE|FIXED|FREE_SHIPPING)$", message = "Tipo de descuento inválido")
        private String discountType;

        @NotNull(message = "El valor del descuento es obligatorio")
        @DecimalMin(value = "0.01", message = "El valor debe ser mayor a 0")
        @DecimalMax(value = "100.00", message = "El porcentaje máximo es 100")
        private BigDecimal discountValue;

        @DecimalMin(value = "0.01", message = "La compra mínima debe ser mayor a 0")
        private BigDecimal minimumPurchase;

        @DecimalMin(value = "0.01", message = "El descuento máximo debe ser mayor a 0")
        private BigDecimal maximumDiscount;

        private LocalDateTime validFrom;
        private LocalDateTime validUntil;

        @Min(value = 1, message = "El límite de uso debe ser mayor a 0")
        private Integer usageLimit;

        @Min(value = 1, message = "El límite de uso por usuario debe ser mayor a 0")
        private Integer usageLimitPerUser;

        private Boolean firstPurchaseOnly;
        private List<Long> applicableCategoryIds;
        private List<Long> applicableProductIds;
    }

    /**
     * Request para validar cupón
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidateCouponRequest {

        @NotBlank(message = "El código del cupón es obligatorio")
        private String code;

        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
        private BigDecimal amount;

        private List<Long> productIds;
        private List<Long> categoryIds;
    }

    /**
     * Response de cupón
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CouponResponse {
        private Long couponId;
        private String code;
        private String description;
        private String discountType;
        private BigDecimal discountValue;
        private BigDecimal minimumPurchase;
        private BigDecimal maximumDiscount;
        private LocalDateTime validFrom;
        private LocalDateTime validUntil;
        private Integer usageLimit;
        private Integer usageLimitPerUser;
        private Integer timesUsed;
        private Integer remainingUses;
        private Boolean isActive;
        private Boolean firstPurchaseOnly;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    /**
     * Response de validación de cupón
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CouponValidationResponse {
        private Boolean valid;
        private String code;
        private String discountType;
        private BigDecimal discountValue;
        private BigDecimal discountApplied;
        private String message;
        private List<String> errors;
    }

    /**
     * Response con lista de cupones
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CouponListResponse {
        private List<CouponResponse> coupons;
        private Integer totalCoupons;
        private Integer currentPage;
        private Integer totalPages;
    }

    /**
     * Estadísticas de uso de cupón
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CouponUsageStats {
        private Long couponId;
        private String code;
        private Integer totalUses;
        private BigDecimal totalDiscountGiven;
        private BigDecimal averageDiscountPerUse;
        private BigDecimal totalRevenue;
        private List<TopUserUsage> topUsers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopUserUsage {
        private Long userId;
        private String userName;
        private Integer usageCount;
        private BigDecimal totalDiscount;
    }
}
