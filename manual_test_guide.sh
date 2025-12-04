#!/bin/bash
# PRUEBA MANUAL DEL LÍMITE DE 3 SESIONES

echo "=========================================="
echo "PRUEBA MANUAL - LÍMITE DE 3 SESIONES"
echo "=========================================="
echo ""
echo "INSTRUCCIONES:"
echo "1. Cambia las credenciales abajo por las tuyas"
echo "2. Ejecuta cada comando uno por uno"
echo "3. Observa los logs del servidor"
echo ""

EMAIL="tu-email@ejemplo.com"
PASSWORD="tu-password"

echo "=== DISPOSITIVO 1: PC Windows ==="
echo "curl -X POST http://localhost:8080/api/auth/login \\"
echo "  -H 'Content-Type: application/json' \\"
echo "  -H 'User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) PC-Windows' \\"
echo "  -d '{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}'"
echo ""

echo "=== DISPOSITIVO 2: iPhone ==="
echo "curl -X POST http://localhost:8080/api/auth/login \\"
echo "  -H 'Content-Type: application/json' \\"
echo "  -H 'User-Agent: Mozilla/5.0 (iPhone; CPU iPhone OS 14_0) iPhone-Mobile' \\"
echo "  -d '{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}'"
echo ""

echo "=== DISPOSITIVO 3: Android ==="
echo "curl -X POST http://localhost:8080/api/auth/login \\"
echo "  -H 'Content-Type: application/json' \\"
echo "  -H 'User-Agent: Mozilla/5.0 (Linux; Android 10) Android-Mobile' \\"
echo "  -d '{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}'"
echo ""

echo "=== DISPOSITIVO 4: MacBook (DEBERÍA REVOCAR DISPOSITIVO 1) ==="
echo "curl -X POST http://localhost:8080/api/auth/login \\"
echo "  -H 'Content-Type: application/json' \\"
echo "  -H 'User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) MacBook' \\"
echo "  -d '{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}'"
echo ""

echo "=== PROBAR TOKEN DEL DISPOSITIVO 1 (DEBERÍA FALLAR) ==="
echo "curl -H 'Authorization: Bearer [TOKEN_DISPOSITIVO_1]' \\"
echo "  -H 'User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) PC-Windows' \\"
echo "  http://localhost:8080/api/auth/me"
echo ""

echo "NOTA: Observa los logs del servidor para ver:"
echo "🔍 Usuario X tiene Y sesiones activas"
echo "🔒 Token más antiguo REVOCADO: ..."
echo "✅ Nueva sesión creada: ..."