-- Script de datos de prueba para órdenes
-- Ejecutar después de que el backend esté corriendo
-- Verificar que existan usuarios (asumiendo que ya tienes usuarios en la BD)
-- Asumiendo que el user_id 1 existe (ajusta según tu BD)
-- Insertar órdenes de prueba
INSERT INTO orders (
        order_number,
        user_id,
        subtotal,
        tax,
        shipping,
        discount,
        total,
        status,
        payment_status,
        shipping_status,
        payment_method,
        shipping_address,
        billing_address,
        created_at,
        updated_at
    )
VALUES -- Orden 1: Pendiente de pago
    (
        'ORD-2026-00001',
        1,
        1500.00,
        240.00,
        150.00,
        0.00,
        1890.00,
        'PENDING',
        'PENDING',
        'PENDING',
        'TARJETA_CREDITO',
        'Av. Insurgentes 123, Col. Roma, CDMX, CP 06700',
        'Av. Insurgentes 123, Col. Roma, CDMX, CP 06700',
        NOW(),
        NOW()
    ),
    -- Orden 2: Confirmada y pagada, lista para envío
    (
        'ORD-2026-00002',
        1,
        2500.00,
        400.00,
        150.00,
        250.00,
        2800.00,
        'CONFIRMED',
        'PAID',
        'PREPARING',
        'PAYPAL',
        'Calle Morelos 456, Col. Centro, Guadalajara, JAL, CP 44100',
        'Calle Morelos 456, Col. Centro, Guadalajara, JAL, CP 44100',
        DATE_SUB(NOW(), INTERVAL 1 DAY),
        NOW()
    ),
    -- Orden 3: En proceso y enviada
    (
        'ORD-2026-00003',
        1,
        3200.00,
        512.00,
        150.00,
        0.00,
        3862.00,
        'PROCESSING',
        'PAID',
        'SHIPPED',
        'TRANSFERENCIA',
        'Blvd. Díaz Ordaz 789, Col. Las Palmas, Monterrey, NL, CP 64000',
        'Blvd. Díaz Ordaz 789, Col. Las Palmas, Monterrey, NL, CP 64000',
        DATE_SUB(NOW(), INTERVAL 3 DAY),
        NOW()
    ),
    -- Orden 4: Completada y entregada
    (
        'ORD-2026-00004',
        1,
        1800.00,
        288.00,
        150.00,
        180.00,
        2058.00,
        'COMPLETED',
        'PAID',
        'DELIVERED',
        'TARJETA_DEBITO',
        'Av. Universidad 321, Col. Del Valle, CDMX, CP 03100',
        'Av. Universidad 321, Col. Del Valle, CDMX, CP 03100',
        DATE_SUB(NOW(), INTERVAL 7 DAY),
        DATE_SUB(NOW(), INTERVAL 1 DAY)
    ),
    -- Orden 5: Cancelada
    (
        'ORD-2026-00005',
        1,
        950.00,
        152.00,
        150.00,
        0.00,
        1252.00,
        'CANCELLED',
        'REFUNDED',
        'PENDING',
        'TARJETA_CREDITO',
        'Calle Juárez 555, Col. Centro, Puebla, PUE, CP 72000',
        'Calle Juárez 555, Col. Centro, Puebla, PUE, CP 72000',
        DATE_SUB(NOW(), INTERVAL 5 DAY),
        DATE_SUB(NOW(), INTERVAL 4 DAY)
    ),
    -- Orden 6: Pendiente reciente
    (
        'ORD-2026-00006',
        1,
        4500.00,
        720.00,
        150.00,
        450.00,
        4920.00,
        'PENDING',
        'PENDING',
        'PENDING',
        'MERCADO_PAGO',
        'Av. Revolución 888, Col. San Ángel, CDMX, CP 01000',
        'Av. Revolución 888, Col. San Ángel, CDMX, CP 01000',
        NOW(),
        NOW()
    ),
    -- Orden 7: Confirmada recientemente
    (
        'ORD-2026-00007',
        1,
        2100.00,
        336.00,
        150.00,
        0.00,
        2586.00,
        'CONFIRMED',
        'PAID',
        'PENDING',
        'TARJETA_CREDITO',
        'Calzada de Tlalpan 999, Col. Portales, CDMX, CP 03300',
        'Calzada de Tlalpan 999, Col. Portales, CDMX, CP 03300',
        DATE_SUB(NOW(), INTERVAL 2 HOUR),
        NOW()
    );
-- Insertar items de órdenes (asumiendo que existen productos con IDs 1-5)
-- Ajusta los product_id según tu base de datos
-- Items para Orden 1
INSERT INTO order_items (
        order_id,
        product_id,
        product_name,
        product_sku,
        quantity,
        unit_price,
        subtotal,
        created_at,
        updated_at
    )
