# 🚀 GUÍA DE DESPLIEGUE EN RENDER

## Casa de Música Castillo - Backend Spring Boot con MySQL Aiven

---

## 📋 PRE-REQUISITOS

- [x] Cuenta en Render.com
- [x] Cuenta en GitHub
- [x] Base de datos MySQL en Aiven configurada
- [x] Frontend desplegado en Netlify

---

## 🔧 PASO 1: VERIFICAR ARCHIVOS

Asegúrate de tener estos archivos en tu proyecto:

```
✅ Dockerfile (raíz del proyecto)
✅ .dockerignore (raíz del proyecto)
✅ src/main/resources/application-production.yml
✅ pom.xml (con driver MySQL)
```

---

## 🌐 PASO 2: CREAR WEB SERVICE EN RENDER

1. Ve a: https://dashboard.render.com/
2. Click en "New +" → "Web Service"
3. Conecta tu repositorio de GitHub
4. Configuración:
   - **Name**: `casa-musica-backend`
   - **Region**: `Oregon (US West)`
   - **Branch**: `main`
   - **Runtime**: `Docker`
   - **Instance Type**: `Free` (o `Starter`)

---

## 🔐 PASO 3: CONFIGURAR VARIABLES DE ENTORNO

En la pestaña **Environment**, agrega estas variables:

### 🗄️ Base de Datos (3 variables)

```
SPRING_DATASOURCE_URL = jdbc:mysql://[TU-HOST]:21669/defaultdb?ssl-mode=REQUIRED&useSSL=true&requireSSL=true
SPRING_DATASOURCE_USERNAME = [TU-USUARIO]
SPRING_DATASOURCE_PASSWORD = [TU-PASSWORD]
```

**⚠️ IMPORTANTE:**

- Reemplaza `[TU-HOST]` con el host de Aiven
- Usa el password que te da Aiven (click en "REVEAL_PASSWORD")

### 🔐 Seguridad (1 variable)

```
APP_SECURITY_JWT_SECRET = [GENERA-UN-SECRET-LARGO-Y-ALEATORIO]
```

Genera un secret seguro:

```bash
openssl rand -base64 64 | tr -d '\n'
```

### 📧 Email (4 variables)

```
SPRING_MAIL_HOST = smtp-relay.brevo.com
SPRING_MAIL_PORT = 587
SPRING_MAIL_USERNAME = [TU-EMAIL-BREVO]
SPRING_MAIL_PASSWORD = [TU-PASSWORD-BREVO]
```

### 🌐 Frontend y CORS (2 variables)

```
APP_BASE_URL = https://tu-frontend.netlify.app
CORS_ORIGINS = https://tu-frontend.netlify.app
```

**⚠️ CRÍTICO:** Reemplaza con tu URL real de Netlify

### 📨 APIs Externas (4 variables - OPCIONALES)

```
APP_EMAIL_BREVO_API_KEY = [TU-API-KEY]
APP_NOTIFICATION_SMS_PROJECT_ID = [TU-PROJECT-ID]
APP_NOTIFICATION_SMS_ACCESS_KEY_ID = [TU-ACCESS-KEY]
APP_NOTIFICATION_SMS_SECRET_KEY = [TU-SECRET-KEY]
```

**TOTAL: 14 variables mínimas**

---

## 🚀 PASO 4: SUBIR CÓDIGO A GITHUB

```bash
cd "c:\Users\ivanf\Documents\7mo\Proyecto del login original y funcional\segundo proyecto"

# Agregar archivos
git add Dockerfile .dockerignore src/main/resources/application-production.yml

# Commit
git commit -m "Add production configuration for Render deployment"

# Push
git push origin main
```

---

## 🔄 PASO 5: DESPLEGAR

1. En Render, click en **"Manual Deploy"** → **"Deploy latest commit"**
2. Espera 5-7 minutos
3. Monitorea en la pestaña **"Logs"**

---

## ✅ PASO 6: VERIFICAR

### Health Check

```bash
curl https://tu-servicio.onrender.com/api/actuator/health
```

**Respuesta esperada:**

```json
{
  "status": "UP"
}
```

### Probar desde el Frontend

1. Abre tu frontend en Netlify
2. Intenta registrar un usuario
3. Si funciona → ✅ **TODO CORRECTO**

---

## 🐛 TROUBLESHOOTING

### Error: "Communications link failure"

- Verifica que `SPRING_DATASOURCE_URL` tenga `ssl-mode=REQUIRED`
- Confirma que el host y puerto sean correctos

### Error: "Access denied"

- Ve a Aiven → Click en "REVEAL_PASSWORD"
- Copia el password exacto
- Actualiza `SPRING_DATASOURCE_PASSWORD` en Render

### Error: "CORS policy blocked"

- Verifica que `CORS_ORIGINS` tenga la URL correcta de Netlify
- Debe coincidir EXACTAMENTE con la URL del frontend

---

## 📝 CHECKLIST FINAL

- [ ] Todas las variables de entorno configuradas
- [ ] Código subido a GitHub
- [ ] Deploy ejecutado en Render
- [ ] Health check responde "UP"
- [ ] Frontend puede registrar usuarios
- [ ] No hay errores CORS

---

## 🎉 ¡LISTO!

Tu backend está desplegado en Render con MySQL de Aiven.

**Endpoints principales:**

```
POST /api/auth/register
POST /api/auth/login
POST /api/auth/refresh-token
GET  /api/actuator/health
```
