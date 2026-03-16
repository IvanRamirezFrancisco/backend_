package com.security.controller;

import com.security.dto.WishlistDTO;
import com.security.service.WishlistService;
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
 * Controller para gestión de lista de deseos (wishlist)
 * Endpoints: /api/wishlist
 * CORS se maneja globalmente en SecurityConfig
 */
@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
public class WishlistController {

    private final WishlistService wishlistService;

    /**
     * Obtiene la wishlist del usuario autenticado
     * GET /api/wishlist
     */
    @GetMapping
    public ResponseEntity<WishlistDTO.WishlistResponse> getWishlist(Authentication authentication) {

        log.info("Usuario {} obteniendo su wishlist", authentication.getName());

        Long userId = getUserIdFromAuth(authentication);
        var response = wishlistService.getWishlist(userId);

        return ResponseEntity.ok(response);
    }

    /**
     * Obtiene items de la wishlist con descuento/precio bajo
     * GET /api/wishlist/price-drops
     */
    @GetMapping("/price-drops")
    public ResponseEntity<List<WishlistDTO.WishlistItemResponse>> getPriceDroppedItems(
            Authentication authentication) {

        log.info("Usuario {} obteniendo items con descuento", authentication.getName());

        Long userId = getUserIdFromAuth(authentication);
        var items = wishlistService.getPriceDroppedItems(userId);

        return ResponseEntity.ok(items);
    }

    /**
     * Obtiene items que han vuelto a stock
     * GET /api/wishlist/in-stock
     */
    @GetMapping("/in-stock")
    public ResponseEntity<List<WishlistDTO.WishlistItemResponse>> getInStockItems(
            Authentication authentication) {

        log.info("Usuario {} obteniendo items de vuelta en stock", authentication.getName());

        Long userId = getUserIdFromAuth(authentication);
        var items = wishlistService.getInStockItems(userId);

        return ResponseEntity.ok(items);
    }

    /**
     * Obtiene items sin stock
     * GET /api/wishlist/out-of-stock
     */
    @GetMapping("/out-of-stock")
    public ResponseEntity<List<WishlistDTO.WishlistItemResponse>> getOutOfStockItems(
            Authentication authentication) {

        log.info("Usuario {} obteniendo items sin stock", authentication.getName());

        Long userId = getUserIdFromAuth(authentication);
        var items = wishlistService.getOutOfStockItems(userId);

        return ResponseEntity.ok(items);
    }

    /**
     * Obtiene resumen de la wishlist
     * GET /api/wishlist/summary
     */
    @GetMapping("/summary")
    public ResponseEntity<WishlistDTO.WishlistSummaryResponse> getWishlistSummary(
            Authentication authentication) {

        log.info("Usuario {} obteniendo resumen de wishlist", authentication.getName());

        Long userId = getUserIdFromAuth(authentication);
        var summary = wishlistService.getWishlistSummary(userId);

        return ResponseEntity.ok(summary);
    }

    /**
     * Obtiene notificaciones de precio/stock
     * GET /api/wishlist/notifications
     */
    @GetMapping("/notifications")
    public ResponseEntity<Map<String, Object>> getNotifications(Authentication authentication) {

        log.info("Usuario {} obteniendo notificaciones de wishlist", authentication.getName());

        Long userId = getUserIdFromAuth(authentication);

        // Verificar descuentos y stock
        var priceNotifications = wishlistService.checkPriceDrops(userId);
        var stockNotifications = wishlistService.checkBackInStock(userId);

        return ResponseEntity.ok(Map.of(
                "priceDrops", priceNotifications,
                "backInStock", stockNotifications,
                "total", priceNotifications.size() + stockNotifications.size()));
    }

