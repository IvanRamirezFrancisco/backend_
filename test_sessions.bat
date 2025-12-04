@echo off
echo ========================================
echo PRUEBA DEL LIMITE DE 3 SESIONES
echo ========================================
echo.

set EMAIL=tu-email@ejemplo.com
set PASSWORD=tu-password

echo 1. LOGIN DISPOSITIVO 1 (PC-Windows)
curl -X POST http://localhost:8080/api/auth/login ^
  -H "Content-Type: application/json" ^
  -H "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 PC-Windows" ^
  -d "{\"email\":\"%EMAIL%\",\"password\":\"%PASSWORD%\"}" ^
  -o response1.json
echo.
echo Token 1 guardado en response1.json
echo.

timeout /t 2

echo 2. LOGIN DISPOSITIVO 2 (iPhone)
curl -X POST http://localhost:8080/api/auth/login ^
  -H "Content-Type: application/json" ^
  -H "User-Agent: Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X) iPhone-Mobile" ^
  -d "{\"email\":\"%EMAIL%\",\"password\":\"%PASSWORD%\"}" ^
  -o response2.json
echo.
echo Token 2 guardado en response2.json
echo.

timeout /t 2

echo 3. LOGIN DISPOSITIVO 3 (Android)
curl -X POST http://localhost:8080/api/auth/login ^
  -H "Content-Type: application/json" ^
  -H "User-Agent: Mozilla/5.0 (Linux; Android 10; SM-G973F) Android-Mobile" ^
  -d "{\"email\":\"%EMAIL%\",\"password\":\"%PASSWORD%\"}" ^
  -o response3.json
echo.
echo Token 3 guardado en response3.json
echo.

timeout /t 2

echo 4. LOGIN DISPOSITIVO 4 (MacBook) - DEBERIA REVOCAR DISPOSITIVO 1
curl -X POST http://localhost:8080/api/auth/login ^
  -H "Content-Type: application/json" ^
  -H "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) MacBook-Safari" ^
  -d "{\"email\":\"%EMAIL%\",\"password\":\"%PASSWORD%\"}" ^
  -o response4.json
echo.
echo Token 4 guardado en response4.json
echo.

timeout /t 2

echo 5. PROBANDO TOKEN 1 (DEBERIA FALLAR - 401)
echo Extrayendo token 1...
powershell -Command "(Get-Content response1.json | ConvertFrom-Json).data.jwtResponse.accessToken" > token1.txt
set /p TOKEN1=<token1.txt

curl -H "Authorization: Bearer %TOKEN1%" ^
     -H "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 PC-Windows" ^
     http://localhost:8080/api/auth/me ^
     -w "HTTP Status: %%{http_code}\n"
echo.

echo 6. PROBANDO TOKEN 4 (DEBERIA FUNCIONAR - 200)
echo Extrayendo token 4...
powershell -Command "(Get-Content response4.json | ConvertFrom-Json).data.jwtResponse.accessToken" > token4.txt
set /p TOKEN4=<token4.txt

curl -H "Authorization: Bearer %TOKEN4%" ^
     -H "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) MacBook-Safari" ^
     http://localhost:8080/api/auth/me ^
     -w "HTTP Status: %%{http_code}\n"
echo.

echo ========================================
echo PRUEBA COMPLETADA
echo ========================================
echo.
echo RESULTADOS ESPERADOS:
echo - Token 1 debería fallar (401) porque fue revocado
echo - Token 4 debería funcionar (200) porque es el mas nuevo
echo.

pause