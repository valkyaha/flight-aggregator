# syntax=docker/dockerfile:1

# ---- build stage ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
# Cache dependencies first
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies || true
# Build the layered boot jar
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar

# ---- runtime stage ----
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
RUN useradd -r -u 1001 appuser
COPY --from=build /app/build/libs/*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
