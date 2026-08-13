FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY src ./src

RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl \
    && addgroup -S festa \
    && adduser -S festa -G festa

WORKDIR /app

COPY --from=builder --chown=festa:festa /workspace/build/libs/*.jar app.jar

USER festa

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=12 \
    CMD curl --fail --silent --show-error http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
