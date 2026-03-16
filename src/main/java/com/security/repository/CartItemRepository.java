package com.security.repository;

import com.security.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository para items del carrito
 */
@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    /**
     * Encuentra todos los items de un carrito
     */
    @Query("SELECT ci FROM CartItem ci JOIN FETCH ci.product WHERE ci.cart.id = :cartId")
    List<CartItem> findByCartId(@Param("cartId") Long cartId);

    /**
     * Encuentra un item específico en un carrito
     */
    @Query("SELECT ci FROM CartItem ci WHERE ci.cart.id = :cartId AND ci.product.id = :productId")
    Optional<CartItem> findByCartIdAndProductId(@Param("cartId") Long cartId, @Param("productId") Long productId);

    /**
     * Cuenta items en un carrito
     */
    @Query("SELECT COUNT(ci) FROM CartItem ci WHERE ci.cart.id = :cartId")
    Long countByCartId(@Param("cartId") Long cartId);

    /**
     * Suma total de unidades en un carrito
     */
    @Query("SELECT COALESCE(SUM(ci.quantity), 0) FROM CartItem ci WHERE ci.cart.id = :cartId")
    Integer sumQuantityByCartId(@Param("cartId") Long cartId);

    /**
     * Encuentra items con un producto específico
     */
    List<CartItem> findByProductId(Long productId);

    /**
     * Elimina todos los items de un carrito
     */
    @Modifying
    @Query("DELETE FROM CartItem ci WHERE ci.cart.id = :cartId")
    void deleteByCartId(@Param("cartId") Long cartId);

    /**
     * Encuentra items con productos sin stock
     */
    @Query("SELECT ci FROM CartItem ci JOIN ci.product p WHERE ci.cart.id = :cartId AND p.stock < ci.quantity")
    List<CartItem> findItemsWithInsufficientStock(@Param("cartId") Long cartId);

    /**
     * Encuentra items con productos inactivos
     */
    @Query("SELECT ci FROM CartItem ci JOIN ci.product p WHERE ci.cart.id = :cartId AND p.active = false")
    List<CartItem> findItemsWithInactiveProducts(@Param("cartId") Long cartId);
}
