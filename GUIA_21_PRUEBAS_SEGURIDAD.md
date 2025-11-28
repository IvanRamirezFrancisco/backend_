# Guía de 21 Pruebas de Seguridad Completas

## Sistema de Autenticación y Seguridad - Casa Música

### FASE 1: CONFIGURACIÓN Y PREPARACIÓN

#### Prueba 1: Verificación de Componentes de Seguridad

**Objetivo:** Validar que todos los componentes de seguridad estén activos
**Ejecutar:**

```bash
# Verificar que el servidor esté ejecutándose
curl -X GET http://localhost:8080/actuator/health

# Verificar endpoint de seguridad
curl -X GET http://localhost:8080/api/auth/test/public
```

**Resultado esperado:**

- Server responde con status 200
- Endpoint público accesible sin autenticación

#### Prueba 2: Validación de Configuración de Cabeceras de Seguridad

**Objetivo:** Verificar que las cabeceras de seguridad HTTP estén configuradas
**Ejecutar:**

```bash
# Verificar cabeceras de seguridad
curl -I http://localhost:8080/api/auth/test/public
```

**Resultado esperado:**

- X-Frame-Options: DENY
- X-Content-Type-Options: nosniff
- Strict-Transport-Security presente
- Content-Security-Policy configurado

### FASE 2: PRUEBAS DE AUTENTICACIÓN BÁSICA

#### Prueba 3: Registro de Usuario con Validación

**Objetivo:** Verificar el registro seguro de usuarios
**Ejecutar:**

```bash
curl -X POST http://localhost:8080/api/auth/register \
-H "Content-Type: application/json" \
-d '{
    "email": "test@example.com",
    "password": "SecurePass123!",
    "firstName": "Test",
    "lastName": "User"
}'
```

**Resultado esperado:**

- Status 200/201
- Usuario creado con hash seguro de contraseña
- Email de verificación enviado

#### Prueba 4: Autenticación con Credenciales Válidas

**Objetivo:** Verificar el proceso de login estándar
**Ejecutar:**

```bash
curl -X POST http://localhost:8080/api/auth/login \
-H "Content-Type: application/json" \
-d '{
    "email": "test@example.com",
    "password": "SecurePass123!"
}'
```

**Resultado esperado:**

- Status 200
- JWT token válido retornado
- Refresh token generado
- Información del usuario en la respuesta

### FASE 3: PRUEBAS DE SEGURIDAD DE CONTRASEÑAS

#### Prueba 5: Políticas de Contraseñas Débiles

**Objetivo:** Verificar rechazo de contraseñas inseguras
**Ejecutar:**

```bash
# Contraseña débil
curl -X POST http://localhost:8080/api/auth/register \
-H "Content-Type: application/json" \
-d '{
    "email": "weak@example.com",
    "password": "123",
    "firstName": "Weak",
    "lastName": "Password"
}'
```

**Resultado esperado:**

- Status 400 (Bad Request)
- Mensaje de error explicando requisitos de contraseña

#### Prueba 6: Proceso de Recuperación de Contraseña

**Objetivo:** Verificar el flujo seguro de reset de contraseña
**Ejecutar:**

```bash
# Solicitar reset de contraseña
curl -X POST http://localhost:8080/api/auth/forgot-password \
-H "Content-Type: application/json" \
-d '{
    "email": "test@example.com"
}'
```

**Resultado esperado:**

- Status 200
- Email de reset enviado
- Token de reset generado con expiración

### FASE 4: PRUEBAS DE ATAQUES DE FUERZA BRUTA

#### Prueba 7: Protección contra Ataques de Fuerza Bruta

**Objetivo:** Verificar limitación de intentos de login
**Ejecutar:**

```bash
# Realizar 6 intentos fallidos consecutivos
for i in {1..6}; do
    curl -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{
        "email": "test@example.com",
        "password": "wrongpassword'$i'"
    }'
    echo "Intento $i completado"
    sleep 1
done
```

**Resultado esperado:**

- Primeros 5 intentos: Status 401
- Sexto intento: Status 429 (Too Many Requests)
- Cuenta bloqueada temporalmente

#### Prueba 8: Recuperación tras Bloqueo por Fuerza Bruta

**Objetivo:** Verificar que el bloqueo sea temporal
**Ejecutar:**

```bash
# Esperar el tiempo de bloqueo (configurado en 15 minutos por defecto)
sleep 900  # Esperar 15 minutos en producción

# Intentar login válido después del bloqueo
curl -X POST http://localhost:8080/api/auth/login \
-H "Content-Type: application/json" \
-d '{
    "email": "test@example.com",
    "password": "SecurePass123!"
}'
```

**Resultado esperado:**

- Status 200
- Login exitoso después del período de bloqueo

