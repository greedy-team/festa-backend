# api.every-festa.com HTTPS 서빙과 배포 프론트 CORS 허용 추가 최종 구현 리포트

## 작업 개요

- 관련 Issue: #68
- 관련 PR: #75
- 최종 merge commit: `0abf0e64cfaedee280af990c2a13e7107bae6d0d`
- 목적: 현재 development 배포 서버의 Spring Boot API를 `https://api.every-festa.com`으로 제공하고, 배포 프론트엔드 origin의 CORS 요청을 허용한다.

PR 최초 구현 뒤 코드 리뷰에서 배포가 애플리케이션의 8080 health만 확인하면 Caddy, 인증서 또는 443 경로 장애를 놓칠 수 있다는 문제가 확인되었다. 최종 구현에는 공개 HTTPS endpoint를 직접 확인하는 CD 단계가 추가되었으며, CORS 환경변수 전달 범위에 관한 설명도 실제 구성에 맞게 정정되었다.

## 구현 범위

- `deploy/Caddyfile` 신규 추가
- Docker Compose에 Caddy 서비스와 인증서·설정 영속 볼륨 추가
- CD에서 Caddyfile을 OCI 서버에 전송하고 Compose로 함께 기동
- 기존 애플리케이션 health check와 별도로 공개 HTTPS endpoint 검증 추가
- development CORS 기본 허용 origin에 배포 프론트엔드 도메인 추가
- development profile 기반 CORS preflight 회귀 테스트 추가

## 주요 구현 내용

### HTTPS/도메인 적용 구조

요청 경로는 다음과 같다.

`Client -> api.every-festa.com:443 -> Caddy TLS termination -> app:8080`

- 공개 API 도메인은 `api.every-festa.com`이다.
- Caddy가 80/443을 수신하며 자동 HTTPS와 HTTP에서 HTTPS로의 전환을 담당한다.
- TLS 종료 뒤 동일 Docker Compose 네트워크의 `app:8080`으로 HTTP reverse proxy 한다.
- 기존 애플리케이션의 `8080:8080` 포트 공개는 이번 단계에서 유지했다. 기존 CD의 직접 health check 경로를 보존하기 위한 단계적 적용이며, HTTPS만 허용하는 최종 보안 상태는 아니다.

### Caddy 및 TLS/리버스 프록시 최종 구성

`deploy/Caddyfile`은 `api.every-festa.com` 사이트에서 `app:8080`으로 reverse proxy 하도록 구성되었다. Compose 서비스 이름 `app`이 내부 DNS 이름으로 사용되므로 두 컨테이너 사이에서 해당 대상이 해석된다.

Docker Compose의 Caddy 구성은 다음과 같다.

- 이미지: `caddy:2-alpine`
- 공개 포트: `80:80`, `443:443`
- Caddyfile: `/etc/caddy/Caddyfile`에 read-only mount
- 인증서 및 운영 데이터: `caddy-data:/data`
- 설정 데이터: `caddy-config:/config`
- 재시작 정책: `unless-stopped`
- 기동 관계: `app`에 의존

인증서 데이터가 named volume에 저장되므로 Caddy 컨테이너가 재생성되어도 유지된다. 실제 인증서 발급과 갱신을 위해서는 OCI VCN Security List와 서버 방화벽에서 80/443 ingress가 허용되어 있어야 한다.

### GitHub Actions CD 변경 내용

CD는 `deploy/compose.yaml`과 함께 `deploy/Caddyfile`을 OCI 서버의 배포 디렉터리로 전송한다. 서버에서는 기존 PostgreSQL과 애플리케이션에 Caddy를 더한 Compose 구성을 기동한다.

워크플로에는 다음 공개 endpoint 변수가 추가되었다.

- 기본값: `https://api.every-festa.com`
- 재정의: GitHub Actions variable `PUBLIC_BASE_URL`

현재 워크플로는 `develop`과 `main` push에 대해 각각 development와 production 환경을 선택하는 분기를 갖고 있다. 다만 PR 논의 기준으로 실제 사용 가능한 서버와 설정은 development 환경이며, production 인프라와 production CORS는 이번 작업의 검증·적용 범위가 아니다.

## 배포 후 health check와 HTTPS 서빙 확인

최종 CD는 서로 목적이 다른 검증을 순서대로 수행한다.

