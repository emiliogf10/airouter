# =============================================================================
# Dockerfile Multi-Stage — Backend AI Cost Router (Spring Boot 4.1.1 / Java 21)
# =============================================================================
# Cumple con la guía de endurecimiento CLOUD-AND-DEPLOYMENT.md:
#   1. Separación estricta de etapa de compilación (JDK) y ejecución (JRE).
#   2. Ejecución bajo usuario del sistema sin privilegios (USER appuser).
#   3. Ajuste de memoria JVM consciente de límites de contenedor.
# =============================================================================

# -----------------------------------------------------------------------------
# Etapa 1: Construcción y empaquetado con Maven Wrapper y JDK 21 Alpine
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

# Copiar wrapper de Maven y descriptor de dependencias para aprovechar caché de capas
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Descargar dependencias en modo batch antes de copiar el código fuente
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copiar código fuente y migraciones Flyway y generar el artefacto ejecutable
COPY src src
RUN ./mvnw package -DskipTests -B

# -----------------------------------------------------------------------------
# Etapa 2: Imagen ligera de ejecución (JRE 21 Alpine) con mínimo privilegio
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Crear grupo y usuario sin privilegios de administración (No-Root)
RUN addgroup -S airouter && adduser -S appuser -G airouter

# Copiar únicamente el JAR empaquetado desde la etapa de compilación
COPY --from=build --chown=appuser:airouter /workspace/target/*.jar /app/app.jar

# Cambiar al usuario sin privilegios antes de exponer puertos o iniciar el proceso
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