### FASE 5: PRUEBAS DE VALIDACIÓN DE JWT

#### Prueba 9: Validación de Token JWT

**Objetivo:** Verificar que los JWT tokens sean válidos y seguros
**Ejecutar:**

```bash
# Primero obtener un token válido
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
-H "Content-Type: application/json" \
-d '{"email":"test@example.com","password":"SecurePass123!"}' \
| jq -r '.token')

# Usar el token para acceder a endpoint protegido
curl -X GET http://localhost:8080/api/auth/test/user \
-H "Authorization: Bearer $TOKEN"
```

**Resultado esperado:**

- Status 200
- Acceso exitoso al endpoint protegido
- Información de usuario autorizada retornada

#### Prueba 10: Rechazo de Tokens Inválidos

**Objetivo:** Verificar que tokens malformados sean rechazados
**Ejecutar:**

```bash
# Token inválido/malformado
curl -X GET http://localhost:8080/api/auth/test/user \
-H "Authorization: Bearer invalid.token.here"
```

**Resultado esperado:**

- Status 401 (Unauthorized)
- Mensaje de error sobre token inválido

#### Prueba 11: Expiración de Tokens

**Objetivo:** Verificar que los tokens expiren correctamente
**Ejecutar:**

```bash
# Generar token con expiración corta (necesita configuración específica)
# O esperar hasta que un token expire (15 minutos por defecto)
sleep 900

# Intentar usar token expirado
curl -X GET http://localhost:8080/api/auth/test/user \
-H "Authorization: Bearer $EXPIRED_TOKEN"
```

**Resultado esperado:**

- Status 401 (Unauthorized)
- Mensaje de error sobre token expirado

### FASE 6: PRUEBAS DE INYECCIÓN Y SANITIZACIÓN

#### Prueba 12: Prevención de Inyección SQL

**Objetivo:** Verificar protección contra inyección SQL
**Ejecutar:**

```bash
# Intento de inyección SQL en el login
curl -X POST http://localhost:8080/api/auth/login \
-H "Content-Type: application/json" \
-d '{
    "email": "admin@test.com'\'' OR '\''1'\''='\''1",
    "password": "password"
}'
```

**Resultado esperado:**

- Status 401 (no autenticación exitosa)
- Entrada sanitizada, sin ejecución de SQL malicioso

#### Prueba 13: Protección XSS (Cross-Site Scripting)

**Objetivo:** Verificar sanitización de entrada XSS
**Ejecutar:**

```bash
# Intentar registro con script malicioso en nombre
curl -X POST http://localhost:8080/api/auth/register \
-H "Content-Type: application/json" \
-d '{
    "email": "xss@test.com",
    "password": "SecurePass123!",
    "firstName": "<script>alert(\"XSS\")</script>",
    "lastName": "Test"
}'
```

**Resultado esperado:**

- Input sanitizado (script removido/escapado)
- No ejecución de código JavaScript

### FASE 7: PRUEBAS DE AUTORIZACIÓN Y RBAC

#### Prueba 14: Control de Acceso Basado en Roles (RBAC)

**Objetivo:** Verificar que los roles limiten el acceso apropiadamente
**Ejecutar:**

```bash
# Usuario regular intentando acceder a endpoint de admin
TOKEN_USER=$(curl -s -X POST http://localhost:8080/api/auth/login \
-H "Content-Type: application/json" \
-d '{"email":"user@test.com","password":"SecurePass123!"}' \
| jq -r '.token')

curl -X GET http://localhost:8080/api/auth/test/admin \
-H "Authorization: Bearer $TOKEN_USER"
```

**Resultado esperado:**

- Status 403 (Forbidden)
- Acceso denegado por falta de privilegios

#### Prueba 15: Acceso de Usuario Admin

**Objetivo:** Verificar que usuarios admin tengan acceso completo
**Ejecutar:**

```bash
# Login como admin y acceso a endpoint de admin
TOKEN_ADMIN=$(curl -s -X POST http://localhost:8080/api/auth/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@test.com","password":"AdminPass123!"}' \
| jq -r '.token')

curl -X GET http://localhost:8080/api/auth/test/admin \
-H "Authorization: Bearer $TOKEN_ADMIN"
```

**Resultado esperado:**

- Status 200
- Acceso exitoso a recurso de administrador

### FASE 8: PRUEBAS DE AUTENTICACIÓN DE DOS FACTORES

#### Prueba 16: Configuración de 2FA

**Objetivo:** Verificar configuración de autenticación de dos factores
**Ejecutar:**

```bash
# Habilitar 2FA para un usuario
curl -X POST http://localhost:8080/api/auth/2fa/setup \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json"
```

**Resultado esperado:**

- Status 200
- QR code o código secreto generado para configurar 2FA

#### Prueba 17: Login con 2FA Habilitado

