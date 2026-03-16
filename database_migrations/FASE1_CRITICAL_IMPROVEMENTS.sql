-- =====================================================
-- FASE 1: MEJORAS CRÍTICAS - BASE DE DATOS PROFESIONAL
-- =====================================================
-- Proyecto: Sistema de E-commerce con Autenticación
-- Base de Datos: security_db
-- Fecha: Febrero 15, 2026
-- Versión: 1.0.0
-- 
-- IMPORTANTE: 
-- 1. HACER BACKUP COMPLETO ANTES DE EJECUTAR
-- 2. EJECUTAR EN AMBIENTE DE DESARROLLO PRIMERO
-- 3. VERIFICAR CADA PASO ANTES DE CONTINUAR
-- =====================================================
USE security_db;
SET FOREIGN_KEY_CHECKS = 0;
SET SQL_MODE = 'NO_AUTO_VALUE_ON_ZERO';
SET AUTOCOMMIT = 0;
START TRANSACTION;
-- =====================================================
-- PASO 1.1: UNIFICAR USERS Y CUSTOMERS
-- =====================================================
-- Problema: Duplicación de entidades (users vs customers)
-- Solución: Agregar campos de customer a users
-- Tiempo estimado: 5 minutos
-- =====================================================
SELECT '=== PASO 1.1: Agregando campos de customer a users ===' as info;
-- Agregar campos de información personal
ALTER TABLE users
ADD COLUMN first_name VARCHAR(100) NULL
AFTER username,
    ADD COLUMN last_name VARCHAR(100) NULL
AFTER first_name,
    ADD COLUMN phone VARCHAR(20) NULL
AFTER email;
-- Agregar campos de cliente/comprador
ALTER TABLE users
ADD COLUMN is_customer BOOLEAN DEFAULT FALSE
AFTER phone,
    ADD COLUMN total_orders INT DEFAULT 0
AFTER is_customer,
    ADD COLUMN total_spent DECIMAL(15, 2) DEFAULT 0.00
AFTER total_orders;
-- Agregar campos de dirección temporal (migración)
ALTER TABLE users
ADD COLUMN address TEXT NULL
AFTER total_spent,
    ADD COLUMN city VARCHAR(100) NULL
AFTER address,
    ADD COLUMN state VARCHAR(100) NULL
AFTER city,
    ADD COLUMN postal_code VARCHAR(20) NULL
AFTER state,
    ADD COLUMN country VARCHAR(50) NULL
AFTER postal_code;
-- Índices para mejorar búsquedas
CREATE INDEX idx_users_customer ON users(is_customer, enabled);
CREATE INDEX idx_users_name ON users(first_name, last_name);
CREATE INDEX idx_users_phone ON users(phone);
SELECT CONCAT(
        '✅ Campos agregados a users. Total columnas: ',
        COUNT(*)
    ) as resultado
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'security_db'
    AND TABLE_NAME = 'users';
-- =====================================================
-- PASO 1.2: MIGRAR DATOS DE CUSTOMERS A USERS
-- =====================================================
-- Migrar información de customers existentes a users
-- =====================================================
SELECT '=== PASO 1.2: Migrando datos de customers a users ===' as info;
-- Verificar si hay customers que necesitan migración
SELECT COUNT(*) as customers_to_migrate
FROM customers;
-- Actualizar users con datos de customers (por email coincidente)
UPDATE users u
    INNER JOIN customers c ON u.email = c.email
SET u.first_name = c.first_name,
    u.last_name = c.last_name,
    u.phone = c.phone,
    u.is_customer = TRUE,
    u.total_orders = c.total_orders,
    u.total_spent = c.total_spent,
    u.address = c.address,
    u.city = c.city,
    u.state = c.state,
    u.postal_code = c.postal_code,
    u.country = c.country;
