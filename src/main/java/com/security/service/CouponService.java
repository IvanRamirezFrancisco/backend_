package com.security.service;

import com.security.dto.CouponDTO;
import com.security.entity.Coupon;
import com.security.entity.User;
import com.security.exception.ResourceNotFoundException;
import com.security.repository.CouponRepository;
import com.security.repository.CouponUsageRepository;
import com.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service para gestión de cupones de descuento
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final UserRepository userRepository;

    /**
     * Crea un nuevo cupón (ADMIN)
     */
    @Transactional
    public CouponDTO.CouponResponse createCoupon(CouponDTO.CreateCouponRequest request) {
        log.info("Creando cupón: {}", request.getCode());

        // Verificar que el código no exista
        if (couponRepository.findByCodeIgnoreCase(request.getCode()).isPresent()) {
            throw new IllegalArgumentException("El código de cupón ya existe: " + request.getCode());
        }

        // Validar tipo de descuento y valor
        if ("PERCENTAGE".equals(request.getDiscountType())) {
            if (request.getDiscountValue().compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("El porcentaje de descuento no puede ser mayor a 100%");
            }
        }

        Coupon coupon = new Coupon();
        coupon.setCode(request.getCode().toUpperCase());
        coupon.setDescription(request.getDescription());
        coupon.setDiscountType(request.getDiscountType());
        coupon.setDiscountValue(request.getDiscountValue());
        coupon.setMinimumPurchase(request.getMinimumPurchase());
        coupon.setMaximumDiscount(request.getMaximumDiscount());
        coupon.setValidFrom(request.getValidFrom());
        coupon.setValidUntil(request.getValidUntil());
        coupon.setUsageLimit(request.getUsageLimit());
        coupon.setUsageLimitPerUser(request.getUsageLimitPerUser());
        coupon.setFirstPurchaseOnly(request.getFirstPurchaseOnly() != null ? request.getFirstPurchaseOnly() : false);
        coupon.setIsActive(true);

        Coupon savedCoupon = couponRepository.save(coupon);
        log.info("Cupón {} creado exitosamente", savedCoupon.getCode());

        return buildCouponResponse(savedCoupon);
    }

    /**
     * Actualiza un cupón existente (ADMIN)
     */
    @Transactional
    public CouponDTO.CouponResponse updateCoupon(Long couponId, CouponDTO.CreateCouponRequest request) {
        log.info("Actualizando cupón ID: {}", couponId);

        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Cupón no encontrado"));

        coupon.setDescription(request.getDescription());
        coupon.setDiscountType(request.getDiscountType());
        coupon.setDiscountValue(request.getDiscountValue());
        coupon.setMinimumPurchase(request.getMinimumPurchase());
        coupon.setMaximumDiscount(request.getMaximumDiscount());
        coupon.setValidFrom(request.getValidFrom());
        coupon.setValidUntil(request.getValidUntil());
        coupon.setUsageLimit(request.getUsageLimit());
        coupon.setUsageLimitPerUser(request.getUsageLimitPerUser());

        Coupon updatedCoupon = couponRepository.save(coupon);

        return buildCouponResponse(updatedCoupon);
    }

    /**
     * Valida un cupón antes de aplicarlo
     */
    @Transactional(readOnly = true)
    public CouponDTO.CouponValidationResponse validateCoupon(CouponDTO.ValidateCouponRequest request, Long userId) {
        log.info("Validando cupón: {}", request.getCode());

        List<String> errors = new ArrayList<>();

        Coupon coupon = couponRepository.findByCodeIgnoreCase(request.getCode())
                .orElse(null);

        if (coupon == null) {
            return CouponDTO.CouponValidationResponse.builder()
                    .valid(false)
                    .message("Cupón no encontrado")
                    .errors(List.of("El cupón no existe"))
                    .build();
        }

        // Validaciones
        if (!coupon.getIsActive()) {
            errors.add("El cupón no está activo");
        }

        LocalDateTime now = LocalDateTime.now();
        if (coupon.getValidFrom() != null && now.isBefore(coupon.getValidFrom())) {
            errors.add("El cupón aún no es válido");
        }

        if (coupon.getValidUntil() != null && now.isAfter(coupon.getValidUntil())) {
            errors.add("El cupón ha expirado");
        }

        if (coupon.getUsageLimit() != null && coupon.getTimesUsed() >= coupon.getUsageLimit()) {
            errors.add("El cupón ha alcanzado su límite de uso");
        }

        if (coupon.getMinimumPurchase() != null &&
                request.getAmount().compareTo(coupon.getMinimumPurchase()) < 0) {
            errors.add(String.format("Compra mínima requerida: $%.2f", coupon.getMinimumPurchase()));
        }

        // Validar uso por usuario
        if (userId != null && coupon.getUsageLimitPerUser() != null) {
            Long userUsageCount = couponUsageRepository.countByCouponIdAndUserId(coupon.getId(), userId);
            if (userUsageCount >= coupon.getUsageLimitPerUser()) {
                errors.add("Has alcanzado el límite de uso de este cupón");
            }
        }

        // Si es para primera compra
        if (coupon.getFirstPurchaseOnly() && userId != null) {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getTotalOrders() > 0) {
                errors.add("Este cupón solo es válido para la primera compra");
            }
        }

        if (!errors.isEmpty()) {
            return CouponDTO.CouponValidationResponse.builder()
                    .valid(false)
                    .code(coupon.getCode())
                    .message("El cupón no es válido")
                    .errors(errors)
                    .build();
        }

        // Calcular descuento
        BigDecimal discount = coupon.calculateDiscount(request.getAmount());

        return CouponDTO.CouponValidationResponse.builder()
                .valid(true)
                .code(coupon.getCode())
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue())
                .discountApplied(discount)
                .message("Cupón válido")
                .build();
    }

    /**
     * Obtiene todos los cupones activos
     */
    @Transactional(readOnly = true)
    public CouponDTO.CouponListResponse getActiveCoupons(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Coupon> couponPage = couponRepository.findActiveCoupons(pageable);

        List<CouponDTO.CouponResponse> couponResponses = couponPage.getContent().stream()
                .map(this::buildCouponResponse)
                .collect(Collectors.toList());

        return CouponDTO.CouponListResponse.builder()
                .coupons(couponResponses)
                .totalCoupons((int) couponPage.getTotalElements())
                .currentPage(page)
                .totalPages(couponPage.getTotalPages())
                .build();
    }

    /**
     * Obtiene un cupón por ID
     */
    @Transactional(readOnly = true)
    public CouponDTO.CouponResponse getCouponById(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Cupón no encontrado"));

        return buildCouponResponse(coupon);
    }

    /**
     * Obtiene un cupón por código
     */
    @Transactional(readOnly = true)
    public CouponDTO.CouponResponse getCouponByCode(String code) {
        Coupon coupon = couponRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ResourceNotFoundException("Cupón no encontrado"));

        return buildCouponResponse(coupon);
    }

    /**
     * Desactiva un cupón
     */
    @Transactional
    public void deactivateCoupon(Long couponId) {
        log.info("Desactivando cupón ID: {}", couponId);

        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Cupón no encontrado"));

        coupon.setIsActive(false);
        couponRepository.save(coupon);

        log.info("Cupón {} desactivado", coupon.getCode());
    }

    /**
     * Elimina un cupón
     */
    @Transactional
    public void deleteCoupon(Long couponId) {
        log.info("Eliminando cupón ID: {}", couponId);

        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Cupón no encontrado"));

        // Eliminar registros de uso
        couponUsageRepository.deleteByCouponId(couponId);

        // Eliminar cupón
        couponRepository.delete(coupon);

        log.info("Cupón {} eliminado", coupon.getCode());
    }

    /**
     * Obtiene estadísticas de uso de un cupón
     */
    @Transactional(readOnly = true)
    public CouponDTO.CouponUsageStats getCouponUsageStats(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Cupón no encontrado"));

        Integer totalUses = coupon.getTimesUsed();
        BigDecimal totalDiscount = couponUsageRepository.sumDiscountByCouponId(couponId);
        BigDecimal totalRevenue = couponUsageRepository.sumRevenueByCouponId(couponId);

        BigDecimal averageDiscount = BigDecimal.ZERO;
        if (totalUses > 0) {
            averageDiscount = totalDiscount.divide(new BigDecimal(totalUses), 2, RoundingMode.HALF_UP);
        }

        // Top usuarios
        List<Object[]> topUsersData = couponUsageRepository.findTopUsersByCouponId(couponId);
        List<CouponDTO.TopUserUsage> topUsers = topUsersData.stream()
                .limit(10)
                .map(data -> CouponDTO.TopUserUsage.builder()
                        .userId((Long) data[0])
                        .userName(data[1] + " " + data[2])
                        .usageCount(((Long) data[3]).intValue())
                        .totalDiscount((BigDecimal) data[4])
                        .build())
                .collect(Collectors.toList());

        return CouponDTO.CouponUsageStats.builder()
                .couponId(couponId)
                .code(coupon.getCode())
                .totalUses(totalUses)
                .totalDiscountGiven(totalDiscount)
                .averageDiscountPerUse(averageDiscount)
                .totalRevenue(totalRevenue)
                .topUsers(topUsers)
                .build();
    }

    /**
     * Construye response de cupón
     */
    private CouponDTO.CouponResponse buildCouponResponse(Coupon coupon) {
        Integer remainingUses = null;
        if (coupon.getUsageLimit() != null) {
            remainingUses = coupon.getUsageLimit() - coupon.getTimesUsed();
            if (remainingUses < 0)
                remainingUses = 0;
        }

        return CouponDTO.CouponResponse.builder()
                .couponId(coupon.getId())
                .code(coupon.getCode())
                .description(coupon.getDescription())
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue())
                .minimumPurchase(coupon.getMinimumPurchase())
                .maximumDiscount(coupon.getMaximumDiscount())
                .validFrom(coupon.getValidFrom())
                .validUntil(coupon.getValidUntil())
                .usageLimit(coupon.getUsageLimit())
                .usageLimitPerUser(coupon.getUsageLimitPerUser())
                .timesUsed(coupon.getTimesUsed())
                .remainingUses(remainingUses)
                .isActive(coupon.getIsActive())
                .firstPurchaseOnly(coupon.getFirstPurchaseOnly())
                .createdAt(coupon.getCreatedAt())
                .updatedAt(coupon.getUpdatedAt())
                .build();
    }
}
