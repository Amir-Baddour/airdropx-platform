# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies separately from source so a source-only change doesn't re-download the internet.
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN useradd --system --create-home appuser
COPY --from=build /app/target/airdropx-backend.jar app.jar
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