**Objetivo:** Verificar proceso de login con 2FA
**Ejecutar:**

```bash
# Login inicial (primer factor)
curl -X POST http://localhost:8080/api/auth/login \
-H "Content-Type: application/json" \
-d '{
    "email": "2fa-user@test.com",
    "password": "SecurePass123!"
}'

# Verificación del segundo factor
curl -X POST http://localhost:8080/api/auth/2fa/verify \
-H "Content-Type: application/json" \
-d '{
    "email": "2fa-user@test.com",
    "code": "123456"
}'
```

**Resultado esperado:**

- Primer paso: Status 200 con indicación de requerir 2FA
- Segundo paso: Status 200 con token completo tras código válido

### FASE 9: PRUEBAS DE SEGURIDAD DE SESIÓN

#### Prueba 18: Invalidación de Sesión/Token

**Objetivo:** Verificar que el logout invalide los tokens
**Ejecutar:**

```bash
# Logout
curl -X POST http://localhost:8080/api/auth/logout \
-H "Authorization: Bearer $TOKEN"

# Intentar usar token después del logout
curl -X GET http://localhost:8080/api/auth/test/user \
-H "Authorization: Bearer $TOKEN"
```

**Resultado esperado:**

- Logout: Status 200
- Uso posterior del token: Status 401 (Unauthorized)

#### Prueba 19: Renovación Segura de Tokens

**Objetivo:** Verificar renovación de access tokens usando refresh token
**Ejecutar:**

```bash
# Usar refresh token para obtener nuevo access token
curl -X POST http://localhost:8080/api/auth/refresh \
-H "Content-Type: application/json" \
-d '{
    "refreshToken": "your-refresh-token-here"
}'
```

**Resultado esperado:**

- Status 200
- Nuevo access token generado
- Refresh token puede ser rotado

### FASE 10: PRUEBAS DE MONITOREO Y AUDITORÍA

#### Prueba 20: Registro de Eventos de Seguridad

**Objetivo:** Verificar que eventos de seguridad sean registrados
**Ejecutar:**

```bash
# Realizar varias acciones que deberían generar logs
curl -X POST http://localhost:8080/api/auth/login \
-H "Content-Type: application/json" \
-d '{"email":"test@test.com","password":"wrongpassword"}'

# Verificar logs (requiere acceso a logs del servidor)
tail -f application.log | grep "SECURITY_AUDIT"
```

**Resultado esperado:**

- Eventos de seguridad registrados en logs
- Información de IP, usuario, y tipo de evento incluida

#### Prueba 21: Detección de Patrones Sospechosos

**Objetivo:** Verificar que el sistema detecte actividad sospechosa
**Ejecutar:**

```bash
# Generar múltiples intentos fallidos desde diferentes "ubicaciones"
for i in {1..10}; do
    curl -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -H "User-Agent: SuspiciousBot$i" \
    -d '{
        "email": "target@test.com",
        "password": "wrongpassword'$i'"
    }'
    sleep 1
done
```

**Resultado esperado:**

- Sistema detecta patrón sospechoso
- Alertas de seguridad generadas
- IP/usuario marcado como sospechoso

### FASE 11: PRUEBAS ADICIONALES DE VERIFICACIÓN

#### Verificaciones de Estado del Sistema:

```bash
# Verificar estadísticas de seguridad
curl -X GET http://localhost:8080/api/auth/security/statistics \
-H "Authorization: Bearer $ADMIN_TOKEN"

# Verificar estado de servicios de seguridad
curl -X GET http://localhost:8080/actuator/metrics/security
```

#### Configuración de Pruebas:

1. **Environment de pruebas:** Usar base de datos de testing
2. **Configuraciones:** Tiempos de expiración reducidos para pruebas más rápidas
3. **Logs:** Nivel DEBUG para ver detalles de seguridad
4. **Limpieza:** Limpiar datos de prueba después de cada suite

#### Interpretación de Resultados:

- ✅ **PASS:** Comportamiento esperado obtenido
- ❌ **FAIL:** Resultado inesperado, requiere revisión
- ⚠️ **WARNING:** Funciona pero con advertencias

### Notas Importantes:

- Ejecutar en entorno de desarrollo/testing únicamente
- Ajustar tiempos de espera según configuración
- Monitorear logs durante las pruebas
- Validar que todas las pruebas pasen antes de producción
- Documentar cualquier excepción o comportamiento especial

### Automatización:

Estas pruebas pueden automatizarse usando frameworks como:

- **Newman** (para colecciones Postman)
- **Jest** con axios para JavaScript
- **JUnit** para integración con el backend Java
- **GitHub Actions** o **Jenkins** para CI/CD

¡El sistema ha pasado todas las implementaciones de seguridad requeridas y está listo para las pruebas de validación!
