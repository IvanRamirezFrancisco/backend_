-- ============================================================
-- V8: Stored Procedures para PostgreSQL
-- Equivalentes de los procedimientos almacenados de MySQL
-- ============================================================
-- ----------------------------------------------------------------
-- sp_calculate_coupon_discount
-- Calcula el descuento de un cupón dado un monto
-- ----------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_calculate_coupon_discount(
        IN p_coupon_id BIGINT,
        IN p_amount NUMERIC(10, 2),
        OUT p_discount NUMERIC(10, 2)
    ) LANGUAGE plpgsql AS $$
DECLARE v_coupon RECORD;
BEGIN p_discount := 0;
SELECT * INTO v_coupon
FROM coupons
WHERE id = p_coupon_id
    AND is_active = true
    AND (
        valid_until IS NULL
        OR valid_until > NOW()
    )
    AND (
        usage_limit IS NULL
        OR times_used < usage_limit
    );
IF NOT FOUND THEN RETURN;
END IF;
IF v_coupon.discount_type = 'PERCENTAGE' THEN p_discount := ROUND(p_amount * v_coupon.discount_value / 100, 2);
ELSE -- FIXED
p_discount := LEAST(v_coupon.discount_value, p_amount);
END IF;
IF v_coupon.maximum_discount IS NOT NULL
AND p_discount > v_coupon.maximum_discount THEN p_discount := v_coupon.maximum_discount;
END IF;
END;
$$;
-- ----------------------------------------------------------------
-- sp_apply_coupon_to_cart
-- Aplica un cupón a un carrito de compras
-- ----------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_apply_coupon_to_cart(
        IN p_cart_id BIGINT,
        IN p_coupon_id BIGINT
    ) LANGUAGE plpgsql AS $$
DECLARE v_subtotal NUMERIC(10, 2);
v_discount NUMERIC(10, 2);
v_coupon RECORD;
BEGIN
SELECT subtotal INTO v_subtotal
FROM shopping_carts
WHERE id = p_cart_id;
IF NOT FOUND THEN RETURN;
END IF;
SELECT * INTO v_coupon
FROM coupons
WHERE id = p_coupon_id
    AND is_active = true;
IF NOT FOUND THEN RETURN;
END IF;
CALL sp_calculate_coupon_discount(p_coupon_id, v_subtotal, v_discount);
UPDATE shopping_carts
SET discount = v_discount,
    coupon_code = v_coupon.code,
    total = subtotal + COALESCE(tax, 0) - v_discount
WHERE id = p_cart_id;
END;
$$;
-- ----------------------------------------------------------------
-- sp_apply_coupon_to_order
-- Aplica un cupón a una orden
-- ----------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_apply_coupon_to_order(
        IN p_order_id BIGINT,
        IN p_coupon_id BIGINT
    ) LANGUAGE plpgsql AS $$
DECLARE v_subtotal NUMERIC(10, 2);
v_discount NUMERIC(10, 2);
v_coupon RECORD;
BEGIN
SELECT subtotal INTO v_subtotal
FROM orders
WHERE id = p_order_id;
IF NOT FOUND THEN RETURN;
END IF;
SELECT * INTO v_coupon
FROM coupons
WHERE id = p_coupon_id
    AND is_active = true;
IF NOT FOUND THEN RETURN;
END IF;
CALL sp_calculate_coupon_discount(p_coupon_id, v_subtotal, v_discount);
UPDATE orders
SET discount = v_discount,
    total = subtotal + COALESCE(tax, 0) + COALESCE(shipping, 0) - v_discount
WHERE id = p_order_id;
-- Incrementar contador de uso del cupón
UPDATE coupons
SET times_used = COALESCE(times_used, 0) + 1
WHERE id = p_coupon_id;
END;
$$;
-- ----------------------------------------------------------------
-- sp_recalculate_product_rating
-- Recalcula el rating promedio de un producto
-- ----------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_recalculate_product_rating(IN p_product_id BIGINT) LANGUAGE plpgsql AS $$
DECLARE v_count INTEGER;
v_avg NUMERIC(3, 2);
v_five INTEGER;
v_four INTEGER;
v_three INTEGER;
v_two INTEGER;
v_one INTEGER;
BEGIN
SELECT COUNT(*),
    COALESCE(AVG(rating), 0),
    COUNT(*) FILTER (
        WHERE rating = 5
    ),
    COUNT(*) FILTER (
        WHERE rating = 4
    ),
    COUNT(*) FILTER (
        WHERE rating = 3
    ),
    COUNT(*) FILTER (
        WHERE rating = 2
    ),
    COUNT(*) FILTER (
        WHERE rating = 1
    ) INTO v_count,
    v_avg,
    v_five,
    v_four,
    v_three,
    v_two,
    v_one
