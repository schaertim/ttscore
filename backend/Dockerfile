# Multi-stage build for the Ktor backend.
# Build context is the backend/ directory (set Railway's service root to backend).

# --- Stage 1: build the fat jar ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew buildFatJar --no-daemon

# --- Stage 2: minimal runtime ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar app.jar
# Railway injects PORT; application.conf reads ${?PORT}.
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
