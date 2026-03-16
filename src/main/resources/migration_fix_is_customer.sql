-- ============================================================
--  MIGRACIÓN: Corrección de is_customer en usuarios existentes
--  Base de datos: security_db
--  Fecha: 2026-02-28
--
--  PROBLEMA:
--    Los clientes registrados públicamente antes de este fix
--    tienen is_customer = 0 (falso), siendo tratados como Staff.
--
--  CRITERIO DE CORRECCIÓN:
--    Un usuario es CLIENTE si tiene SOLO el rol ROLE_USER y
--    NO tiene ningún rol de Staff (ADMIN, MODERATOR, MANAGER, etc.)
-- ============================================================
-- 1. Ver el estado actual antes de corregir (solo lectura)
SELECT u.id,
    u.email,
    u.first_name,
    u.last_name,
    u.is_customer,
    GROUP_CONCAT(
        r.name
        ORDER BY r.name SEPARATOR ', '
    ) AS roles
FROM users u
    LEFT JOIN user_roles ur ON u.id = ur.user_id
    LEFT JOIN roles r ON ur.role_id = r.id
GROUP BY u.id,
    u.email,
    u.first_name,
    u.last_name,
    u.is_customer
HAVING is_customer = 0
    AND roles = 'ROLE_USER'
ORDER BY u.created_at DESC;
-- ============================================================
-- 2. CORRECCIÓN: Marcar como clientes (is_customer = 1) a todos
--    los usuarios que tengan ÚNICAMENTE el rol ROLE_USER
--    y estén marcados incorrectamente como is_customer = 0
-- ============================================================
UPDATE users u
SET u.is_customer = 1,
    u.updated_at = NOW()
WHERE u.is_customer = 0
    AND u.id IN (
        -- Solo usuarios cuyo único rol sea ROLE_USER
        SELECT user_id
        FROM user_roles
        GROUP BY user_id
        HAVING COUNT(*) = 1
            AND MAX(role_id) = (
                SELECT id
                FROM roles
                WHERE name = 'ROLE_USER'
                LIMIT 1
            )
    );
-- 3. Verificar el resultado de la corrección
SELECT CONCAT(
        'Usuarios corregidos: ',
        ROW_COUNT()
    ) AS resultado;
-- 4. Resumen final del estado de la tabla
SELECT is_customer,
    COUNT(*) AS total_usuarios
FROM users
GROUP BY is_customer;