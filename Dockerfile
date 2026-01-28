# ==========================================
# DOCKERFILE PARA PRODUCCIÓN - SPRING BOOT
# Casa de Música Castillo - Auth System
# ==========================================

# Etapa 1: Build
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Copiar archivos de dependencias primero (cache de Docker)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar código fuente y compilar
COPY src ./src
RUN mvn clean package -DskipTests -B

# Etapa 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Crear usuario no-root para seguridad
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Copiar JAR desde etapa de build
COPY --from=build /app/target/*.jar app.jar

# Cambiar a usuario no-root
USER appuser

# AJUSTE 1: Memoria Reducida (Crucial para plan gratuito)
# Bajamos Xmx a 384m para dejar espacio al sistema operativo dentro de los 512m de Render
ENV JAVA_OPTS="-Xmx384m -Xms256m -XX:+UseG1GC -XX:+UseContainerSupport"
ENV SPRING_PROFILES_ACTIVE=production

# Puerto de la aplicación
EXPOSE 8080

# AJUSTE 2: Healthcheck simplificado o comentado
# Si no tienes Actuator instalado, esto matará tu app.
# Lo dejo comentado por seguridad. Descoméntalo solo si sabes que /actuator/health funciona.
# HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=5 \
#    CMD wget --no-verbose --tries=1 --spider http://localhost:${PORT:-8080}/actuator/health || exit 1

# Comando de inicio - Render proporciona la variable $PORT
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]