-- =====================================================
-- Script SQL: Inicialización de Permisos del Sistema
-- =====================================================
-- Este script crea los permisos granulares necesarios para RBAC Enterprise
-- Ejecutar DESPUÉS de que Hibernate haya creado las tablas
-- =====================================================
-- 1. PERMISOS DE GESTIÓN DE USUARIOS
-- =====================================================
INSERT INTO permissions (
        name,
        description,
        category,
        created_at,
        updated_at
    )
VALUES (
        'VIEW_USERS',
        'Ver lista de usuarios del sistema',
        'USER_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'CREATE_USER',
        'Crear nuevos usuarios (Staff)',
        'USER_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'UPDATE_USER',
        'Actualizar información de usuarios',
        'USER_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'DELETE_USER',
        'Eliminar usuarios (soft delete)',
        'USER_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'ACTIVATE_USER',
        'Activar cuentas de usuario',
        'USER_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'DEACTIVATE_USER',
        'Desactivar cuentas de usuario',
        'USER_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'LOCK_ACCOUNT',
        'Bloquear cuentas de usuario',
        'USER_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'UNLOCK_ACCOUNT',
        'Desbloquear cuentas de usuario',
        'USER_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'ASSIGN_ROLES',
        'Asignar roles a usuarios',
        'USER_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'VIEW_USER_DETAILS',
        'Ver detalles completos de usuarios',
        'USER_MANAGEMENT',
        NOW(),
        NOW()
    );
-- =====================================================
-- 2. PERMISOS DE GESTIÓN DE ROLES Y PERMISOS
-- =====================================================
INSERT INTO permissions (
        name,
        description,
        category,
        created_at,
        updated_at
    )
VALUES (
        'VIEW_ROLES',
        'Ver lista de roles del sistema',
        'ROLE_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'CREATE_ROLE',
        'Crear nuevos roles',
        'ROLE_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'UPDATE_ROLE',
        'Actualizar configuración de roles',
        'ROLE_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'DELETE_ROLE',
        'Eliminar roles del sistema',
        'ROLE_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'VIEW_PERMISSIONS',
        'Ver lista de permisos disponibles',
        'ROLE_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'ASSIGN_PERMISSIONS',
        'Asignar permisos a roles',
        'ROLE_MANAGEMENT',
        NOW(),
        NOW()
    );
-- =====================================================
-- 3. PERMISOS DE GESTIÓN DE PRODUCTOS
-- =====================================================
INSERT INTO permissions (
        name,
        description,
        category,
        created_at,
        updated_at
    )
VALUES (
        'VIEW_PRODUCTS',
        'Ver lista de productos',
        'PRODUCT_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'CREATE_PRODUCT',
        'Crear nuevos productos',
        'PRODUCT_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'UPDATE_PRODUCT',
        'Actualizar información de productos',
        'PRODUCT_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'DELETE_PRODUCT',
        'Eliminar productos',
        'PRODUCT_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'MANAGE_STOCK',
        'Gestionar inventario y stock',
        'PRODUCT_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'MANAGE_PRICES',
        'Gestionar precios de productos',
        'PRODUCT_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'VIEW_PRODUCT_REVIEWS',
        'Ver reseñas de productos',
        'PRODUCT_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'MODERATE_REVIEWS',
        'Moderar y eliminar reseñas',
        'PRODUCT_MANAGEMENT',
        NOW(),
        NOW()
    );
-- =====================================================
-- 4. PERMISOS DE GESTIÓN DE ÓRDENES
-- =====================================================
INSERT INTO permissions (
        name,
        description,
        category,
        created_at,
        updated_at
    )
VALUES (
        'VIEW_ORDERS',
        'Ver lista de órdenes',
        'ORDER_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'VIEW_ORDER_DETAILS',
        'Ver detalles completos de órdenes',
        'ORDER_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'UPDATE_ORDER_STATUS',
        'Actualizar estado de órdenes',
        'ORDER_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'UPDATE_PAYMENT_STATUS',
        'Actualizar estado de pago',
        'ORDER_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'UPDATE_SHIPPING_STATUS',
        'Actualizar estado de envío',
        'ORDER_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'CANCEL_ORDER',
        'Cancelar órdenes',
        'ORDER_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'PROCESS_REFUND',
        'Procesar reembolsos',
        'ORDER_MANAGEMENT',
        NOW(),
        NOW()
    );
-- =====================================================
-- 5. PERMISOS DE GESTIÓN DE CATEGORÍAS Y MARCAS
-- =====================================================
INSERT INTO permissions (
        name,
        description,
        category,
        created_at,
        updated_at
    )
VALUES (
        'VIEW_CATEGORIES',
        'Ver lista de categorías',
        'CATALOG_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'MANAGE_CATEGORIES',
        'Crear, actualizar y eliminar categorías',
        'CATALOG_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'VIEW_BRANDS',
        'Ver lista de marcas',
        'CATALOG_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'MANAGE_BRANDS',
        'Crear, actualizar y eliminar marcas',
        'CATALOG_MANAGEMENT',
        NOW(),
        NOW()
    );
-- =====================================================
-- 6. PERMISOS DE GESTIÓN DE CUPONES
-- =====================================================
INSERT INTO permissions (
        name,
        description,
        category,
        created_at,
        updated_at
    )
VALUES (
        'VIEW_COUPONS',
        'Ver lista de cupones',
        'COUPON_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'CREATE_COUPON',
        'Crear nuevos cupones',
        'COUPON_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'UPDATE_COUPON',
        'Actualizar información de cupones',
        'COUPON_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'DELETE_COUPON',
        'Eliminar cupones',
        'COUPON_MANAGEMENT',
        NOW(),
        NOW()
    ),
    (
        'VIEW_COUPON_USAGE',
        'Ver estadísticas de uso de cupones',
        'COUPON_MANAGEMENT',
        NOW(),
        NOW()
    );