1. `docker compose up --wait`로 컨테이너를 기동한다.
2. 서버 내부에서 `http://localhost:8080/actuator/health`를 확인한다.
3. Actions runner에서 `http://$OCI_HOST:8080/actuator/health`를 확인한다.
4. Actions runner에서 `${PUBLIC_BASE_URL}/actuator/health`를 HTTPS로 확인한다.
5. 모든 검증을 통과한 뒤에만 현재 이미지를 마지막 성공 이미지로 기록한다.

기존 8080 검증은 Spring Boot 애플리케이션의 생존과 외부 직접 접근을 확인한다. 새 HTTPS 검증은 DNS, 443 접근, TLS 인증서 검증, Caddy reverse proxy와 최종 HTTP 성공 응답을 함께 확인한다. `curl`은 최대 15회, 4초 간격으로 재시도하며 인증서 검증을 우회하지 않는다.

8080 health check 실패 시에는 직전 성공 애플리케이션 이미지로 rollback한다. 반면 HTTPS 검증 실패는 배포를 실패 처리하고 새 이미지를 마지막 성공 이미지로 기록하지 않지만, 애플리케이션 이미지 rollback은 실행하지 않는다. 인증서 발급, Caddy, DNS 또는 방화벽 장애는 이전 애플리케이션 이미지로 되돌려도 해결되지 않기 때문이다.

Caddy 자체 healthcheck는 Compose에 추가하지 않았다. 따라서 `docker compose up --wait`만으로 TLS 발급 실패를 판별하지는 못하며, 뒤이어 실행되는 공개 HTTPS endpoint 확인이 이 공백을 보완한다.

## CORS 관련 최종 결정

development profile의 기본 허용 origin은 다음 세 개다.

- `http://localhost:3000`
- `https://every-festa.com`
- `https://www.every-festa.com`

API 도메인인 `https://api.every-festa.com`은 브라우저 요청의 프론트엔드 Origin이 아니므로 허용 origin에 넣지 않았다. 기존 허용 method와 header 정책을 유지하며 credential 허용은 활성화하지 않았다.

설정은 `${CORS_ALLOWED_ORIGINS:...}` 형식이어서 애플리케이션에 환경변수가 전달된다면 전체 목록을 재정의할 수 있다. 그러나 최종 CD의 `app.env` 생성과 Compose의 `app.environment`에는 `CORS_ALLOWED_ORIGINS` 전달 경로가 없다. 따라서 현재 배포에서는 YAML의 development 기본값이 실제 적용되며, 환경변수 재정의 기능은 연결되어 있지 않다.

공통 설정의 기본값은 빈 목록이고 별도의 production CORS 설정은 이번 PR에서 추가하지 않았다. PR review thread에서 production 서버와 구성은 현재 실제 운영 경로가 아니라고 확인했으며, 실제 production 환경을 구축할 때 허용 origin과 환경변수 전달을 함께 정의하기로 범위를 구분했다.

## 리뷰에서 지적된 내용과 반영 결과

### 공개 HTTPS endpoint 검증 누락

- 지적: 애플리케이션 8080 health만 성공하면 Caddy 기동, 인증서 발급 또는 443 접근이 실패해도 CD가 성공할 수 있었다.
- 반영: `26b993c`에서 외부 8080 확인 뒤 `https://api.every-festa.com/actuator/health`를 직접 확인하는 단계를 추가했다.
- 결과: HTTPS 검증을 통과하기 전에는 새 이미지를 마지막 성공 이미지로 기록하지 않는다.
- rollback 결정: HTTPS 계층 장애에는 애플리케이션 이미지 rollback이 유효하지 않아 배포 실패 처리만 하고 자동 rollback은 하지 않는다.

### production CORS와 환경변수 전달 범위

- 지적: origin 추가가 development profile에만 있고, CD에서 `CORS_ALLOWED_ORIGINS`를 컨테이너로 전달하지 않아 production 배포에서는 프론트 CORS가 허용되지 않는다.
- 반영 여부: production CORS 및 환경변수 전달은 코드에 추가하지 않았다.
- 이유: review thread에서 현재 실제 배포 대상은 development 서버이며 production 서버·설정은 아직 사용할 수 없다고 확인했다. 이번 PR은 현재 사용 중인 development 배포 경로에 한정했다.
- 문서 정정: `58d7e25`에서 환경변수로 재정의할 수 있다는 오해가 없도록, 현재 CD/Compose에는 전달 경로가 없다는 주석으로 수정했다.

## 테스트 및 최종 CI 결과

PR 최종 head `58d7e25ab4cd4716393d1d1fb7fede695da8af16`에 대한 GitHub Actions run `32929933823` 결과는 다음과 같다.

