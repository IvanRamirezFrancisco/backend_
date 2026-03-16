-- ============================================================
-- V7: Seed de datos iniciales para PostgreSQL
-- Roles, Permisos, Paises, Configuraciones, Categorias, Marcas
-- ============================================================
-- ----------------------------------------------------------------
-- ROLES
-- ----------------------------------------------------------------
INSERT INTO roles (name, description, created_at, updated_at)
SELECT 'ROLE_USER',
    'Usuario estándar del sistema',
    '2025-10-14 22:15:22',
    '2025-10-14 22:15:22'
WHERE NOT EXISTS (
        SELECT 1
        FROM roles
        WHERE name = 'ROLE_USER'
    );
INSERT INTO roles (name, description, created_at, updated_at)
SELECT 'ROLE_ADMIN',
    'Administrador del sistema',
    '2025-10-14 22:15:22',
    '2025-10-14 22:15:22'
WHERE NOT EXISTS (
        SELECT 1
        FROM roles
        WHERE name = 'ROLE_ADMIN'
    );
INSERT INTO roles (name, description, created_at, updated_at)
SELECT 'ROLE_SUPER_ADMIN',
    'Super administrador con acceso completo',
    '2026-02-27 12:55:13',
    '2026-02-27 12:55:13'
WHERE NOT EXISTS (
        SELECT 1
        FROM roles
        WHERE name = 'ROLE_SUPER_ADMIN'
    );
-- ----------------------------------------------------------------
-- PERMISSIONS
-- ----------------------------------------------------------------
INSERT INTO permissions (
        id,
        name,
        description,
        category,
        created_at,
        updated_at
    )
VALUES (
        1,
        'USER_CREATE',
        'Crear Usuarios',
        'USER',
        '2025-11-22 21:17:02',
        NULL
    ),
    (
        2,
        'USER_READ',
        'Ver Usuarios',
        'USER',
        '2025-11-22 21:17:02',
        NULL
    ),
    (
        3,
        'USER_UPDATE',
        'Actualizar Usuarios',
        'USER',
        '2025-11-22 21:17:02',
        NULL
    ),
    (
        4,
        'USER_DELETE',
        'Eliminar Usuarios',
        'USER',
        '2025-11-22 21:17:02',
        NULL
    ),
    (
        5,
        'USER_MANAGE_ROLES',
        'Gestionar Roles de Usuario',
        'USER',
        '2025-11-22 21:17:02',
        NULL
    ),
    (
        6,
        'ROLE_CREATE',
        'Crear Roles',
        'ROLE',
        '2025-11-22 21:17:02',
        NULL
    ),
    (
        7,
        'ROLE_READ',
        'Ver Roles',
        'ROLE',
        '2025-11-22 21:17:02',
        NULL
    ),
    (
        8,
        'ROLE_UPDATE',
        'Actualizar Roles',
        'ROLE',
        '2025-11-22 21:17:02',
        NULL
    ),
    (
        9,
        'ROLE_DELETE',
        'Eliminar Roles',
        'ROLE',
        '2025-11-22 21:17:02',
        NULL
    ),
    (
        10,
        'PERMISSION_READ',
        'Ver Permisos',
        'PERMISSION',
        '2025-11-22 21:17:02',
        NULL
    ),
    (
        11,
        'PERMISSION_ASSIGN',
        'Asignar Permisos',
        'PERMISSION',
        '2025-11-22 21:17:02',
        NULL
    ),
    (
        12,
        'SYSTEM_SETTINGS',
        'Configuración del Sistema',
        'SYSTEM',
        '2025-11-22 21:17:02',
        NULL
    ),
    (
        13,
        'PRODUCT_READ',
        'Ver Productos',
        'PRODUCT',
        '2025-11-22 21:17:02',
        NULL
    ),
    (
        14,
        'PRODUCT_CREATE',
        'Crear Productos',
        'PRODUCT',
        '2025-11-22 21:17:02',
        NULL
    ),
    (
        15,
        'PRODUCT_UPDATE',
        'Actualizar Productos',
        'PRODUCT',
        '2025-11-22 21:17:02',
        NULL
    ),
    (
        16,
        'ORDER_READ',
        'Ver Órdenes',
        'ORDER',
        '2025-11-22 21:17:02',
        NULL
    ),
    (
        17,
        'DASHBOARD_VIEW',
        'Ver Dashboard',
        'ADMIN',
        '2025-11-22 21:17:02',
        NULL
    ),
    (
        18,
        'PRODUCT_DELETE',
        'Eliminar Productos',
        'PRODUCT',
        '2025-11-22 21:17:02',
        NULL
    ) ON CONFLICT (id) DO NOTHING;
