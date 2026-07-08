# syntax=docker/dockerfile:1

# ---- build stage ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY mock-providers/build.gradle.kts ./mock-providers/
RUN ./gradlew --no-daemon :mock-providers:dependencies || true
COPY mock-providers/src ./mock-providers/src
RUN ./gradlew --no-daemon :mock-providers:bootJar

# ---- runtime stage ----
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN useradd -r -u 1001 appuser
COPY --from=build /workspace/mock-providers/build/libs/*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