    /**
     * Agrega un producto a la wishlist
     * POST /api/wishlist
     */
    @PostMapping
    public ResponseEntity<WishlistDTO.WishlistItemResponse> addToWishlist(
            @Valid @RequestBody WishlistDTO.AddToWishlistRequest request,
            Authentication authentication) {

        log.info("Usuario {} agregando producto {} a wishlist",
                authentication.getName(), request.getProductId());

        Long userId = getUserIdFromAuth(authentication);
        var item = wishlistService.addToWishlist(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(item);
    }

    /**
     * Actualiza un item de la wishlist
     * PUT /api/wishlist/{itemId}
     */
    @PutMapping("/{itemId}")
    public ResponseEntity<WishlistDTO.WishlistItemResponse> updateWishlistItem(
            @PathVariable Long itemId,
            @Valid @RequestBody WishlistDTO.UpdateWishlistRequest request,
            Authentication authentication) {

        log.info("Usuario {} actualizando wishlist item {}",
                authentication.getName(), itemId);

        Long userId = getUserIdFromAuth(authentication);
        var item = wishlistService.updateWishlistItem(itemId, userId, request);

        return ResponseEntity.ok(item);
    }

    /**
     * Elimina un item de la wishlist
     * DELETE /api/wishlist/{itemId}
     */
    @DeleteMapping("/{itemId}")
    public ResponseEntity<Map<String, String>> removeFromWishlist(
            @PathVariable Long itemId,
            Authentication authentication) {

        log.info("Usuario {} eliminando wishlist item {}",
                authentication.getName(), itemId);

        Long userId = getUserIdFromAuth(authentication);
        wishlistService.removeFromWishlist(itemId, userId);

        return ResponseEntity.ok(Map.of(
                "message", "Producto eliminado de la wishlist",
                "itemId", itemId.toString()));
    }

    /**
     * Mueve un item de wishlist al carrito
     * POST /api/wishlist/{itemId}/move-to-cart
     */
    @PostMapping("/{itemId}/move-to-cart")
    public ResponseEntity<Map<String, String>> moveToCart(
            @PathVariable Long itemId,
            Authentication authentication) {

        log.info("Usuario {} moviendo wishlist item {} al carrito",
                authentication.getName(), itemId);

        Long userId = getUserIdFromAuth(authentication);
        wishlistService.moveToCart(itemId, userId);

        return ResponseEntity.ok(Map.of(
                "message", "Producto agregado al carrito",
                "itemId", itemId.toString()));
    }

    /**
     * Mueve todos los items disponibles al carrito
     * POST /api/wishlist/move-all-to-cart
     */
    @PostMapping("/move-all-to-cart")
    public ResponseEntity<Map<String, Object>> moveAllToCart(Authentication authentication) {
        log.info("Usuario {} moviendo toda la wishlist al carrito", authentication.getName());

        Long userId = getUserIdFromAuth(authentication);

        // Obtener items en stock
        var inStockItems = wishlistService.getInStockItems(userId);
        int movedCount = 0;

        for (var item : inStockItems) {
            try {
                wishlistService.moveToCart(item.getWishlistId(), userId);
                movedCount++;
            } catch (Exception e) {
                log.warn("Error moviendo item {} al carrito: {}",
                        item.getWishlistId(), e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of(
                "message", "Items movidos al carrito",
                "totalItems", inStockItems.size(),
                "movedItems", movedCount));
    }

    /**
     * Verifica cambios de precio para todos los items
     * POST /api/wishlist/check-prices
     */
    @PostMapping("/check-prices")
    public ResponseEntity<Map<String, Object>> checkPrices(Authentication authentication) {
        log.info("Usuario {} verificando precios de wishlist", authentication.getName());

        Long userId = getUserIdFromAuth(authentication);
        var changes = wishlistService.checkPriceDrops(userId);

        return ResponseEntity.ok(Map.of(
                "message", "Verificación de precios completada",
                "changesFound", changes.size(),
                "changes", changes));
    }

    /**
     * Verifica disponibilidad de stock
     * POST /api/wishlist/check-stock
     */
    @PostMapping("/check-stock")
    public ResponseEntity<Map<String, Object>> checkStock(Authentication authentication) {
        log.info("Usuario {} verificando stock de wishlist", authentication.getName());

        Long userId = getUserIdFromAuth(authentication);
        var notifications = wishlistService.checkBackInStock(userId);

        return ResponseEntity.ok(Map.of(
                "message", "Verificación de stock completada",
                "notificationsCreated", notifications.size(),
                "notifications", notifications));
    }

    /**
     * Compara precios de items similares
     * GET /api/wishlist/{itemId}/price-comparison
     */
    @GetMapping("/{itemId}/price-comparison")
    public ResponseEntity<Map<String, String>> getPriceComparison(
            @PathVariable Long itemId,
            Authentication authentication) {

        log.info("Usuario {} comparando precios del item {}",
                authentication.getName(), itemId);

        return ResponseEntity.ok(Map.of(
                "message", "Comparación de precios no disponible actualmente",
                "itemId", itemId.toString()));
    }

    /**
     * Limpia la wishlist completa
     * DELETE /api/wishlist
     */
    @DeleteMapping
    public ResponseEntity<Map<String, String>> clearWishlist(Authentication authentication) {
        log.info("Usuario {} limpiando toda su wishlist", authentication.getName());

        Long userId = getUserIdFromAuth(authentication);
        var wishlistResponse = wishlistService.getWishlist(userId);

        wishlistResponse.getItems().forEach(item -> wishlistService.removeFromWishlist(item.getWishlistId(), userId));

        return ResponseEntity.ok(Map.of(
                "message", "Wishlist limpiada exitosamente",
                "removedItems", String.valueOf(wishlistResponse.getItems().size())));
    }

    /**
     * Extrae el ID del usuario desde la autenticación
     */
    private Long getUserIdFromAuth(Authentication authentication) {
        try {
            Object principal = authentication.getPrincipal();
            if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
                return Long.parseLong(userDetails.getUsername());
            }
            return null;
        } catch (Exception e) {
            log.error("Error al extraer userId de authentication: {}", e.getMessage());
            return null;
        }
    }
}
