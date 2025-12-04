@echo off
echo ========================================
echo PRUEBA DEL FLUJO DE REGISTRO Y VERIFICACION
echo ========================================
echo.

echo 1. REGISTRO DE NUEVO USUARIO
curl -X POST http://localhost:8080/api/auth/register ^
  -H "Content-Type: application/json" ^
  -d "{\"firstName\":\"Test\",\"lastName\":\"User\",\"email\":\"test@ejemplo.com\",\"username\":\"testuser\",\"password\":\"Password123!\",\"phone\":\"+1234567890\"}"
echo.
echo.

echo 2. INTENTAR LOGIN SIN VERIFICAR (DEBERIA FALLAR)
curl -X POST http://localhost:8080/api/auth/login ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"test@ejemplo.com\",\"password\":\"Password123!\"}"
echo.
echo.

echo ========================================
echo RESULTADOS ESPERADOS:
echo ========================================
echo 1. Registro exitoso + email de verificacion enviado
echo 2. Login fallido con mensaje: "Cuenta no verificada. Por favor verifica tu email antes de iniciar sesion."
echo.
echo NOTA: Revisa tu email para obtener el enlace de verificacion.
echo Despues de verificar, el login deberia funcionar normalmente.
echo.

pause