SELECT setval(
        'permissions_id_seq',
        (
            SELECT MAX(id)
            FROM permissions
        )
    );
-- ----------------------------------------------------------------
-- ROLE_PERMISSIONS
-- ----------------------------------------------------------------
-- ROLE_USER: solo lectura de productos, órdenes propias, dashboard básico
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id,
    p.id
FROM roles r,
    permissions p
WHERE r.name = 'ROLE_USER'
    AND p.id IN (13, 16, 17) ON CONFLICT DO NOTHING;
-- ROLE_ADMIN: todos los permisos
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id,
    p.id
FROM roles r,
    permissions p
WHERE r.name = 'ROLE_ADMIN'
    AND p.id IN (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18) ON CONFLICT DO NOTHING;
-- ROLE_SUPER_ADMIN: todos los permisos
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id,
    p.id
FROM roles r,
    permissions p
WHERE r.name = 'ROLE_SUPER_ADMIN'
    AND p.id IN (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18) ON CONFLICT DO NOTHING;
-- ----------------------------------------------------------------
-- COUNTRIES
-- ----------------------------------------------------------------
INSERT INTO countries (id, code, name, phone_prefix, active, created_at)
VALUES (1, 'MX', 'México', '+52', true, NOW()),
    (2, 'US', 'Estados Unidos', '+1', true, NOW()),
    (3, 'ES', 'España', '+34', true, NOW()),
    (4, 'AR', 'Argentina', '+54', true, NOW()),
    (5, 'CO', 'Colombia', '+57', true, NOW()),
    (6, 'CL', 'Chile', '+56', true, NOW()),
    (27, 'CA', 'Canadá', '+1', true, NOW()),
    (28, 'PE', 'Perú', '+51', true, NOW()),
    (29, 'VE', 'Venezuela', '+58', true, NOW()),
    (30, 'EC', 'Ecuador', '+593', true, NOW()),
    (31, 'BO', 'Bolivia', '+591', true, NOW()),
    (32, 'PY', 'Paraguay', '+595', true, NOW()),
    (33, 'UY', 'Uruguay', '+598', true, NOW()),
    (34, 'BR', 'Brasil', '+55', true, NOW()),
    (35, 'GT', 'Guatemala', '+502', true, NOW()),
    (36, 'HN', 'Honduras', '+504', true, NOW()),
    (37, 'SV', 'El Salvador', '+503', true, NOW()),
    (38, 'NI', 'Nicaragua', '+505', true, NOW()),
    (39, 'CR', 'Costa Rica', '+506', true, NOW()),
    (40, 'PA', 'Panamá', '+507', true, NOW()),
    (41, 'CU', 'Cuba', '+53', true, NOW()),
    (42, 'DO', 'Rep. Dominicana', '+1', true, NOW()) ON CONFLICT (id) DO NOTHING;
SELECT setval(
        'countries_id_seq',
        (
            SELECT MAX(id)
            FROM countries
        )
    );
-- ----------------------------------------------------------------
-- SECURITY SETTINGS
-- ----------------------------------------------------------------
INSERT INTO security_settings (
        id,
        setting_key,
        setting_value,
        data_type,
        description,
        category,
        is_public,
        min_value,
        max_value
    )