SELECT CONCAT('✅ Usuarios actualizados: ', ROW_COUNT()) as resultado;
-- =====================================================
-- PASO 1.3: ACTUALIZAR FOREIGN KEY EN ORDERS
-- =====================================================
-- Cambiar customer_id por user_id en orders
-- =====================================================
SELECT '=== PASO 1.3: Actualizando relación orders -> users ===' as info;
-- Primero, agregar columna user_id temporal
ALTER TABLE orders
ADD COLUMN user_id BIGINT NULL
AFTER customer_id;
-- Copiar los IDs correctos usando el email como referencia
UPDATE orders o
    INNER JOIN customers c ON o.customer_id = c.id
    INNER JOIN users u ON c.email = u.email
SET o.user_id = u.id;
-- Verificar que todos los orders tienen user_id
SELECT COUNT(*) as orders_sin_user
FROM orders
WHERE user_id IS NULL;
-- Si todo está bien (0 orders sin user), eliminar FK antigua
ALTER TABLE orders DROP FOREIGN KEY FKpxtb8awmi0dk6smoh2vp1litg;
-- Hacer user_id NOT NULL
ALTER TABLE orders
MODIFY COLUMN user_id BIGINT NOT NULL;
-- Crear nueva FK apuntando a users
ALTER TABLE orders
ADD CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT;
-- Eliminar customer_id antigua
ALTER TABLE orders DROP COLUMN customer_id;
-- Índice para mejorar consultas de órdenes por usuario
CREATE INDEX idx_orders_user_status ON orders(user_id, status, created_at);
SELECT '✅ Relación orders -> users actualizada correctamente' as resultado;
-- =====================================================
-- PASO 1.4: ELIMINAR TABLA CUSTOMERS (YA NO SE NECESITA)
-- =====================================================
SELECT '=== PASO 1.4: Eliminando tabla customers obsoleta ===' as info;
-- Guardar registro de cuántos customers había
SELECT COUNT(*) as customers_eliminados
FROM customers;
-- Eliminar tabla customers
DROP TABLE IF EXISTS customers;
SELECT '✅ Tabla customers eliminada correctamente' as resultado;
-- =====================================================
-- PASO 2: NORMALIZAR DIRECCIONES
-- =====================================================
-- Crear tabla de países y direcciones separadas
-- =====================================================
SELECT '=== PASO 2.1: Creando tabla countries ===' as info;
CREATE TABLE IF NOT EXISTS countries (
    id INT PRIMARY KEY AUTO_INCREMENT,
    code CHAR(2) NOT NULL UNIQUE COMMENT 'ISO 3166-1 alpha-2',
    name VARCHAR(100) NOT NULL,
    phone_prefix VARCHAR(5) NULL,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_country_code(code),
    INDEX idx_country_active(active)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Catálogo de países (ISO 3166-1)';
-- Insertar países principales (Top 20 para e-commerce)
INSERT INTO countries (code, name, phone_prefix)
VALUES ('MX', 'México', '+52'),
    ('US', 'Estados Unidos', '+1'),
    ('CA', 'Canadá', '+1'),
    ('ES', 'España', '+34'),
    ('AR', 'Argentina', '+54'),
    ('CO', 'Colombia', '+57'),
    ('CL', 'Chile', '+56'),
    ('PE', 'Perú', '+51'),
    ('VE', 'Venezuela', '+58'),
    ('EC', 'Ecuador', '+593'),
    ('BO', 'Bolivia', '+591'),
    ('PY', 'Paraguay', '+595'),
    ('UY', 'Uruguay', '+598'),
    ('CR', 'Costa Rica', '+506'),
    ('PA', 'Panamá', '+507'),
    ('GT', 'Guatemala', '+502'),
    ('HN', 'Honduras', '+504'),
    ('SV', 'El Salvador', '+503'),
    ('NI', 'Nicaragua', '+505'),
    ('DO', 'República Dominicana', '+1809');
SELECT CONCAT('✅ Países insertados: ', ROW_COUNT()) as resultado;
-- =====================================================
-- PASO 2.2: CREAR TABLA DE DIRECCIONES
-- =====================================================
SELECT '=== PASO 2.2: Creando tabla addresses ===' as info;
CREATE TABLE IF NOT EXISTS addresses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    address_type ENUM('BILLING', 'SHIPPING', 'BOTH') DEFAULT 'BOTH' COMMENT 'Tipo de dirección: facturación, envío o ambas',
    -- Datos de la dirección
    street VARCHAR(200) NOT NULL COMMENT 'Calle y número',
    apartment VARCHAR(50) NULL COMMENT 'Departamento, piso, oficina',
    neighborhood VARCHAR(100) NULL COMMENT 'Colonia o barrio',
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL COMMENT 'Estado o provincia',
    postal_code VARCHAR(20) NOT NULL,
    country_id INT NOT NULL,
    -- Campos adicionales
    recipient_name VARCHAR(200) NULL COMMENT 'Nombre de quien recibe',
    recipient_phone VARCHAR(20) NULL COMMENT 'Teléfono de contacto',
    reference VARCHAR(300) NULL COMMENT 'Referencias para encontrar la dirección',
    -- Control
    is_default BOOLEAN DEFAULT FALSE COMMENT 'Dirección por defecto',
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (country_id) REFERENCES countries(id) ON DELETE RESTRICT,
    INDEX idx_addresses_user(user_id, active),
    INDEX idx_addresses_default(user_id, is_default),
    INDEX idx_addresses_country(country_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Direcciones de usuarios (envío y facturación)';
SELECT '✅ Tabla addresses creada correctamente' as resultado;
-- =====================================================
-- PASO 2.3: MIGRAR DIRECCIONES DE USERS A ADDRESSES
-- =====================================================
SELECT '=== PASO 2.3: Migrando direcciones existentes ===' as info;
-- Migrar direcciones de users que tienen datos de dirección
INSERT INTO addresses (
        user_id,
        address_type,
        street,
        city,
        state,
        postal_code,
        country_id,
        is_default,
        active
    )
SELECT u.id,
    'BOTH' as address_type,
    COALESCE(u.address, 'Sin dirección') as street,
    COALESCE(u.city, 'Sin ciudad') as city,
    COALESCE(u.state, 'Sin estado') as state,
    COALESCE(u.postal_code, '00000') as postal_code,
    (
        SELECT id
        FROM countries
        WHERE code = 'MX'
        LIMIT 1
    ) as country_id,
    TRUE as is_default,
    TRUE as active
FROM users u
WHERE u.is_customer = TRUE
    AND (
        u.address IS NOT NULL
        OR u.city IS NOT NULL
    );
SELECT CONCAT('✅ Direcciones migradas: ', ROW_COUNT()) as resultado;
-- Limpiar campos de dirección de users (ya no se necesitan)
ALTER TABLE users DROP COLUMN address,
    DROP COLUMN city,
    DROP COLUMN state,
    DROP COLUMN postal_code,
    DROP COLUMN country;
SELECT '✅ Campos de dirección eliminados de users' as resultado;
-- =====================================================
-- PASO 3: HISTORIAL DE PRECIOS DE PRODUCTOS
-- =====================================================
SELECT '=== PASO 3: Creando tabla product_price_history ===' as info;
CREATE TABLE IF NOT EXISTS product_price_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    -- Precios anteriores y nuevos
    old_price DECIMAL(10, 2) NULL COMMENT 'Precio anterior',
    new_price DECIMAL(10, 2) NOT NULL COMMENT 'Precio nuevo',
    old_discount_price DECIMAL(10, 2) NULL COMMENT 'Precio con descuento anterior',
    new_discount_price DECIMAL(10, 2) NULL COMMENT 'Precio con descuento nuevo',
    -- Auditoría del cambio
    changed_by BIGINT NULL COMMENT 'ID del usuario que realizó el cambio',
    reason VARCHAR(200) NULL COMMENT 'Razón del cambio: "Black Friday", "Liquidación", etc.',
    -- Vigencia
    effective_from TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Desde cuándo aplica',
    effective_to TIMESTAMP NULL COMMENT 'Hasta cuándo aplicó (NULL = vigente)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    FOREIGN KEY (changed_by) REFERENCES users(id) ON DELETE
    SET NULL,
        INDEX idx_price_history_product(product_id, effective_from),
        INDEX idx_price_history_dates(effective_from, effective_to),
        INDEX idx_price_history_user(changed_by)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Historial de cambios de precios de productos';
SELECT '✅ Tabla product_price_history creada' as resultado;
-- Insertar precios actuales como historial inicial
INSERT INTO product_price_history (
        product_id,
        old_price,
        new_price,
        old_discount_price,
        new_discount_price,
        reason,
        effective_from
    )
SELECT id,
    NULL as old_price,
    price as new_price,
    NULL as old_discount_price,
    discount_price as new_discount_price,
    'Precio inicial' as reason,
    created_at as effective_from
FROM products
WHERE price IS NOT NULL;
SELECT CONCAT('✅ Precios iniciales registrados: ', ROW_COUNT()) as resultado;
-- =====================================================
-- PASO 4: TRIGGERS DE AUDITORÍA AUTOMÁTICA
-- =====================================================
SELECT '=== PASO 4: Creando triggers de auditoría ===' as info;
DELIMITER // -- ============================================
-- TRIGGER 4.1: Auditar actualizaciones de users
-- ============================================
DROP TRIGGER IF EXISTS trg_users_after_update // CREATE TRIGGER trg_users_after_update
AFTER
UPDATE ON users FOR EACH ROW BEGIN
DECLARE changes_detected BOOLEAN DEFAULT FALSE;
DECLARE change_description TEXT DEFAULT '';
-- Detectar cambios importantes
IF OLD.email != NEW.email THEN
SET changes_detected = TRUE;
SET change_description = CONCAT(change_description, 'Email cambiado; ');
END IF;
IF OLD.enabled != NEW.enabled THEN
SET changes_detected = TRUE;
SET change_description = CONCAT(
        change_description,
        IF(
            NEW.enabled = 1,
            'Usuario habilitado; ',
            'Usuario deshabilitado; '
        )
    );
END IF;
IF OLD.password != NEW.password THEN
SET changes_detected = TRUE;
SET change_description = CONCAT(change_description, 'Contraseña modificada; ');
END IF;
-- Si hubo cambios, registrar en audit_logs
IF changes_detected THEN
INSERT INTO audit_logs (
        user_id,
        event_type,
        event_description,
        resource_affected,
        old_values,
        new_values,
        severity,
        status,
        created_at
    )
VALUES (
        NEW.id,
        'USER_UPDATE',
        TRIM(
            TRAILING '; '
            FROM change_description
        ),
        CONCAT('users:', NEW.id),
        JSON_OBJECT(
            'email',
            OLD.email,
            'enabled',
            OLD.enabled,
            'username',
            OLD.username
        ),
        JSON_OBJECT(
            'email',
            NEW.email,
            'enabled',
            NEW.enabled,
            'username',
            NEW.username
        ),
        'MEDIUM',
        'SUCCESS',
        NOW()
    );
END IF;
END // -- ============================================
-- TRIGGER 4.2: Auditar eliminación de users
-- ============================================
DROP TRIGGER IF EXISTS trg_users_after_delete // CREATE TRIGGER trg_users_after_delete
AFTER DELETE ON users FOR EACH ROW BEGIN
INSERT INTO audit_logs (
        user_id,
        event_type,
        event_description,
        resource_affected,
        old_values,
        severity,
        status,
        created_at
    )
VALUES (
        OLD.id,
        'USER_DELETE',
        CONCAT('Usuario eliminado: ', OLD.username),
        CONCAT('users:', OLD.id),
        JSON_OBJECT(
            'email',
            OLD.email,
            'username',
            OLD.username,
            'enabled',
            OLD.enabled
        ),
        'HIGH',
        'SUCCESS',
        NOW()
    );
END // -- ============================================
-- TRIGGER 4.3: Auditar cambios de precio en products
-- ============================================
DROP TRIGGER IF EXISTS trg_products_price_change // CREATE TRIGGER trg_products_price_change
AFTER
UPDATE ON products FOR EACH ROW BEGIN -- Registrar cambio de precio
    IF OLD.price != NEW.price
    OR COALESCE(OLD.discount_price, 0) != COALESCE(NEW.discount_price, 0) THEN -- Cerrar registro anterior de precio (marcar effective_to)
UPDATE product_price_history
SET effective_to = NOW()
WHERE product_id = NEW.id
    AND effective_to IS NULL;
-- Insertar nuevo registro de precio
INSERT INTO product_price_history (
        product_id,
        old_price,
        new_price,
        old_discount_price,
        new_discount_price,
        changed_by,
        reason,
        effective_from
    )
VALUES (
        NEW.id,
        OLD.price,
        NEW.price,
        OLD.discount_price,
        NEW.discount_price,
        @current_user_id,
        -- Variable de sesión (setear desde Java)
        @price_change_reason,
        -- Variable de sesión
        NOW()
    );
-- Registrar en audit_logs
INSERT INTO audit_logs (
        event_type,
        event_description,
        resource_affected,
        old_values,
        new_values,
        severity,
        status
    )
VALUES (
        'PRODUCT_PRICE_CHANGE',
        CONCAT('Precio modificado: ', NEW.name),
        CONCAT('products:', NEW.id),
        JSON_OBJECT(
            'price',
            OLD.price,
            'discount_price',
            OLD.discount_price
        ),
        JSON_OBJECT(
            'price',
            NEW.price,
            'discount_price',
            NEW.discount_price
        ),
        'MEDIUM',
        'SUCCESS'
    );
END IF;
-- Registrar cambio de stock
IF OLD.stock != NEW.stock THEN
INSERT INTO audit_logs (
        event_type,
        event_description,
        resource_affected,
        old_values,
        new_values,
        severity,
        status
    )
VALUES (
        'PRODUCT_STOCK_CHANGE',
        CONCAT('Stock modificado: ', NEW.name),
        CONCAT('products:', NEW.id),
        JSON_OBJECT('stock', OLD.stock),
        JSON_OBJECT('stock', NEW.stock),
        IF(NEW.stock < 5, 'HIGH', 'LOW'),
        'SUCCESS'
    );
END IF;
END // -- ============================================
-- TRIGGER 4.4: Auditar creación de orders
-- ============================================
DROP TRIGGER IF EXISTS trg_orders_after_insert // CREATE TRIGGER trg_orders_after_insert
AFTER
INSERT ON orders FOR EACH ROW BEGIN
INSERT INTO audit_logs (
        user_id,
        event_type,
        event_description,
        resource_affected,
        new_values,
        severity,
        status
    )
VALUES (
        NEW.user_id,
        'ORDER_CREATED',
        CONCAT('Nueva orden creada: ', NEW.order_number),
        CONCAT('orders:', NEW.id),
        JSON_OBJECT(
            'order_number',
            NEW.order_number,
            'total',
            NEW.total,
            'status',
            NEW.status
        ),
        'INFO',
        'SUCCESS'
    );
END // -- ============================================
-- TRIGGER 4.5: Auditar cambios de estado en orders
-- ============================================
DROP TRIGGER IF EXISTS trg_orders_status_change // CREATE TRIGGER trg_orders_status_change
AFTER
UPDATE ON orders FOR EACH ROW BEGIN IF OLD.status != NEW.status THEN
INSERT INTO audit_logs (
        user_id,
        event_type,
        event_description,
        resource_affected,
        old_values,
        new_values,
        severity,
        status
    )
VALUES (
        NEW.user_id,
        'ORDER_STATUS_CHANGE',
        CONCAT('Estado de orden cambiado: ', NEW.order_number),
        CONCAT('orders:', NEW.id),
        JSON_OBJECT('status', OLD.status),
        JSON_OBJECT('status', NEW.status),
        'MEDIUM',
        'SUCCESS'
    );
END IF;
END // DELIMITER;
SELECT '✅ Triggers de auditoría creados correctamente' as resultado;
-- =====================================================
-- PASO 5: ÍNDICES PARA MEJORAR PERFORMANCE
-- =====================================================
SELECT '=== PASO 5: Creando índices de performance ===' as info;
-- Índices en orders
CREATE INDEX idx_orders_dates ON orders(created_at, status);
CREATE INDEX idx_orders_user_date ON orders(user_id, created_at DESC);
-- Índices en products
CREATE INDEX idx_products_active_category ON products(active, category_id);
CREATE INDEX idx_products_price_range ON products(price, discount_price);
CREATE INDEX idx_products_stock_alert ON products(stock, active);
CREATE INDEX idx_products_sales ON products(sales_count DESC);
-- Índices en order_items
CREATE INDEX idx_order_items_product ON order_items(product_id, order_id);
-- Índices en audit_logs
CREATE INDEX idx_audit_logs_user_date ON audit_logs(user_id, created_at DESC);
CREATE INDEX idx_audit_logs_event_date ON audit_logs(event_type, created_at DESC);
CREATE INDEX idx_audit_logs_severity ON audit_logs(severity, created_at DESC);
-- Índices en login_attempts
CREATE INDEX idx_login_attempts_email_time ON login_attempts(email, attempt_time DESC);
CREATE INDEX idx_login_attempts_failed ON login_attempts(successful, attempt_time DESC);
SELECT '✅ Índices de performance creados' as resultado;
-- =====================================================
-- PASO 6: VERIFICACIÓN FINAL
-- =====================================================
SELECT '=== PASO 6: Verificación final ===' as info;
-- Verificar estructura de users
SELECT '✅ users' as tabla,
    COUNT(*) as total_usuarios,
    SUM(is_customer) as total_clientes,
    SUM(enabled) as usuarios_activos
FROM users;
-- Verificar addresses
SELECT '✅ addresses' as tabla,
    COUNT(*) as total_direcciones,
    COUNT(DISTINCT user_id) as usuarios_con_direccion
FROM addresses;
-- Verificar countries
SELECT '✅ countries' as tabla,
    COUNT(*) as total_paises
FROM countries;
-- Verificar product_price_history
SELECT '✅ product_price_history' as tabla,
    COUNT(*) as total_registros,
    COUNT(DISTINCT product_id) as productos_con_historial
FROM product_price_history;
-- Verificar triggers
SELECT '✅ triggers' as tipo,
    COUNT(*) as total_triggers
FROM information_schema.TRIGGERS
WHERE TRIGGER_SCHEMA = 'security_db';
-- Verificar índices nuevos
SELECT '✅ indices' as tipo,
    COUNT(*) as total_indices
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'security_db'
    AND INDEX_NAME LIKE 'idx_%';
-- =====================================================
-- FINALIZACIÓN
-- =====================================================
COMMIT;
SET FOREIGN_KEY_CHECKS = 1;
SELECT '
╔════════════════════════════════════════════════════════════╗
║                                                            ║
║   ✅ FASE 1 COMPLETADA EXITOSAMENTE                        ║
║                                                            ║
║   Mejoras implementadas:                                   ║
║   ✓ Users y Customers unificados                           ║
║   ✓ Direcciones normalizadas con tabla countries          ║
║   ✓ Historial de precios implementado                      ║
║   ✓ 5 Triggers de auditoría activos                        ║
║   ✓ 15+ Índices de performance agregados                   ║
║                                                            ║
║   Próximos pasos:                                          ║
║   1. Actualizar entidades Java (User, Address, Country)    ║
║   2. Probar consultas y triggers                           ║
║   3. Proceder con FASE 2 (Carrito, Reviews, Cupones)       ║
║                                                            ║
╚════════════════════════════════════════════════════════════╝
' as RESULTADO_FINAL;
-- =====================================================
-- ROLLBACK SCRIPT (En caso de emergencia)
-- =====================================================
/*
 Para revertir estos cambios, ejecuta:
 
 START TRANSACTION;
 
 -- Eliminar triggers
 DROP TRIGGER IF EXISTS trg_users_after_update;
 DROP TRIGGER IF EXISTS trg_users_after_delete;
 DROP TRIGGER IF EXISTS trg_products_price_change;
 DROP TRIGGER IF EXISTS trg_orders_after_insert;
 DROP TRIGGER IF EXISTS trg_orders_status_change;
 
 -- Eliminar tablas nuevas
 DROP TABLE IF EXISTS addresses;
 DROP TABLE IF EXISTS countries;
 DROP TABLE IF EXISTS product_price_history;
 
 -- Recrear tabla customers (desde backup)
 -- RESTORE desde tu backup
 
 COMMIT;
 */