package com.security.service;

import com.security.dto.CartDTO;
import com.security.entity.*;
import com.security.exception.*;
import com.security.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.StoredProcedureQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service para gestión completa del carrito de compras
 * Soporta usuarios autenticados y sesiones anónimas
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ShoppingCartService {

    private final ShoppingCartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    private static final int CART_EXPIRATION_HOURS = 72;
    private static final BigDecimal TAX_RATE = new BigDecimal("0.16"); // 16% IVA

    /**
     * Obtiene o crea un carrito activo para un usuario
     */
    @Transactional
    public ShoppingCart getOrCreateCartForUser(Long userId) {
        log.info("Obteniendo o creando carrito para usuario ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + userId));

        LocalDateTime now = LocalDateTime.now();
        Optional<ShoppingCart> existingCart = cartRepository.findActiveCartByUserId(userId, now);

        if (existingCart.isPresent()) {
            log.debug("Carrito existente encontrado: {}", existingCart.get().getId());
            return existingCart.get();
        }

        // Crear nuevo carrito
        ShoppingCart newCart = new ShoppingCart();
        newCart.setUser(user);
        newCart.setStatus("ACTIVE");
        newCart.setExpiresAt(now.plusHours(CART_EXPIRATION_HOURS));
        newCart.setSubtotal(BigDecimal.ZERO);
        newCart.setTax(BigDecimal.ZERO);
        newCart.setTotal(BigDecimal.ZERO);

        ShoppingCart savedCart = cartRepository.save(newCart);
        log.info("Nuevo carrito creado: {}", savedCart.getId());
        return savedCart;
    }

    /**
     * Obtiene o crea un carrito para sesión anónima
     */
    @Transactional
    public ShoppingCart getOrCreateCartForSession(String sessionId) {
        log.info("Obteniendo o creando carrito para sesión: {}", sessionId);

        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        LocalDateTime now = LocalDateTime.now();
        Optional<ShoppingCart> existingCart = cartRepository.findActiveCartBySessionId(sessionId, now);

        if (existingCart.isPresent()) {
            return existingCart.get();
        }

        // Crear carrito anónimo
        ShoppingCart newCart = new ShoppingCart();
        newCart.setSessionId(sessionId);
        newCart.setStatus("ACTIVE");
        newCart.setExpiresAt(now.plusHours(CART_EXPIRATION_HOURS));
        newCart.setSubtotal(BigDecimal.ZERO);
        newCart.setTax(BigDecimal.ZERO);
        newCart.setTotal(BigDecimal.ZERO);

        return cartRepository.save(newCart);
    }

    /**
     * Obtiene carrito con items cargados
     */
    @Transactional(readOnly = true)
    public ShoppingCart getCartWithItems(Long cartId) {
        return cartRepository.findByIdWithItems(cartId)
                .orElseThrow(() -> new CartNotFoundException(cartId));
    }

    /**
     * Agrega un producto al carrito
     */
    @Transactional
    public CartDTO.CartResponse addItemToCart(Long cartId, Long productId, Integer quantity) {
        log.info("Agregando producto {} (cantidad: {}) al carrito {}", productId, quantity, cartId);

        // Validaciones
        ShoppingCart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new CartNotFoundException(cartId));

        if (!cart.isActive()) {
            throw new CartNotFoundException("El carrito está inactivo o expirado");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + productId));

        if (!product.getActive()) {
            throw new IllegalArgumentException("El producto no está disponible");
        }

        // Verificar stock
        if (product.getStock() < quantity) {
            throw new InsufficientStockException(productId, quantity, product.getStock());
        }

        // Buscar si el producto ya existe en el carrito
        Optional<CartItem> existingItem = cartItemRepository.findByCartIdAndProductId(cartId, productId);

        if (existingItem.isPresent()) {
            // Actualizar cantidad existente
            CartItem item = existingItem.get();
            Integer newQuantity = item.getQuantity() + quantity;

            if (product.getStock() < newQuantity) {
                throw new InsufficientStockException(productId, newQuantity, product.getStock());
            }

            item.setQuantity(newQuantity);
            item.calculateSubtotal();
            cartItemRepository.save(item);
            log.info("Cantidad actualizada para item {}", item.getId());
        } else {
            // Crear nuevo item
            CartItem newItem = new CartItem();
            newItem.setCart(cart);
            newItem.setProduct(product);
            newItem.setQuantity(quantity);
            newItem.setUnitPriceFromProduct();
            newItem.calculateSubtotal();

            cartItemRepository.save(newItem);
            log.info("Nuevo item agregado al carrito");
        }

        // Recalcular totales del carrito
        recalculateCartTotals(cartId);

        return buildCartResponse(cartId);
    }

    /**
     * Actualiza la cantidad de un item
     */
    @Transactional
    public CartDTO.CartResponse updateItemQuantity(Long itemId, Integer newQuantity) {
        log.info("Actualizando cantidad del item {} a {}", itemId, newQuantity);

        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item no encontrado con ID: " + itemId));

        Product product = item.getProduct();

        // Verificar stock
        if (product.getStock() < newQuantity) {
            throw new InsufficientStockException(product.getId(), newQuantity, product.getStock());
        }

        item.setQuantity(newQuantity);
        item.calculateSubtotal();
        cartItemRepository.save(item);

        // Recalcular totales
        Long cartId = item.getCart().getId();
        recalculateCartTotals(cartId);

        return buildCartResponse(cartId);
    }

    /**
     * Elimina un item del carrito
     */
    @Transactional
    public CartDTO.CartResponse removeItem(Long itemId) {
        log.info("Eliminando item {}", itemId);

        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item no encontrado con ID: " + itemId));

        Long cartId = item.getCart().getId();
        cartItemRepository.delete(item);

        // Recalcular totales
        recalculateCartTotals(cartId);

        return buildCartResponse(cartId);
    }

    /**
     * Aplica un cupón al carrito usando stored procedure
     */
    @Transactional
    public CartDTO.CouponAppliedResponse applyCoupon(Long cartId, String couponCode) {
        log.info("Aplicando cupón {} al carrito {}", couponCode, cartId);

        ShoppingCart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new CartNotFoundException(cartId));

        Coupon coupon = couponRepository.findByCodeIgnoreCase(couponCode)
                .orElseThrow(() -> new InvalidCouponException(couponCode, "Cupón no encontrado"));

        // Validar cupón
        if (!coupon.isValid()) {
            throw new InvalidCouponException(couponCode, "Cupón no válido o expirado");
        }

        // Verificar si el usuario ya usó este cupón
        if (cart.getUser() != null && coupon.getUsageLimitPerUser() != null) {
            Long usageCount = couponUsageRepository.countByCouponIdAndUserId(
                    coupon.getId(), cart.getUser().getId());

            if (usageCount >= coupon.getUsageLimitPerUser()) {
                throw new InvalidCouponException(couponCode,
                        "Has alcanzado el límite de uso de este cupón");
            }
        }

        // Verificar monto mínimo
        if (coupon.getMinimumPurchase() != null &&
                cart.getSubtotal().compareTo(coupon.getMinimumPurchase()) < 0) {
            throw new InvalidCouponException(couponCode,
                    String.format("Compra mínima requerida: $%.2f", coupon.getMinimumPurchase()));
        }

        // Llamar al stored procedure para aplicar el cupón
        try {
            StoredProcedureQuery query = entityManager.createStoredProcedureQuery("sp_apply_coupon_to_cart");
            query.registerStoredProcedureParameter("p_cart_id", Long.class, ParameterMode.IN);
            query.registerStoredProcedureParameter("p_coupon_id", Long.class, ParameterMode.IN);

            query.setParameter("p_cart_id", cartId);
            query.setParameter("p_coupon_id", coupon.getId());

            query.execute();

            // Refrescar el carrito
            entityManager.refresh(cart);

            log.info("Cupón {} aplicado exitosamente al carrito {}", couponCode, cartId);

            return CartDTO.CouponAppliedResponse.builder()
                    .couponCode(couponCode)
                    .discountType(coupon.getDiscountType())
                    .discountValue(coupon.getDiscountValue())
                    .discountApplied(cart.getDiscount())
                    .newTotal(cart.getTotal())
                    .message("Cupón aplicado exitosamente")
                    .build();

        } catch (Exception e) {
            log.error("Error al aplicar cupón: {}", e.getMessage());
            throw new InvalidCouponException(couponCode, "Error al aplicar cupón: " + e.getMessage());
        }
    }

    /**
     * Remueve el cupón del carrito
     */
    @Transactional
    public CartDTO.CartResponse removeCoupon(Long cartId) {
        log.info("Removiendo cupón del carrito {}", cartId);

        ShoppingCart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new CartNotFoundException(cartId));

        cart.setCouponCode(null);
        cart.setDiscount(BigDecimal.ZERO);
        cartRepository.save(cart);

        recalculateCartTotals(cartId);

        return buildCartResponse(cartId);
    }

    /**
     * Vacía el carrito
     */
    @Transactional
    public void clearCart(Long cartId) {
        log.info("Vaciando carrito {}", cartId);

        ShoppingCart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new CartNotFoundException(cartId));

        cartItemRepository.deleteByCartId(cartId);

        cart.setSubtotal(BigDecimal.ZERO);
        cart.setTax(BigDecimal.ZERO);
        cart.setDiscount(BigDecimal.ZERO);
        cart.setTotal(BigDecimal.ZERO);
        cart.setCouponCode(null);

        cartRepository.save(cart);
    }

    /**
     * Transfiere carrito de sesión anónima a usuario autenticado
     */
    @Transactional
    public ShoppingCart transferCartToUser(String sessionId, Long userId) {
        log.info("Transfiriendo carrito de sesión {} a usuario {}", sessionId, userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        LocalDateTime now = LocalDateTime.now();

        // Buscar carrito anónimo
        Optional<ShoppingCart> anonymousCart = cartRepository.findActiveCartBySessionId(sessionId, now);
        if (anonymousCart.isEmpty()) {
            log.info("No hay carrito anónimo para transferir");
            return getOrCreateCartForUser(userId);
        }

        // Buscar carrito existente del usuario
        Optional<ShoppingCart> userCart = cartRepository.findActiveCartByUserId(userId, now);

        if (userCart.isPresent()) {
            // Merge: mover items del carrito anónimo al carrito del usuario
            ShoppingCart targetCart = userCart.get();
            ShoppingCart sourceCart = anonymousCart.get();

            List<CartItem> itemsToTransfer = cartItemRepository.findByCartId(sourceCart.getId());

            for (CartItem item : itemsToTransfer) {
                // Verificar si el producto ya existe en el carrito del usuario
                Optional<CartItem> existingItem = cartItemRepository.findByCartIdAndProductId(
                        targetCart.getId(), item.getProduct().getId());

                if (existingItem.isPresent()) {
                    // Sumar cantidades
                    CartItem existing = existingItem.get();
                    existing.setQuantity(existing.getQuantity() + item.getQuantity());
                    existing.calculateSubtotal();
                    cartItemRepository.save(existing);
                } else {
                    // Mover item al carrito del usuario
                    item.setCart(targetCart);
                    cartItemRepository.save(item);
                }
            }

            // Marcar carrito anónimo como convertido
            sourceCart.setStatus("CONVERTED");
            cartRepository.save(sourceCart);

            // Recalcular totales del carrito destino
            recalculateCartTotals(targetCart.getId());

            return targetCart;
        } else {
            // Simplemente asignar el carrito anónimo al usuario
            ShoppingCart cart = anonymousCart.get();
            cart.setUser(user);
            cart.setSessionId(null);
            return cartRepository.save(cart);
        }
    }

    /**
     * Recalcula totales del carrito (llamado después de cambios)
     */
    private void recalculateCartTotals(Long cartId) {
        ShoppingCart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new CartNotFoundException(cartId));

        List<CartItem> items = cartItemRepository.findByCartId(cartId);

        // Calcular subtotal
        BigDecimal subtotal = items.stream()
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        cart.setSubtotal(subtotal);

        // Calcular IVA
        BigDecimal tax = subtotal.multiply(TAX_RATE);
        cart.setTax(tax);

        // Aplicar descuento si existe cupón
        BigDecimal discount = cart.getDiscount() != null ? cart.getDiscount() : BigDecimal.ZERO;

        // Calcular total
        BigDecimal total = subtotal.add(tax).subtract(discount);
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO;
        }
        cart.setTotal(total);

        cartRepository.save(cart);
        log.debug("Totales recalculados para carrito {}: Subtotal={}, Tax={}, Discount={}, Total={}",
                cartId, subtotal, tax, discount, total);
    }

    /**
     * Construye response completo del carrito
     */
    @Transactional(readOnly = true)
    public CartDTO.CartResponse buildCartResponse(Long cartId) {
        ShoppingCart cart = cartRepository.findByIdWithItems(cartId)
                .orElseThrow(() -> new CartNotFoundException(cartId));

        List<CartDTO.CartItemResponse> itemResponses = cart.getItems().stream()
                .map(this::buildCartItemResponse)
                .collect(Collectors.toList());

        return CartDTO.CartResponse.builder()
                .cartId(cart.getId())
                .userId(cart.getUser() != null ? cart.getUser().getId() : null)
                .sessionId(cart.getSessionId())
                .items(itemResponses)
                .totalItems(itemResponses.stream().mapToInt(CartDTO.CartItemResponse::getQuantity).sum())
                .subtotal(cart.getSubtotal())
                .tax(cart.getTax())
                .taxRate(TAX_RATE.multiply(new BigDecimal("100"))) // Convertir a porcentaje
                .discount(cart.getDiscount())
                .total(cart.getTotal())
                .couponCode(cart.getCouponCode())
                .status(cart.getStatus())
                .expiresAt(cart.getExpiresAt())
                .createdAt(cart.getCreatedAt())
                .updatedAt(cart.getUpdatedAt())
                .build();
    }

    /**
     * Construye response de item individual
     */
    private CartDTO.CartItemResponse buildCartItemResponse(CartItem item) {
        Product product = item.getProduct();

        return CartDTO.CartItemResponse.builder()
                .itemId(item.getId())
                .productId(product.getId())
                .productName(product.getName())
                .productImage(product.getImageUrl())
                .productSku(product.getSku())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getSubtotal())
                .availableStock(product.getStock())
                .addedAt(item.getAddedAt())
                .build();
    }

    /**
     * Obtiene resumen del carrito
     */
    @Transactional(readOnly = true)
    public CartDTO.CartSummaryResponse getCartSummary(Long cartId) {
        ShoppingCart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new CartNotFoundException(cartId));

        Integer totalItems = cartItemRepository.sumQuantityByCartId(cartId);

        return CartDTO.CartSummaryResponse.builder()
                .cartId(cart.getId())
                .totalItems(totalItems != null ? totalItems : 0)
                .subtotal(cart.getSubtotal())
                .total(cart.getTotal())
                .status(cart.getStatus())
                .build();
    }

    /**
     * Valida el carrito antes de checkout
     */
    @Transactional(readOnly = true)
    public List<String> validateCartForCheckout(Long cartId) {
        List<String> errors = new ArrayList<>();

        ShoppingCart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new CartNotFoundException(cartId));

        if (!cart.isActive()) {
            errors.add("El carrito no está activo");
            return errors;
        }

        List<CartItem> items = cartItemRepository.findByCartId(cartId);

        if (items.isEmpty()) {
            errors.add("El carrito está vacío");
        }

        // Validar stock de cada item
        for (CartItem item : items) {
            Product product = item.getProduct();

            if (!product.getActive()) {
                errors.add(String.format("Producto '%s' no está disponible", product.getName()));
            }

            if (product.getStock() < item.getQuantity()) {
                errors.add(String.format("Stock insuficiente para '%s'. Disponible: %d, Solicitado: %d",
                        product.getName(), product.getStock(), item.getQuantity()));
            }
        }

        return errors;
    }
}
