package com.security.controller;

import com.security.dto.CartDTO;
import com.security.security.UserPrincipal;
import com.security.service.ShoppingCartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller para gestión del carrito de compras
 * Endpoints: /api/cart
 * CORS se maneja globalmente en SecurityConfig
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Slf4j
public class ShoppingCartController {

    private final ShoppingCartService cartService;

    /**
     * Obtiene o crea el carrito del usuario autenticado
     * GET /api/cart
     */
    @GetMapping
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<CartDTO.CartResponse> getCart(Authentication authentication) {
        Long userId = extractUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        log.info("Obteniendo carrito del usuario: {}", userId);

        var cart = cartService.getOrCreateCartForUser(userId);
        var response = cartService.buildCartResponse(cart.getId());

        return ResponseEntity.ok(response);
    }

    /**
     * Obtiene carrito por sesión anónima
     * GET /api/cart/session/{sessionId}
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<CartDTO.CartResponse> getCartBySession(@PathVariable String sessionId) {
        log.info("Obteniendo carrito para sesión: {}", sessionId);

        var cart = cartService.getOrCreateCartForSession(sessionId);
        var response = cartService.buildCartResponse(cart.getId());

        return ResponseEntity.ok(response);
    }

    /**
     * Obtiene resumen del carrito
     * GET /api/cart/{cartId}/summary
     */
    @GetMapping("/{cartId}/summary")
    public ResponseEntity<CartDTO.CartSummaryResponse> getCartSummary(@PathVariable Long cartId) {
        log.info("Obteniendo resumen del carrito: {}", cartId);

        var summary = cartService.getCartSummary(cartId);
        return ResponseEntity.ok(summary);
    }

    /**
     * Agrega un producto al carrito
     * POST /api/cart/{cartId}/items
     */
    @PostMapping("/{cartId}/items")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<CartDTO.CartResponse> addItem(
            @PathVariable Long cartId,
            @Valid @RequestBody CartDTO.AddItemRequest request,
            Authentication authentication) {

        Long userId = extractUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        log.info("Agregando producto {} al carrito {} (usuario {})", request.getProductId(), cartId, userId);

        var response = cartService.addItemToCart(cartId, request.getProductId(), request.getQuantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Actualiza la cantidad de un item
     * PUT /api/cart/items/{itemId}
     */
    @PutMapping("/items/{itemId}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<CartDTO.CartResponse> updateItemQuantity(
            @PathVariable Long itemId,
            @Valid @RequestBody CartDTO.UpdateItemRequest request,
            Authentication authentication) {

        Long userId = extractUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        log.info("Actualizando item {} a cantidad {} (usuario {})", itemId, request.getQuantity(), userId);

        var response = cartService.updateItemQuantity(request.getItemId(), request.getQuantity());
        return ResponseEntity.ok(response);
    }

    /**
     * Elimina un item del carrito
     * DELETE /api/cart/items/{itemId}
     */
    @DeleteMapping("/items/{itemId}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<CartDTO.CartResponse> removeItem(
            @PathVariable Long itemId,
            Authentication authentication) {

        Long userId = extractUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        log.info("Eliminando item {} (usuario {})", itemId, userId);

        var response = cartService.removeItem(itemId);
        return ResponseEntity.ok(response);
    }

    /**
     * Aplica un cupón al carrito
     * POST /api/cart/{cartId}/coupon
     */
    @PostMapping("/{cartId}/coupon")
    public ResponseEntity<CartDTO.CouponAppliedResponse> applyCoupon(
            @PathVariable Long cartId,
            @Valid @RequestBody CartDTO.ApplyCouponRequest request) {

        log.info("Aplicando cupón {} al carrito {}", request.getCouponCode(), cartId);

        var response = cartService.applyCoupon(cartId, request.getCouponCode());
        return ResponseEntity.ok(response);
    }

    /**
     * Remueve el cupón del carrito
     * DELETE /api/cart/{cartId}/coupon
     */
    @DeleteMapping("/{cartId}/coupon")
    public ResponseEntity<CartDTO.CartResponse> removeCoupon(@PathVariable Long cartId) {
        log.info("Removiendo cupón del carrito {}", cartId);

        var response = cartService.removeCoupon(cartId);
        return ResponseEntity.ok(response);
    }

    /**
     * Vacía el carrito
     * DELETE /api/cart/{cartId}
     */
    @DeleteMapping("/{cartId}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> clearCart(
            @PathVariable Long cartId,
            Authentication authentication) {

        Long userId = extractUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        log.info("Vaciando carrito {} (usuario {})", cartId, userId);

        cartService.clearCart(cartId);
        return ResponseEntity.ok(Map.of("message", "Carrito vaciado exitosamente"));
    }

    /**
     * Valida el carrito antes de checkout
     * GET /api/cart/{cartId}/validate
     */
    @GetMapping("/{cartId}/validate")
    public ResponseEntity<Map<String, Object>> validateCart(@PathVariable Long cartId) {
        log.info("Validando carrito {}", cartId);

        List<String> errors = cartService.validateCartForCheckout(cartId);
        boolean isValid = errors.isEmpty();

        return ResponseEntity.ok(Map.of(
                "valid", isValid,
                "errors", errors,
                "message", isValid ? "Carrito válido para checkout" : "El carrito tiene errores"));
    }

    /**
     * Transfiere carrito de sesión anónima a usuario autenticado
     * POST /api/cart/transfer
     */
    @PostMapping("/transfer")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<CartDTO.CartResponse> transferCart(
            @RequestParam String sessionId,
            Authentication authentication) {

        Long userId = extractUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        log.info("Transfiriendo carrito de sesión {} a usuario {}", sessionId, userId);

        var cart = cartService.transferCartToUser(sessionId, userId);
        var response = cartService.buildCartResponse(cart.getId());

        return ResponseEntity.ok(response);
    }

    /**
     * Obtiene el carrito activo del usuario autenticado o crea uno nuevo
     * POST /api/cart
     */
    @PostMapping
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<CartDTO.CartResponse> createOrGetCart(Authentication authentication) {
        Long userId = extractUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        log.info("Creando/obteniendo carrito para usuario: {}", userId);

        var cart = cartService.getOrCreateCartForUser(userId);
        var response = cartService.buildCartResponse(cart.getId());

        return ResponseEntity.ok(response);
    }

    /**
     * Crea carrito para sesión anónima
     * POST /api/cart/anonymous
     */
    @PostMapping("/anonymous")
    public ResponseEntity<CartDTO.CartResponse> createAnonymousCart(@RequestParam(required = false) String sessionId) {
        log.info("Creando carrito anónimo para sesión: {}", sessionId);

        var cart = cartService.getOrCreateCartForSession(sessionId);
        var response = cartService.buildCartResponse(cart.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Extrae el ID numérico del usuario desde el objeto Authentication.
     * El principal es siempre un UserPrincipal (implementación interna de
     * UserDetails)
     * que almacena el id de la entidad User, NO su email.
     */
    private Long extractUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getId();
        }
        log.warn("Principal de tipo inesperado: {}. Se esperaba UserPrincipal.",
                principal != null ? principal.getClass().getName() : "null");
        return null;
    }
}