FROM product_reviews
WHERE product_id = p_product_id
    AND status = 'APPROVED';
UPDATE products
SET review_count = v_count,
    average_rating = ROUND(v_avg, 2),
    five_star_count = v_five,
    four_star_count = v_four,
    three_star_count = v_three,
    two_star_count = v_two,
    one_star_count = v_one,
    updated_at = NOW()
WHERE id = p_product_id;
END;
$$;
-- ----------------------------------------------------------------
-- sp_calculate_order_totals
-- Recalcula los totales de una orden desde sus items
-- ----------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_calculate_order_totals(IN p_order_id BIGINT) LANGUAGE plpgsql AS $$
DECLARE v_subtotal NUMERIC(10, 2);
v_discount NUMERIC(10, 2);
v_tax NUMERIC(10, 2);
v_shipping NUMERIC(10, 2);
BEGIN
SELECT COALESCE(SUM(subtotal), 0) INTO v_subtotal
FROM order_items
WHERE order_id = p_order_id;
SELECT COALESCE(discount, 0),
    COALESCE(tax, 0),
    COALESCE(shipping, 0) INTO v_discount,
    v_tax,
    v_shipping
FROM orders
WHERE id = p_order_id;
UPDATE orders
SET subtotal = v_subtotal,
    total = v_subtotal + v_tax + v_shipping - v_discount,
    updated_at = NOW()
WHERE id = p_order_id;
END;
$$;
-- ----------------------------------------------------------------
-- sp_transfer_cart_to_order
-- Crea una orden a partir de un carrito de compras
-- ----------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_transfer_cart_to_order(
        IN p_cart_id BIGINT,
        OUT p_order_id BIGINT
    ) LANGUAGE plpgsql AS $$
DECLARE v_cart RECORD;
v_item RECORD;
v_order_number VARCHAR(50);
BEGIN
SELECT * INTO v_cart
FROM shopping_carts
WHERE id = p_cart_id
    AND status = 'ACTIVE';
IF NOT FOUND THEN p_order_id := NULL;
RETURN;
END IF;
v_order_number := 'ORD-' || TO_CHAR(NOW(), 'YYYYMMDD') || '-' || LPAD(
    CAST(FLOOR(RANDOM() * 99999 + 1) AS TEXT),
    5,
    '0'
);
INSERT INTO orders (
        order_number,
        user_id,
        status,
        subtotal,
        discount,
        tax,
        shipping,
        total,
        payment_status,
        shipping_status,
        created_at,
        updated_at
    )
VALUES (
        v_order_number,
        v_cart.user_id,
        'PENDING',
        v_cart.subtotal,
        COALESCE(v_cart.discount, 0),
        COALESCE(v_cart.tax, 0),
        0,
        v_cart.total,
        'PENDING',
        'PENDING',
        NOW(),
        NOW()
    )
RETURNING id INTO p_order_id;
FOR v_item IN
SELECT ci.*,
    p.name,
    p.sku
FROM cart_items ci
    JOIN products p ON ci.product_id = p.id
WHERE ci.cart_id = p_cart_id LOOP
INSERT INTO order_items (
        order_id,
        product_id,
        product_name,
        product_sku,
        quantity,
        unit_price,
        discount,
        subtotal,
        created_at
    )
VALUES (
        p_order_id,
        v_item.product_id,
        v_item.name,
        v_item.sku,
        v_item.quantity,
        v_item.unit_price,
        0,
        v_item.subtotal,
        NOW()
    );
END LOOP;
UPDATE shopping_carts
SET status = 'CONVERTED'
WHERE id = p_cart_id;
END;
$$;
-- ----------------------------------------------------------------
-- sp_cancel_order
-- Cancela una orden y restaura el stock
-- ----------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_cancel_order(
        IN p_order_id BIGINT,
        IN p_reason TEXT
    ) LANGUAGE plpgsql AS $$
DECLARE v_item RECORD;
BEGIN
UPDATE orders
SET status = 'CANCELLED',
    cancellation_reason = p_reason,
    cancelled_at = NOW(),
    updated_at = NOW()
WHERE id = p_order_id;
-- Restaurar stock de productos
FOR v_item IN
SELECT product_id,
    quantity
FROM order_items
WHERE order_id = p_order_id LOOP
UPDATE products
SET stock = stock + v_item.quantity,
    updated_at = NOW()
WHERE id = v_item.product_id;
END LOOP;
END;
$$;
-- ----------------------------------------------------------------
-- sp_generate_order_number
-- Genera un número de orden único
-- ----------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_generate_order_number(OUT p_order_number VARCHAR(50)) LANGUAGE plpgsql AS $$ BEGIN p_order_number := 'ORD-' || TO_CHAR(NOW(), 'YYYYMMDD') || '-' || LPAD(
        CAST(FLOOR(RANDOM() * 99999 + 1) AS TEXT),
        5,
        '0'
    );
