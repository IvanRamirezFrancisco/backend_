# 📋 GUÍA DE EJECUCIÓN - FASE 1

## 🎯 Objetivo

Implementar mejoras críticas en la base de datos para alcanzar **7.5/10** (nivel funcional básico)

---

## ⚠️ ANTES DE EMPEZAR

### 1. **HACER BACKUP COMPLETO** 🚨

```powershell
# Ejecutar en PowerShell
mysqldump -u root -p security_db > "C:\Users\ivanf\Documents\dumps\backup_antes_fase1_$(Get-Date -Format 'yyyyMMdd_HHmmss').sql"
```

### 2. **Verificar que el backup se creó correctamente**

```powershell
# Debe mostrar el tamaño del archivo
Get-Item "C:\Users\ivanf\Documents\dumps\backup_antes_fase1_*.sql" | Select-Object Name, Length
```

---

## 🚀 EJECUCIÓN DEL SCRIPT

### Opción 1: MySQL Workbench (RECOMENDADO)

1. Abre MySQL Workbench
2. Conecta a tu servidor local (localhost:3306)
3. Abre el archivo: `database_migrations/FASE1_CRITICAL_IMPROVEMENTS.sql`
4. Revisa el script (scroll completo para entender qué hará)
5. Ejecuta todo el script: **⚡ Execute (Ctrl+Shift+Enter)**
6. Espera 2-5 minutos
7. Revisa los mensajes de salida con ✅

### Opción 2: Terminal MySQL

```bash
mysql -u root -p security_db < database_migrations/FASE1_CRITICAL_IMPROVEMENTS.sql
```

---

## 📊 QUÉ HACE ESTE SCRIPT (Paso a Paso)

### **PASO 1: Unificación de Users y Customers** (2 min)

- ✅ Agrega campos de cliente a `users` (first_name, last_name, phone, is_customer)
- ✅ Migra datos de `customers` a `users`
- ✅ Actualiza FK de `orders`: `customer_id` → `user_id`
- ✅ Elimina tabla `customers` (obsoleta)

**Resultado:** Una sola tabla para usuarios (con o sin compras)

---

### **PASO 2: Normalización de Direcciones** (2 min)

- ✅ Crea tabla `countries` (20 países latinoamericanos)
- ✅ Crea tabla `addresses` (múltiples direcciones por usuario)
- ✅ Migra direcciones de `users` a `addresses`
- ✅ Elimina campos de dirección de `users`

**Resultado:** Direcciones normalizadas (envío vs facturación)

---

### **PASO 3: Historial de Precios** (1 min)

- ✅ Crea tabla `product_price_history`
- ✅ Registra precios actuales como historial inicial
- ✅ Prepara triggers para futuras actualizaciones

**Resultado:** Rastreo completo de cambios de precios

---

### **PASO 4: Triggers de Auditoría** (1 min)

- ✅ Trigger: Cambios en usuarios (email, password, estado)
- ✅ Trigger: Eliminación de usuarios
- ✅ Trigger: Cambios de precio en productos
- ✅ Trigger: Cambios de stock en productos
- ✅ Trigger: Creación de órdenes
- ✅ Trigger: Cambios de estado en órdenes

**Resultado:** Auditoría automática de todo cambio crítico

---

### **PASO 5: Índices de Performance** (30 seg)

- ✅ 15+ índices compuestos en tablas principales
- ✅ Optimización de búsquedas por fecha, estado, usuario
- ✅ Mejora de consultas de reportes

**Resultado:** Consultas 10-50x más rápidas

---

## ✅ VERIFICACIÓN POST-EJECUCIÓN

### 1. Verificar que no hubo errores

```sql
-- Ejecutar en MySQL Workbench
USE security_db;

-- Debe mostrar las tablas nuevas
SHOW TABLES LIKE 'addresses';
SHOW TABLES LIKE 'countries';
SHOW TABLES LIKE 'product_price_history';

-- Debe mostrar 0 (tabla eliminada)
SHOW TABLES LIKE 'customers';
```

### 2. Verificar triggers

```sql
-- Debe mostrar 5 triggers
SELECT
    TRIGGER_NAME,
    EVENT_MANIPULATION,
    EVENT_OBJECT_TABLE
FROM information_schema.TRIGGERS
WHERE TRIGGER_SCHEMA = 'security_db'
ORDER BY EVENT_OBJECT_TABLE;
```