VALUES (
        1,
        'MAX_LOGIN_ATTEMPTS',
        '5',
        'NUMBER',
        'Intentos máximos de login antes de bloqueo',
        'AUTHENTICATION',
        false,
        NULL,
        NULL
    ),
    (
        2,
        'LOCK_TIME_MINUTES',
        '30',
        'NUMBER',
        'Tiempo de bloqueo de cuenta en minutos',
        'AUTHENTICATION',
        false,
        NULL,
        NULL
    ),
    (
        3,
        'SESSION_TIMEOUT_MINUTES',
        '15',
        'NUMBER',
        'Tiempo de inactividad para cierre de sesión',
        'SESSION',
        false,
        NULL,
        NULL
    ),
    (
        4,
        'PASSWORD_MIN_LENGTH',
        '8',
        'NUMBER',
        'Longitud mínima de contraseñas',
        'PASSWORD',
        false,
        NULL,
        NULL
    ),
    (
        5,
        'PASSWORD_REQUIRE_UPPERCASE',
        'true',
        'BOOLEAN',
        'Requerir letras mayúsculas',
        'PASSWORD',
        false,
        NULL,
        NULL
    ),
    (
        6,
        'PASSWORD_REQUIRE_LOWERCASE',
        'true',
        'BOOLEAN',
        'Requerir letras minúsculas',
        'PASSWORD',
        false,
        NULL,
        NULL
    ),
    (
        7,
        'PASSWORD_REQUIRE_NUMBERS',
        'true',
        'BOOLEAN',
        'Requerir números',
        'PASSWORD',
        false,
        NULL,
        NULL
    ),
    (
        8,
        'PASSWORD_REQUIRE_SYMBOLS',
        'true',
        'BOOLEAN',
        'Requerir símbolos especiales',
        'PASSWORD',
        false,
        NULL,
        NULL
    ),
    (
        9,
        'MFA_REQUIRED',
        'false',
        'BOOLEAN',
        'Requerir MFA para todos los usuarios',
        'MFA',
        false,
        NULL,
        NULL
    ),
    (
        10,
        'RATE_LIMIT_LOGIN_ATTEMPTS',
        '5',
        'NUMBER',
        'Intentos de login por minuto',
        'RATE_LIMITING',
        false,
        NULL,
        NULL
    ),
    (
        11,
        'RATE_LIMIT_PASSWORD_RESET',
        '3',
        'NUMBER',
        'Solicitudes de recuperación por hora',
        'RATE_LIMITING',
        false,
        NULL,
        NULL
    ),
    (
        12,
        'STORE_NAME',
        'Casa de Música Castillo',
        'STRING',
        'Nombre de la tienda',
        'BUSINESS',
        false,
        NULL,
        NULL
    ),
    (
        13,
        'ENABLE_PRODUCT_REVIEWS',
        'true',
        'BOOLEAN',
        'Permitir reseñas de productos',
        'BUSINESS',
        false,
        NULL,
        NULL
    ),
    (
        14,
        'MAX_CART_ITEMS',
        '50',
        'NUMBER',
        'Máximo de items en carrito',
        'BUSINESS',
        false,
        NULL,
        NULL
    ) ON CONFLICT (id) DO NOTHING;
SELECT setval(
        'security_settings_id_seq',
        (
            SELECT MAX(id)
            FROM security_settings
        )
    );
-- ----------------------------------------------------------------
-- CATEGORIES
-- ----------------------------------------------------------------
INSERT INTO categories (
        id,
        name,
        description,
        image_url,
        active,
        parent_id,
        created_at,
        updated_at
    )
