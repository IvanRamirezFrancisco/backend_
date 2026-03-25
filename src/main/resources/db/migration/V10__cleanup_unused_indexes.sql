-- ============================================================
-- V10: Limpieza de índices sin uso en PostgreSQL
-- Confirmados con pg_stat_user_indexes (idx_scan = 0)
-- ============================================================
-- PASO 1: Eliminar restricción duplicada en coupons
-- (fue creada como CONSTRAINT por unique=true en JPA,
--  ya existe idx_coupons_code_key como restricción de unicidad)
ALTER TABLE coupons DROP CONSTRAINT IF EXISTS idx_coupon_code;
-- PASO 2: Eliminar índices duplicados
DROP INDEX IF EXISTS idx_order_number;
DROP INDEX IF EXISTS idx_product_attributes_product_id;
DROP INDEX IF EXISTS idx_products_category_active;
-- PASO 3: Eliminar índices sin uso justificado
DROP INDEX IF EXISTS idx_products_featured_created;
DROP INDEX IF EXISTS idx_products_active_featured;
DROP INDEX IF EXISTS idx_products_name_trgm;
DROP INDEX IF EXISTS idx_wishlist_priority;
DROP INDEX IF EXISTS idx_wishlist_notified;
DROP INDEX IF EXISTS idx_cart_session;
DROP INDEX IF EXISTS idx_cart_expires;
DROP INDEX IF EXISTS idx_review_rating;
DROP INDEX IF EXISTS idx_review_verified;
DROP INDEX IF EXISTS idx_addresses_country;