- Gradle build: 성공
- Test Results: 성공
- JUnit Test Report: 성공
- 전체 테스트: 225
- passed: 225
- failed: 0
- skipped: 0

추가된 `SecurityConfigCorsTest`는 development profile의 실제 설정을 사용해 다음을 확인한다.

- `https://every-festa.com` preflight 허용 및 `Access-Control-Allow-Origin` 응답
- 기존 `http://localhost:3000` 허용 유지
- 미허용 origin 차단

실제 ACME 인증서 발급은 PR CI에서 재현하지 않으며, merge 후 CD의 공개 HTTPS endpoint 단계에서 운영 경로를 검증한다.

### merge 후 실제 배포 결과

merge commit `0abf0e64cfaedee280af990c2a13e7107bae6d0d`으로 실행된 PROJECT-SPRING-CD run `32934899815`는 실패했다.

- 새 이미지와 Caddy 이미지는 서버에 정상 전송·적재되었다.
- PostgreSQL과 Caddy는 기동되었지만 새 애플리케이션 컨테이너가 `docker compose up --wait --wait-timeout 180`의 3분 안에 healthy가 되지 않았다.
- CD는 직전 성공 이미지 `1cf318e6fb4be5969c36e6b54858ad02ab8db722`로 rollback했고, rollback된 애플리케이션의 내부 Actuator 응답 `status=UP`을 확인했다.
- 실패 위치가 `컨테이너 재기동` 단계이므로 외부 8080 health check와 새 `HTTPS 서빙 확인` 단계는 실행되지 않았다.

따라서 PR 코드의 테스트 CI는 225/225로 성공했지만, merge 시점의 development 배포와 실제 공개 HTTPS 서빙은 이 실행으로 완료 확인되지 않았다. 실패 로그만으로 새 애플리케이션이 3분 내 healthy가 되지 않은 근본 원인까지는 확정할 수 없다.

## 제한사항 및 후속 작업

- `CORS_ALLOWED_ORIGINS`는 설정 문법상 지원하지만 현재 CD에서 `app.env`나 컨테이너 환경으로 전달하지 않는다. 환경별 동적 재정의가 필요해질 때 CD와 Compose 전달 경로를 추가해야 한다.
- production 서버와 설정은 현재 실제 사용·검증 범위가 아니다. production 배포를 구성할 때 production CORS 허용 origin, secret/variable 전달, 공개 HTTPS endpoint를 별도로 확정해야 한다.
- 애플리케이션 8080 포트가 외부에 계속 공개되어 HTTPS를 우회한 평문 접근이 가능하다. HTTPS 운영이 안정화되면 CD health check를 내부 또는 443 경로로 전환하고 8080 ingress를 닫는 후속 작업이 필요하다.
- OCI VCN Security List와 서버 방화벽의 80/443 허용은 코드 밖의 선행 운영 작업이다.
- HTTPS 확인은 HTTP 성공 여부와 TLS 유효성을 검사하지만 Actuator JSON의 `status=UP` 값 자체를 파싱하지는 않는다.
- Caddy 전용 Compose healthcheck는 없으며, 공개 HTTPS 확인 실패 시 Caddy나 인프라를 자동 rollback하지 않는다.
- merge 후 첫 실제 CD는 새 애플리케이션 health timeout으로 rollback되었고 HTTPS 검증 단계에 도달하지 못했다. 배포 로그와 애플리케이션 시작 시간을 확인한 뒤 CD를 다시 성공시켜 공개 HTTPS를 검증해야 한다.

## 변경 범위 요약

| 파일 | 최종 변경 |
| --- | --- |
| `.github/workflows/PROJECT-SPRING-CD.yaml` | Caddyfile 전송, 공개 HTTPS endpoint 재시도 검증 추가 |
| `deploy/Caddyfile` | `api.every-festa.com`에서 `app:8080`으로 reverse proxy |
| `deploy/compose.yaml` | Caddy 서비스, 80/443 포트, 영속 볼륨 추가 |
| `src/main/resources/application-development.yml` | development 프론트 CORS 기본 origin 추가 및 환경변수 전달 범위 명시 |
| `src/test/java/com/greedy/festa/global/config/SecurityConfigCorsTest.java` | 배포 origin, localhost, 미허용 origin CORS 회귀 테스트 추가 |

최종 변경은 Caddy 기반 HTTPS 서빙, 배포 단계의 실제 HTTPS 확인, development 프론트 CORS 허용에 집중한다. production CORS와 8080 비공개 전환은 현재 인프라 준비 상태에 맞춰 후속 범위로 남겼다.
