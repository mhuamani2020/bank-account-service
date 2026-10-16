# syntax=docker/dockerfile:1
# ============================================================================
# bank-account-service — imagen Docker (Parte II: "cada microservicio en un
# contenedor independiente"). Build multi-etapa.
#
# El contexto de build es la RAÍZ del proyecto (ver docker-compose.yml):
#   docker build -f bank-account-service/Dockerfile -t bank-account-service .
# ============================================================================
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /workspace

# 1) Librería compartida de resiliencia (no está publicada en un repositorio Maven)
COPY bank-resilience-commons/pom.xml bank-resilience-commons/pom.xml
COPY bank-resilience-commons/src bank-resilience-commons/src
RUN mvn -q -B -f bank-resilience-commons/pom.xml -DskipTests install

# 2) Microservicio
COPY bank-account-service/pom.xml bank-account-service/pom.xml
COPY bank-account-service/src bank-account-service/src
RUN mvn -q -B -f bank-account-service/pom.xml -DskipTests package

# ---------- Etapa de ejecución ----------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /workspace/bank-account-service/target/*.jar app.jar
EXPOSE 8082
HEALTHCHECK --interval=10s --timeout=5s --start-period=45s --retries=12 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8082'
ENTRYPOINT ["java","-jar","/app/app.jar"]