VALUES (
        1,
        'Guitarras',
        'Guitarras eléctricas, acústicas y bajos',
        '',
        true,
        NULL,
        '2026-02-18 12:24:30',
        '2026-02-18 12:24:30'
    ),
    (
        6,
        'Teclados',
        'Pianos digitales y teclados musicales',
        '',
        true,
        NULL,
        '2026-02-19 01:16:26',
        '2026-02-19 01:16:26'
    ),
    (
        7,
        'Baterías',
        'Baterías acústicas y electrónicas',
        '',
        true,
        NULL,
        '2026-02-19 01:16:35',
        '2026-02-19 01:16:35'
    ),
    (
        8,
        'Vientos',
        'Instrumentos de viento metal y madera',
        '',
        true,
        NULL,
        '2026-02-19 01:16:45',
        '2026-02-19 01:16:45'
    ),
    (
        9,
        'Cuerdas',
        'Violines, cellos y instrumentos de cuerda',
        '',
        true,
        NULL,
        '2026-02-19 01:16:55',
        '2026-02-19 01:16:55'
    ),
    (
        10,
        'Accesorios',
        'Accesorios y complementos musicales',
        '',
        true,
        NULL,
        '2026-02-19 01:17:05',
        '2026-02-19 01:17:05'
    ),
    (
        11,
        'Amplificadores',
        'Amplificadores y equipos de sonido',
        '',
        true,
        NULL,
        '2026-02-19 01:17:15',
        '2026-02-19 01:17:15'
    ),
    (
        12,
        'Estudio',
        'Equipos de grabación y estudio',
        '',
        true,
        NULL,
        '2026-02-19 01:17:25',
        '2026-02-19 01:17:25'
    ) ON CONFLICT (id) DO NOTHING;
SELECT setval(
        'categories_id_seq',
        (
            SELECT MAX(id)
            FROM categories
        )
    );
-- ----------------------------------------------------------------
-- BRANDS
-- ----------------------------------------------------------------
INSERT INTO brands (
        id,
        name,
        description,
        logo_url,
        website_url,
        country_origin,
        active,
        created_at,
        updated_at,
        product_count
    )
VALUES (
        1,
        'Fender',
        'Guitarras y bajos Fender, referente mundial desde 1946',
        'https://upload.wikimedia.org/wikipedia/commons/thumb/4/4a/Fender_logo.svg/320px-Fender_logo.svg.png',
        'https://www.fender.com',
        'Estados Unidos',
        true,
        '2026-02-18 17:32:09',
        '2026-02-22 05:29:58',
        0
    ),
    (
        2,
        'Gibson',
        'Guitarras Gibson, icono del rock desde 1902',
        'https://upload.wikimedia.org/wikipedia/commons/thumb/e/e8/Gibson_Guitar_Logo.svg/320px-Gibson_Guitar_Logo.svg.png',
        'https://www.gibson.com',
        'Estados Unidos',
        true,
        '2026-02-18 17:32:20',
        '2026-02-22 05:29:58',
        0
    ),
    (
        3,
        'Yamaha',
        'Instrumentos musicales Yamaha, calidad y precisión japonesa',
        'https://upload.wikimedia.org/wikipedia/commons/thumb/5/58/Yamaha_logo.svg/320px-Yamaha_logo.svg.png',
        'https://www.yamaha.com',
        'Japón',
        true,
        '2026-02-18 17:32:30',
        '2026-02-22 05:29:58',
        0
    ),
    (
        4,
        'Roland',
        'Instrumentos electrónicos y sintetizadores Roland',
        'https://upload.wikimedia.org/wikipedia/commons/thumb/f/f3/Roland_logo.svg/320px-Roland_logo.svg.png',
        'https://www.roland.com',
        'Japón',
        true,
        '2026-02-18 17:32:40',
        '2026-02-22 05:29:58',
        0
    ),
    (
        5,
        'Pearl',
        'Baterías y percusión Pearl',
        'https://upload.wikimedia.org/wikipedia/commons/thumb/d/d2/Pearl_Drums_logo.svg/200px-Pearl_Drums_logo.svg.png',
        'https://www.pearldrum.com',
        'Japón',
        true,
        '2026-02-18 17:32:50',
        '2026-02-22 05:29:58',
        0
    ),
    (
        6,
        'Martin',
        'Guitarras acústicas C.F. Martin desde 1833',
        'https://upload.wikimedia.org/wikipedia/commons/thumb/5/5e/CF_Martin_%26_Company_logo.svg/200px-CF_Martin_%26_Company_logo.svg.png',
        'https://www.martinguitar.com',
        'Estados Unidos',
        true,
        '2026-02-18 17:33:00',
        '2026-02-22 05:29:58',
        0
    ) ON CONFLICT (id) DO NOTHING;