VALUES (
        (
            SELECT id
            FROM orders
            WHERE order_number = 'ORD-2026-00001'
        ),
        1,
        'Guitarra Acústica Yamaha',
        'GTA-YMH-001',
        1,
        1500.00,
        1500.00,
        NOW(),
        NOW()
    );
-- Items para Orden 2
INSERT INTO order_items (
        order_id,
        product_id,
        product_name,
        product_sku,
        quantity,
        unit_price,
        subtotal,
        created_at,
        updated_at
    )
VALUES (
        (
            SELECT id
            FROM orders
            WHERE order_number = 'ORD-2026-00002'
        ),
        2,
        'Teclado Casio 61 Teclas',
        'TEC-CAS-002',
        2,
        1250.00,
        2500.00,
        NOW(),
        NOW()
    );
-- Items para Orden 3
INSERT INTO order_items (
        order_id,
        product_id,
        product_name,
        product_sku,
        quantity,
        unit_price,
        subtotal,
        created_at,
        updated_at
    )
VALUES (
        (
            SELECT id
            FROM orders
            WHERE order_number = 'ORD-2026-00003'
        ),
        3,
        'Batería Electrónica Roland',
        'BAT-ROL-003',
        1,
        3200.00,
        3200.00,
        NOW(),
        NOW()
    );
-- Items para Orden 4
INSERT INTO order_items (
        order_id,
        product_id,
        product_name,
        product_sku,
        quantity,
        unit_price,
        subtotal,
        created_at,
        updated_at
    )
VALUES (
        (
            SELECT id
            FROM orders
            WHERE order_number = 'ORD-2026-00004'
        ),
        1,
        'Guitarra Acústica Yamaha',
        'GTA-YMH-001',
        1,
        1500.00,
        1500.00,
        NOW(),
        NOW()
    ),
    (
        (
            SELECT id
            FROM orders
            WHERE order_number = 'ORD-2026-00004'
        ),
        4,
        'Amplificador Fender',
        'AMP-FEN-004',
        1,
        300.00,
        300.00,
        NOW(),
        NOW()
    );
-- Items para Orden 5
INSERT INTO order_items (
        order_id,
        product_id,
        product_name,
        product_sku,
        quantity,
        unit_price,
        subtotal,
        created_at,
        updated_at
    )
VALUES (
        (
            SELECT id
            FROM orders
            WHERE order_number = 'ORD-2026-00005'
        ),
        5,
        'Micrófono Shure SM58',
        'MIC-SHU-005',
        2,
        475.00,
        950.00,
        NOW(),
        NOW()
    );
-- Items para Orden 6
INSERT INTO order_items (
        order_id,
        product_id,
        product_name,
        product_sku,
        quantity,
        unit_price,
        subtotal,
        created_at,
        updated_at
    )
VALUES (
        (
            SELECT id
            FROM orders
            WHERE order_number = 'ORD-2026-00006'
        ),
        3,
        'Batería Electrónica Roland',
        'BAT-ROL-003',
        1,
        3200.00,
        3200.00,
        NOW(),
        NOW()
    ),
    (
        (
            SELECT id
            FROM orders
            WHERE order_number = 'ORD-2026-00006'
        ),
        2,
        'Teclado Casio 61 Teclas',
        'TEC-CAS-002',
        1,
        1300.00,
        1300.00,
        NOW(),
        NOW()
    );
-- Items para Orden 7
INSERT INTO order_items (
        order_id,
        product_id,
        product_name,
        product_sku,
        quantity,
        unit_price,
        subtotal,
        created_at,
        updated_at
    )
VALUES (
        (
            SELECT id
            FROM orders
            WHERE order_number = 'ORD-2026-00007'
        ),
        1,
        'Guitarra Acústica Yamaha',
        'GTA-YMH-001',
        1,
        1500.00,
        1500.00,
        NOW(),
        NOW()
    ),
    (
        (
            SELECT id
            FROM orders
            WHERE order_number = 'ORD-2026-00007'
        ),
        4,
        'Amplificador Fender',
        'AMP-FEN-004',
        2,
        300.00,
        600.00,
        NOW(),
        NOW()
    );
-- Verificar datos insertados
SELECT 'Órdenes creadas:' as Info;
SELECT id,
    order_number,
    status,
    payment_status,
    shipping_status,
    total,
    created_at
FROM orders
ORDER BY created_at DESC;
SELECT 'Items de órdenes:' as Info;
SELECT oi.id,
    o.order_number,
    oi.product_name,
    oi.quantity,
    oi.unit_price,
    oi.subtotal
FROM order_items oi
    JOIN orders o ON oi.order_id = o.id
ORDER BY o.created_at DESC,
    oi.id;
-- Verificar estadísticas
SELECT 'Total' as Estado,
    COUNT(*) as Cantidad
FROM orders
UNION ALL
SELECT status as Estado,
    COUNT(*) as Cantidad
FROM orders
GROUP BY status
ORDER BY Estado;