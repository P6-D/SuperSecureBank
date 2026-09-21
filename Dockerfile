# ── Stage 1: Build ──────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Copy Gradle wrapper and config first for layer caching
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/

# Make wrapper executable and download dependencies (cached layer)
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# Copy source code and build the fat JAR
COPY src/ src/
RUN ./gradlew bootJar --no-daemon

# ── Stage 2: Runtime ────────────────────────────────────────────────
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy the Spring Boot fat JAR from the build stage
COPY --from=build /app/build/libs/SecureBank-Backend-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