-- =====================================================
-- 7. PERMISOS DE REPORTES Y ESTADÍSTICAS
-- =====================================================
INSERT INTO permissions (
        name,
        description,
        category,
        created_at,
        updated_at
    )
VALUES (
        'VIEW_DASHBOARD',
        'Ver dashboard de administración',
        'REPORTS',
        NOW(),
        NOW()
    ),
    (
        'VIEW_SALES_REPORTS',
        'Ver reportes de ventas',
        'REPORTS',
        NOW(),
        NOW()
    ),
    (
        'VIEW_USER_REPORTS',
        'Ver reportes de usuarios',
        'REPORTS',
        NOW(),
        NOW()
    ),
    (
        'VIEW_PRODUCT_REPORTS',
        'Ver reportes de productos',
        'REPORTS',
        NOW(),
        NOW()
    ),
    (
        'EXPORT_REPORTS',
        'Exportar reportes a Excel/PDF',
        'REPORTS',
        NOW(),
        NOW()
    ),
    (
        'VIEW_AUDIT_LOGS',
        'Ver logs de auditoría del sistema',
        'REPORTS',
        NOW(),
        NOW()
    );
-- =====================================================
-- 8. PERMISOS DE CONFIGURACIÓN DEL SISTEMA
-- =====================================================
INSERT INTO permissions (
        name,
        description,
        category,
        created_at,
        updated_at
    )
VALUES (
        'VIEW_SETTINGS',
        'Ver configuración del sistema',
        'SYSTEM_SETTINGS',
        NOW(),
        NOW()
    ),
    (
        'UPDATE_SETTINGS',
        'Actualizar configuración del sistema',
        'SYSTEM_SETTINGS',
        NOW(),
        NOW()
    ),
    (
        'MANAGE_SECURITY',
        'Gestionar configuración de seguridad',
        'SYSTEM_SETTINGS',
        NOW(),
        NOW()
    ),
    (
        'VIEW_SYSTEM_LOGS',
        'Ver logs del sistema',
        'SYSTEM_SETTINGS',
        NOW(),
        NOW()
    );
-- =====================================================
-- 9. ASIGNACIÓN DE PERMISOS A ROLES EXISTENTES
-- =====================================================
-- ROLE_SUPER_ADMIN: Tiene TODOS los permisos del sistema
INSERT INTO role_permissions (role_id, permission_id)
SELECT (
        SELECT id
        FROM roles
        WHERE name = 'ROLE_SUPER_ADMIN'
    ) as role_id,
    id as permission_id
FROM permissions;
-- ROLE_ADMIN: Tiene permisos administrativos principales (excepto gestión de roles y configuración crítica)
INSERT INTO role_permissions (role_id, permission_id)
SELECT (
        SELECT id
        FROM roles
        WHERE name = 'ROLE_ADMIN'
    ) as role_id,
    id as permission_id
FROM permissions
WHERE category IN (
        'USER_MANAGEMENT',
        'PRODUCT_MANAGEMENT',
        'ORDER_MANAGEMENT',
        'CATALOG_MANAGEMENT',
        'COUPON_MANAGEMENT',
        'REPORTS'
    )
    AND name NOT IN ('DELETE_USER', 'ASSIGN_ROLES');
-- ROLE_MODERATOR: Permisos limitados (productos, órdenes, reseñas)
INSERT INTO role_permissions (role_id, permission_id)
SELECT (
        SELECT id
        FROM roles
        WHERE name = 'ROLE_MODERATOR'
    ) as role_id,
    id as permission_id
FROM permissions
WHERE name IN (
        'VIEW_PRODUCTS',
        'UPDATE_PRODUCT',
        'VIEW_PRODUCT_REVIEWS',
        'MODERATE_REVIEWS',
        'VIEW_ORDERS',
        'VIEW_ORDER_DETAILS',
        'UPDATE_ORDER_STATUS',
        'VIEW_DASHBOARD',
        'VIEW_SALES_REPORTS'
    );
-- ROLE_USER: Permisos básicos de usuario (solo lectura)
INSERT INTO role_permissions (role_id, permission_id)
SELECT (
        SELECT id
        FROM roles
        WHERE name = 'ROLE_USER'
    ) as role_id,
    id as permission_id
FROM permissions
WHERE name IN (
        'VIEW_PRODUCTS',
        'VIEW_CATEGORIES',
        'VIEW_BRANDS'
    );
-- =====================================================
-- 10. VERIFICACIÓN
-- =====================================================
-- Ver total de permisos creados
SELECT COUNT(*) as total_permissions
FROM permissions;
-- Ver permisos por categoría
SELECT category,
    COUNT(*) as permission_count
FROM permissions
GROUP BY category
ORDER BY category;
-- Ver permisos asignados a ROLE_ADMIN
SELECT r.name as role_name,
    p.name as permission_name,
    p.category
FROM roles r
    INNER JOIN role_permissions rp ON r.id = rp.role_id
    INNER JOIN permissions p ON rp.permission_id = p.id
WHERE r.name = 'ROLE_ADMIN'
ORDER BY p.category,
    p.name;
-- Ver cantidad de permisos por rol
SELECT r.name as role_name,
    COUNT(rp.permission_id) as permission_count
FROM roles r
    LEFT JOIN role_permissions rp ON r.id = rp.role_id
GROUP BY r.name
ORDER BY permission_count DESC;