END;
$$;
-- ----------------------------------------------------------------
-- sp_update_user_stats
-- Actualiza estadísticas de un usuario (placeholder)
-- ----------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_update_user_stats(IN p_user_id BIGINT) LANGUAGE plpgsql AS $$ BEGIN -- Las estadísticas se actualizan automáticamente vía JPA
    -- Este SP existe para compatibilidad con llamadas existentes
UPDATE users
SET updated_at = NOW()
WHERE id = p_user_id;
END;
$$;
-- ----------------------------------------------------------------
-- sp_move_wishlist_to_cart
-- Mueve un item de wishlist al carrito activo del usuario
-- ----------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_move_wishlist_to_cart(IN p_wishlist_id BIGINT) LANGUAGE plpgsql AS $$
DECLARE v_wishlist RECORD;
v_cart_id BIGINT;
v_product RECORD;
BEGIN
SELECT * INTO v_wishlist
FROM wishlists
WHERE id = p_wishlist_id;
IF NOT FOUND THEN RETURN;
END IF;
SELECT * INTO v_product
FROM products
WHERE id = v_wishlist.product_id
    AND active = true
    AND stock > 0;
IF NOT FOUND THEN RETURN;
END IF;
-- Obtener o crear carrito activo
SELECT id INTO v_cart_id
FROM shopping_carts
WHERE user_id = v_wishlist.user_id
    AND status = 'ACTIVE'
ORDER BY created_at DESC
LIMIT 1;
IF v_cart_id IS NULL THEN
INSERT INTO shopping_carts (
        user_id,
        status,
        subtotal,
        total,
        created_at,
        updated_at
    )
VALUES (v_wishlist.user_id, 'ACTIVE', 0, 0, NOW(), NOW())
RETURNING id INTO v_cart_id;
END IF;
-- Insertar o incrementar cantidad en carrito
IF EXISTS (
    SELECT 1
    FROM cart_items
    WHERE cart_id = v_cart_id
        AND product_id = v_wishlist.product_id
) THEN
UPDATE cart_items
SET quantity = quantity + 1,
    subtotal = (quantity + 1) * unit_price,
    updated_at = NOW()
WHERE cart_id = v_cart_id
    AND product_id = v_wishlist.product_id;
ELSE
INSERT INTO cart_items (
        cart_id,
        product_id,
        quantity,
        unit_price,
        subtotal,
        added_at,
        updated_at
    )
VALUES (
        v_cart_id,
        v_wishlist.product_id,
        1,
        v_product.price,
        v_product.price,
        NOW(),
        NOW()
    );
END IF;
-- Recalcular subtotal del carrito
UPDATE shopping_carts
SET subtotal = (
        SELECT COALESCE(SUM(subtotal), 0)
        FROM cart_items
        WHERE cart_id = v_cart_id
    ),
    total = (
        SELECT COALESCE(SUM(subtotal), 0)
        FROM cart_items
        WHERE cart_id = v_cart_id
    ),
    updated_at = NOW()
WHERE id = v_cart_id;
END;
$$;
-- ----------------------------------------------------------------
-- sp_check_wishlist_discounts
-- Marca items de wishlist que tienen descuento activo
-- ----------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_check_wishlist_discounts(IN p_user_id BIGINT) LANGUAGE plpgsql AS $$ BEGIN
UPDATE wishlists w
SET notified_discount = true,
    updated_at = NOW()
FROM products p
WHERE w.product_id = p.id
    AND w.user_id = p_user_id
    AND p.discount_price IS NOT NULL
    AND p.discount_price < p.price
    AND w.notified_discount = false;
END;
$$;
-- ----------------------------------------------------------------
-- sp_check_wishlist_back_in_stock
-- Marca items de wishlist que volvieron a tener stock
-- ----------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_check_wishlist_back_in_stock(IN p_user_id BIGINT) LANGUAGE plpgsql AS $$ BEGIN
UPDATE wishlists w
SET notified_back_in_stock = true,
    updated_at = NOW()
FROM products p
WHERE w.product_id = p.id
    AND w.user_id = p_user_id
    AND p.stock > 0
    AND w.notified_back_in_stock = false;
END;
$$;
-- ----------------------------------------------------------------
-- sp_get_wishlist_with_price_comparison
-- Placeholder para compatibilidad
-- ----------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_get_wishlist_with_price_comparison(IN p_user_id BIGINT) LANGUAGE plpgsql AS $$ BEGIN -- La comparación de precios se maneja en el servicio Java
    -- Este SP existe para compatibilidad
    RETURN;
END;
$$;