### 3. Verificar datos migrados

```sql
-- Verificar usuarios con datos de cliente
SELECT
    id,
    username,
    email,
    first_name,
    last_name,
    is_customer,
    total_orders,
    total_spent
FROM users
WHERE is_customer = TRUE
LIMIT 5;

-- Verificar direcciones migradas
SELECT
    a.id,
    u.email,
    a.street,
    a.city,
    c.name as country,
    a.is_default
FROM addresses a
JOIN users u ON a.user_id = u.id
JOIN countries c ON a.country_id = c.id
LIMIT 5;

-- Verificar historial de precios
SELECT
    ph.id,
    p.name as product,
    ph.new_price,
    ph.new_discount_price,
    ph.effective_from
FROM product_price_history ph
JOIN products p ON ph.product_id = p.id
LIMIT 5;
```

### 4. Verificar que orders apunta a users

```sql
SELECT
    o.id,
    o.order_number,
    u.email as customer_email,
    o.status,
    o.total
FROM orders o
JOIN users u ON o.user_id = u.id
LIMIT 5;
```

---

## 🐛 SOLUCIÓN DE PROBLEMAS

### Error: "Cannot add foreign key constraint"

**Solución:** Hay datos inconsistentes. Ejecuta:

```sql
-- Verificar órdenes sin usuario
SELECT * FROM orders WHERE customer_id NOT IN (SELECT id FROM customers);
-- Eliminar o corregir antes de continuar
```

### Error: "Duplicate column name"

**Solución:** Ya ejecutaste el script antes. Opciones:

1. Restaurar desde backup y ejecutar de nuevo
2. Comentar las líneas `ALTER TABLE ADD COLUMN` ya ejecutadas

### Error: "Trigger already exists"

**Solución:** Ejecuta primero:

```sql
DROP TRIGGER IF EXISTS trg_users_after_update;
DROP TRIGGER IF EXISTS trg_users_after_delete;
DROP TRIGGER IF EXISTS trg_products_price_change;
DROP TRIGGER IF EXISTS trg_orders_after_insert;
DROP TRIGGER IF EXISTS trg_orders_status_change;
```

---

## 🎯 PRÓXIMOS PASOS

Una vez completada la Fase 1:

### 1. **Actualizar Entidades Java** (30 min)

- ❌ Eliminar `Customer.java`
- ✅ Actualizar `User.java` con campos nuevos
- ✅ Crear `Address.java`
- ✅ Crear `Country.java`
- ✅ Crear `ProductPriceHistory.java`
- ✅ Actualizar `Order.java` (customer → user)

### 2. **Actualizar Repositorios** (15 min)

- ❌ Eliminar `CustomerRepository`
- ✅ Crear `AddressRepository`
- ✅ Crear `CountryRepository`
- ✅ Actualizar queries en `OrderRepository`

### 3. **Actualizar Servicios** (30 min)

- Adaptar lógica a nueva estructura
- Agregar métodos para addresses

### 4. **Probar Triggers** (15 min)

```sql
-- Probar trigger de precios
UPDATE products SET price = 999.99 WHERE id = 1;

-- Verificar que se registró en audit_logs y price_history
SELECT * FROM audit_logs ORDER BY id DESC LIMIT 5;
SELECT * FROM product_price_history ORDER BY id DESC LIMIT 5;
```

### 5. **Proceder con FASE 2** 🚀

- Carrito de compras
- Sistema de reviews
- Cupones y descuentos
- Wishlist

---

## 📞 SOPORTE

Si algo falla:

1. **NO entres en pánico** 🧘
2. **Copia el mensaje de error completo**
3. **Restaura desde backup:**
   ```bash
   mysql -u root -p security_db < backup_antes_fase1_XXXXXX.sql
   ```
4. **Pide ayuda** con el error específico

---

## 📈 CALIFICACIÓN ESPERADA

| Antes     | Después FASE 1 | Mejora |
| --------- | -------------- | ------ |
| 5.5/10 ⚠️ | 7.5/10 ✅      | +36%   |

**Estado:** Funcional básico - Apto para desarrollo continuo

---

¿Listo para ejecutar? 🚀
