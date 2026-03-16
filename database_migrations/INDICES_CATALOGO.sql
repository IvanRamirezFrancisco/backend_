-- ============================================================
--  ÍNDICES para ordenamiento y filtrado del Catálogo Público
--  Ejecutar manualmente en la base de datos de producción/dev
-- ============================================================

-- 1. Índice para ordenar por precio (ASC y DESC)
--    Cubre: sortBy=price_asc y sortBy=price_desc
CREATE INDEX IF NOT EXISTS idx_products_price
    ON products (price);

-- 2. Índice para ordenar por nombre A→Z
--    Cubre: sortBy=name_asc
CREATE INDEX IF NOT EXISTS idx_products_name
    ON products (name);

-- 3. Índice compuesto: destacados primero + más recientes
--    Cubre: sortBy=featured (orden por defecto)
CREATE INDEX IF NOT EXISTS idx_products_featured_created
    ON products (featured DESC, created_at DESC);

-- 4. Índice para filtrar por categoría (WHERE clause del catálogo)
CREATE INDEX IF NOT EXISTS idx_products_category_id
    ON products (category_id);

-- 5. Índice para filtrar por marca
CREATE INDEX IF NOT EXISTS idx_products_brand_id
    ON products (brand_id);

-- 6. Índice para filtrar solo productos activos (se usa en EVERY query)
CREATE INDEX IF NOT EXISTS idx_products_active
    ON products (active);

-- 7. Índice compuesto: active + category_id (filtro más común)
CREATE INDEX IF NOT EXISTS idx_products_active_category
    ON products (active, category_id);

-- 8. Índice compuesto: active + featured (para el endpoint /featured)
CREATE INDEX IF NOT EXISTS idx_products_active_featured
    ON products (active, featured);

-- 9. Búsqueda por keyword sobre name (LIKE '%keyword%')
--    Nota: en MySQL/MariaDB usar FULLTEXT para mejor performance
--    CREATE FULLTEXT INDEX idx_products_name_ft ON products (name, sku);
--    En PostgreSQL usar GIN/trgm:
--    CREATE EXTENSION IF NOT EXISTS pg_trgm;
--    CREATE INDEX idx_products_name_trgm ON products USING GIN (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_products_sku
    ON products (sku);

-- ============================================================
--  Verificar índices creados (MySQL/MariaDB)
-- ============================================================
-- SHOW INDEX FROM products;

-- ============================================================
--  Verificar índices creados (PostgreSQL)
-- ============================================================
-- SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'products';
