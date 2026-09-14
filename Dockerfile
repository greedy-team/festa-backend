FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jre-alpine AS extract

WORKDIR /builder
COPY app.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl \
    && addgroup -S festa \
    && adduser -S festa -G festa

WORKDIR /app

# 파일 로그가 나가는 자리. 여기에 붙는 이름 있는 볼륨이 첫 생성 때 이 디렉터리의
# 소유권을 물려받으므로, 비root(festa)로 도는 앱이 권한 조정 없이 쓸 수 있다.
RUN mkdir -p /app/logs && chown festa:festa /app/logs

# 의존성이 같으면 앱 코드가 바뀌어도 이 레이어들은 서버에서 재사용한다.
COPY --from=extract --chown=festa:festa /builder/extracted/dependencies/ ./
COPY --from=extract --chown=festa:festa /builder/extracted/spring-boot-loader/ ./
COPY --from=extract --chown=festa:festa /builder/extracted/snapshot-dependencies/ ./
COPY --from=extract --chown=festa:festa /builder/extracted/application/ ./

USER festa

EXPOSE 8080

# start-period 동안의 실패는 retries로 세지 않는다. E2(1 OCPU/1GB)에서 실측 기동이
# 128~190초를 오가므로 여유를 얹어 240초로 잡는다. 기동이 끝난 뒤의 장애는 retries가
# 맡으며, 이쪽은 짧아야 죽은 앱이 빨리 드러난다.
HEALTHCHECK --interval=10s --timeout=5s --start-period=240s --retries=3 \
    CMD curl --fail --silent --show-error http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
