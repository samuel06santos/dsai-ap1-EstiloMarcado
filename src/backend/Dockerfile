# Imagem de producao do backend (Estilo Marcado).
# Build multi-stage: compila com Maven/JDK 21 e executa apenas com o JRE 21.

# --- Estagio 1: build -------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Baixa as dependencias primeiro para aproveitar o cache de camadas.
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

# --- Estagio 2: execucao ----------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Executa sem privilegios.
RUN addgroup -S app && adduser -S app -G app

WORKDIR /app
COPY --from=build /build/target/*.jar /app/app.jar

USER app
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