SELECT setval(
        'brands_id_seq',
        (
            SELECT MAX(id)
            FROM brands
        )
    );
-- ----------------------------------------------------------------
-- PRODUCTS (muestra representativa para pruebas)
-- ----------------------------------------------------------------
INSERT INTO products (
        id,
        name,
        sku,
        description,
        detailed_description,
        price,
        discount_price,
        stock,
        image_url,
        model,
        weight,
        dimensions,
        secondary_images,
        active,
        featured,
        average_rating,
        review_count,
        five_star_count,
        four_star_count,
        three_star_count,
        two_star_count,
        one_star_count,
        views,
        sales_count,
        brand_id,
        category_id,
        created_at,
        updated_at
    )
VALUES (
        1,
        'Guitarra Eléctrica Fender Stratocaster Player',
        'SKU-FEND-STRAT-001',
        'La Fender Player Stratocaster es la guitarra perfecta para músicos en todos los niveles. Con pastillas de alto rendimiento y un perfil de mástil cómodo.',
        '<p>La <strong>Fender Player Stratocaster</strong> combina el diseño clásico con tecnología moderna.</p><ul><li>Pastillas Player Series</li><li>Diapasón de palo de rosa</li><li>Palanca de vibrato sincronizada</li></ul>',
        15999.00,
        13999.00,
        8,
        'https://images.fender.com/content/dam/Fender/cms/media/guitars/stratocaster/player/0144502500/hero.png',
        'Player Stratocaster',
        3.8,
        '39.4 x 13 x 4.5 cm',
        '[]',
        true,
        true,
        4.50,
        12,
        8,
        3,
        1,
        0,
        0,
        250,
        15,
        1,
        1,
        '2026-02-18 16:22:34',
        '2026-02-22 05:29:58'
    ),
    (
        2,
        'Guitarra Acústica Yamaha FG800',
        'SKU-YAMA-FG800-002',
        'La Yamaha FG800 es una guitarra acústica de cuerpo dreadnought con tapa de abeto sólido, perfecta para principiantes y músicos intermedios.',
        '<p>La <strong>Yamaha FG800</strong> ofrece un sonido brillante y proyección excepcional.</p><ul><li>Tapa de abeto sólido</li><li>Fondo y aros de nato</li><li>Perfil de mástil nato</li></ul>',
        4999.00,
        NULL,
        15,
        'https://usa.yamaha.com/files/2017/07/FG800_BL_hero.jpg',
        'FG800',
        2.1,
        '105 x 42 x 13 cm',
        '[]',
        true,
        false,
        4.30,
        8,
        5,
        2,
        1,
        0,
        0,
        180,
        10,
        3,
        1,
        '2026-02-18 17:00:00',
        '2026-02-22 05:30:00'
    ),
    (
        3,
        'Piano Digital Roland FP-30X',
        'SKU-ROLA-FP30X-003',
        'El Roland FP-30X es un piano digital portátil con 88 teclas de acción ponderada y sonido SuperNATURAL.',
        '<p>El <strong>Roland FP-30X</strong> es ideal para estudiantes y pianistas que buscan calidad profesional.</p><ul><li>88 teclas con acción PHA-4 Standard</li><li>Sonido SuperNATURAL Piano</li><li>Bluetooth MIDI/Audio</li></ul>',
        18500.00,
        16000.00,
        5,
        'https://www.roland.com/us/categories/pianos/digital-pianos/products/fp-30x/images/fp-30x_gal_1.jpg',
        'FP-30X',
        14.0,
        '130 x 28.4 x 15.1 cm',
        '[]',
        true,
        true,
        4.70,
        20,
        15,
        4,
        1,
        0,
        0,
        320,
        22,
        4,
        6,
        '2026-02-19 10:00:00',
        '2026-02-22 05:30:00'
    ),
    (
        4,
        'Batería Acústica Pearl Export EXX725S',
        'SKU-PEAR-EXX-004',
        'El set de batería Pearl Export EXX725S es ideal para bateristas en todos los niveles, con cuerpos de 6 capas de poplar/basswood.',
        '<p>La <strong>Pearl Export EXX725S</strong> es la batería más vendida de Pearl.</p><ul><li>5 piezas: bombo 22", tarola 14", 3 toms</li><li>Hardware incluido</li><li>Platillos no incluidos</li></ul>',
        12500.00,
        NULL,
        3,
        'https://www.pearldrum.com/media/catalog/product/cache/1/image/800x800/9df78eab33525d08d6e5fb8d27136e95/e/x/exx725sc_bm_1.jpg',
        'EXX725S',
        45.0,
        'Variable',
        '[]',
        true,
        false,
        4.20,
        5,
        3,
        1,
        1,
        0,
        0,
        100,
        4,
        5,
        7,
        '2026-02-19 11:00:00',
        '2026-02-22 05:30:00'
    ),
    (
        5,
        'Bajo Eléctrico Fender Player Jazz Bass',
        'SKU-FEND-JB-005',
        'El Fender Player Jazz Bass ofrece el inconfundible sonido Fender con dos pastillas de bobina simple Player Series.',
        '<p>El <strong>Fender Player Jazz Bass</strong> es versátil y potente.</p><ul><li>2 pastillas Player Series Single-Coil</li><li>Cuerpo de aliso</li><li>Mástil de arce</li></ul>',
        13999.00,
        11999.00,
        6,
        'https://images.fender.com/content/dam/Fender/cms/media/basses/jazz-bass/player/0149902500/hero.png',
        'Player Jazz Bass',
        4.3,
        '117 x 32 x 5.5 cm',
        '[]',
        true,
        false,
        4.40,
        7,
        4,
        2,
        1,
        0,
        0,
        145,
        8,
        1,
        1,
        '2026-02-19 12:00:00',
        '2026-02-22 05:30:00'
    ),
    (
        6,
        'Teclado Yamaha PSR-E373',
        'SKU-YAMA-PSR-006',
        'El Yamaha PSR-E373 es un teclado portátil de 61 teclas con 622 voces de instrumentos y 205 estilos de acompañamiento.',
        '<p>El <strong>Yamaha PSR-E373</strong> es perfecto para aprender a tocar teclado.</p><ul><li>61 teclas sensitivas</li><li>622 Voces</li><li>205 Estilos</li><li>Lección de piano integrada</li></ul>',
        5500.00,
        4800.00,
        12,
        'https://usa.yamaha.com/files/2020/04/psr-e373_hero.jpg',
        'PSR-E373',
        3.8,
        '93.5 x 33.3 x 12.5 cm',
        '[]',
        true,
        true,
        4.10,
        15,
        8,
        5,
        2,
        0,
        0,
        280,
        18,
        3,
        6,
        '2026-02-19 13:00:00',
        '2026-02-22 05:30:00'
    ),
    (
        7,
        'Amplificador Fender Frontman 10G',
        'SKU-FEND-FRONT-007',
        'El Fender Frontman 10G es un amplificador de guitarra de práctica de 10W, ideal para principiantes.',
        '<p>El <strong>Fender Frontman 10G</strong> es compacto y fácil de usar.</p><ul><li>10 Watts de potencia</li><li>Canal limpio y canal de overdrive</li><li>Entrada auxiliar de 1/8"</li></ul>',
        2200.00,
        NULL,
        20,
        'https://images.fender.com/content/dam/Fender/cms/media/amplifiers/frontman/023-1000-000/hero.png',
        'Frontman 10G',
        4.1,
        '30.5 x 16.5 x 28 cm',
        '[]',
        true,
        false,
        3.90,
        10,
        4,
        4,
        2,
        0,
        0,
        95,
        12,
        1,
        11,
        '2026-02-19 14:00:00',
        '2026-02-22 05:30:00'
    ) ON CONFLICT (id) DO NOTHING;
SELECT setval(
        'products_id_seq',
        (
            SELECT MAX(id)
            FROM products
        )
    );
-- Update brand product_count
UPDATE brands
SET product_count = (
        SELECT COUNT(*)
        FROM products
        WHERE brand_id = brands.id
            AND active = true
    );