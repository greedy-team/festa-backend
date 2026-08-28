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

# start-period 동안의 실패는 retries로 세지 않는다. E2(1 OCPU/1GB)에서 실측 기동이
# 128~190초를 오가므로 여유를 얹어 240초로 잡는다. 기동이 끝난 뒤의 장애는 retries가
# 맡으며, 이쪽은 짧아야 죽은 앱이 빨리 드러난다.
HEALTHCHECK --interval=10s --timeout=5s --start-period=240s --retries=3 \
    CMD curl --fail --silent --show-error http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
