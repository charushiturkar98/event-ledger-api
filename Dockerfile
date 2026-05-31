# ─── Stage 1: build ──────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app
COPY pom.xml .
# Download dependencies in a separate layer for better caching
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# ─── Stage 2: runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app
COPY --from=build /app/target/event-ledger-api-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
