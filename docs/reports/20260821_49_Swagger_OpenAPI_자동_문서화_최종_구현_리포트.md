### 📌 작업 개요

Issue #49와 PR #50은 springdoc-openapi를 도입해 현재 구현된 REST API의 OpenAPI 문서와
Swagger UI를 자동 생성하고, 관리자 API를 JWT로 직접 시험할 수 있게 하는 작업이다.

검토 기준은 PR head `fb0e9b592d09336024d9ddf8ac44ecb97f375cdd`이며, PR base는 확인 시점의
최신 `develop` `a8b0bb4cc080cf1b1bd95028806d7e40fd1c3c03`과 일치한다. 따라서 아래 내용은
최신 develop에 이미 있던 관리자 로그인·주최 API와 PR #50이 추가한 문서화·프로파일 전달 변경을
구분해 기록한다.

### 🎯 도입 목적

- 코드와 별도 관리되는 API 명세의 불일치를 줄이고, 구현된 API를 실행 코드에서 문서화한다.
- 프론트엔드와 개발자가 실제 요청·응답 구조와 엔드포인트별 에러 코드를 확인할 수 있게 한다.
- 관리자 로그인으로 받은 access token을 Swagger UI의 Authorize에 입력해 관리자 API를 시험할 수
  있게 한다.
- 운영 노출을 피하기 위해 Swagger/OpenAPI가 꺼진 상태를 기본값으로 두고 개발 환경에서만 켠다.

### ✅ 구현 내용

#### springdoc-openapi 의존성

`build.gradle.kts`에 다음 의존성을 추가했다.

```kotlin
val springdocVersion = "3.1.0"
implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")
```

사용 버전은 `3.1.0`이다. Spring Boot BOM이 springdoc 버전을 관리하지 않으므로 버전을 직접
지정한다. PR head의 Spring Boot 버전은 `4.1.0`이며, springdoc-openapi 3.1.0도 Spring Boot
4.1.0을 대상으로 릴리스된 계열이다. 별도의 springdoc API starter를 중복 추가하지 않고 UI
starter 하나만 사용한다.

#### SwaggerConfig와 OpenAPI 기본 정보

새 `SwaggerConfig`는 `OpenAPI` 빈을 생성하고 다음 기본 정보를 등록한다.

| 항목 | 값 |
| --- | --- |
| title | `festa API` |
| description | `축제 정보 서비스 festa의 API 문서. /admin 으로 시작하는 경로는 관리자 로그인으로 받은 토큰이 필요하다.` |
| version | `v1` |

이 빈은 profile 조건 없이 생성된다. 다만 문서 endpoint와 UI의 활성 여부는 springdoc 프로퍼티가
결정하므로, 기본·production 환경에서 빈이 존재하더라도 Swagger/OpenAPI endpoint는 등록되지
않는다.

#### 관리자 JWT bearerAuth

`SwaggerConfig`는 이름이 `bearerAuth`인 SecurityScheme을 components에 등록한다.

```text
type: HTTP
scheme: bearer
bearerFormat: JWT
```

스킴 이름은 `SwaggerConfig.BEARER_AUTH` 상수로 정의해 Controller annotation과 같은 값을 쓴다.
Swagger UI의 Authorize에는 access token 자체를 입력하며, HTTP bearer 스킴이 요청에
`Authorization: Bearer <token>` 형식으로 붙인다.

스킴만 components에 등록하고 OpenAPI 전역 SecurityRequirement는 추가하지 않았다. 따라서 공개
API 전체가 인증 필요 API처럼 표시되지 않는다.

#### Controller 문서화 및 인증 표시 정책

PR #50은 최신 develop에 이미 있던 다음 Controller에 문서 annotation을 추가한다.

- `AdminAuthController`: 관리자 로그인 태그, operation 요약, 200·401 응답 설명
- `HostAdminController`: 관리자 주최 태그, CRUD operation 요약, 엔드포인트별 에러 응답 설명

`@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)`는 `HostAdminController` 클래스에
적용된다. 이에 따라 해당 Controller의 주최 등록·목록·수정·삭제 API는 Swagger 문서에서 관리자
JWT가 필요한 API로 표시된다.

`AdminAuthController`에는 SecurityRequirement를 붙이지 않았다. 따라서
`POST /admin/auth/login`은 인증이 필요한 API로 표시되지 않으며, 로그인으로 토큰을 먼저 발급받는
흐름과 일치한다. 이는 기존 SecurityConfig의 로그인 `permitAll` 및 관리자 인증 정책을 변경하지
않고 문서에 표현한 것이다.

### 🌐 환경별 Swagger/OpenAPI 정책

#### 기본 설정: 비활성화

`src/main/resources/application.yml`에 다음 공통 설정을 추가했다.

```yaml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

profile을 지정하지 않으면 `/v3/api-docs`와 Swagger UI가 모두 비활성화된다. 배포 설정이 누락된
경우에도 문서가 열리지 않는 방향을 기본값으로 선택했다.

#### development profile: 활성화

새 `src/main/resources/application-development.yml`은 두 기능을 다시 활성화한다.

```yaml
springdoc:
  api-docs:
    enabled: true
  swagger-ui:
    enabled: true
```

Spring의 profile 설정 병합에 따라 `development`가 활성화되면 공통 설정의 `false`가 이 파일의
`true`로 덮인다.

#### production profile: 비활성화 유지

`application-production.yml`은 추가하지 않았다. production profile은 공통 `application.yml`의
비활성화 값을 그대로 사용하므로 Swagger UI와 API docs가 열리지 않는 구조다.

### 🚀 SPRING_PROFILES_ACTIVE 전달 구조

#### GitHub Actions CD

`.github/workflows/PROJECT-SPRING-CD.yaml`의 기존 배포 환경 계산은 다음과 같다.

- `develop` push → GitHub Environment 및 `DEPLOY_ENVIRONMENT`가 `development`
- `main` push → GitHub Environment 및 `DEPLOY_ENVIRONMENT`가 `production`
- 수동 실행(`workflow_dispatch`) → 사용자가 선택한 `development` 또는 `production`

PR #50은 이미 계산된 `DEPLOY_ENVIRONMENT`를 `app.env`에 다음과 같이 기록한다.

```bash
write_env SPRING_PROFILES_ACTIVE "$DEPLOY_ENVIRONMENT"
```

기존 DB, Google OAuth, JWT, AES 및 초기 관리자 환경변수를 쓰는 경로는 유지되며 그 앞에 profile
한 항목이 추가된다.

#### deploy/compose.yaml

Compose의 app container 환경변수에 다음 전달을 추가했다.

```yaml
SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-}
```

CD가 만든 `app.env`의 값을 Spring Boot container로 전달한다. 값이 없으면 빈 문자열을 전달해
profile 없이 기동하고, 이 경우 공통 설정에 따라 Swagger/OpenAPI는 비활성화된다.

### 🔐 기존 보안 및 운영 경로에 미치는 영향

PR #50은 `SecurityConfig`를 수정하지 않는다. 확인 시점의 정책은 다음과 같다.

- `POST /admin/auth/login`: `permitAll`
- `/admin/**`: `authenticated`
- 그 밖의 요청: `permitAll`

따라서 development에서 `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs` 및
`/v3/api-docs/**`는 기존 `anyRequest().permitAll()` 규칙으로 접근 가능하다. production에서는
permitAll 여부와 무관하게 springdoc endpoint 자체가 비활성화된다.

`/actuator/health`의 exposure 설정과 SecurityConfig 통과 정책은 바뀌지 않았다. CD의 container
및 외부 health check도 계속 `/actuator/health`를 사용한다. 기존 관리자 JWT 필터, 관리자 API의
인증 요구, Controller mapping과 JSON 응답 생성 코드도 변경하지 않고 문서 annotation만 추가했다.

관련 활성 결정과의 관계는 다음과 같다.

- DEC-0082: 관리자 자체 로그인 + JWT 인증 방식 유지
- DEC-0083: `/admin/**`은 별도 역할 구분 없이 인증만 요구하는 정책 유지
- DEC-0085: `POST /admin/auth/login` 공개 로그인 계약 유지
- DEC-0086: 관리자 전용 JWT 서명 체계 유지
- DEC-0078·DEC-0080: develop/main 배포 환경 매핑과 develop 자동 배포 정책 활용

Swagger/OpenAPI 자체를 규정한 별도 활성 DEC는 확인되지 않았으며, 해당 정책의 직접 근거는
Issue #49와 PR #50이다.

### 📖 README 변경

README에 다음 내용을 추가·수정했다.

- `development` profile로 실행하는 Gradle 명령
- 로컬 기동에 사용하는 환경변수 표와 예시 값
- `ADMIN_INITIAL_USERNAME`과 `ADMIN_INITIAL_PASSWORD`의 함께 설정/함께 생략 규칙
- Docker로 PostgreSQL을 실행하는 명령과 포트 충돌 안내
- Swagger UI URL: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON URL: `http://localhost:8080/v3/api-docs`
- 관리자 로그인 후 받은 `accessToken`을 Authorize에 입력하는 방법
- profile 미지정 시 문서가 비활성화된다는 설명

실행 명령과 Swagger URL은 구현 설정과 일치한다. Google OAuth client ID/secret의 `dummy` 값은
현재 로컬 기동에 필요한 프로퍼티를 채우는 용도로 사용할 수 있다. 관리자 JWT 예시는 Base64로
디코딩되는 32바이트 이상 키 형식이다.

다만 README의 “아래 값이 하나라도 없으면 애플리케이션이 뜨지 않는다”는 문장은 실제 선택 항목과
완전히 일치하지 않는다. `SPRING_PROFILES_ACTIVE`는 없어도 기동하며,
`ADMIN_INITIAL_USERNAME`과 `ADMIN_INITIAL_PASSWORD`도 둘 다 비우면 시딩을 생략하고 기동한다.
이 차이는 실행 코드의 결함이 아니라 남아 있는 문서 정확성 문제다.

### 🧪 테스트 및 CI 검증

#### 추가된 자동 테스트

`SwaggerDisabledByDefaultTest`를 추가했다. RANDOM_PORT로 실제 Spring Boot web context를 띄우고
HTTP 요청을 보내 다음을 확인한다.

- profile 미지정 시 `/v3/api-docs`가 200/OpenAPI JSON을 반환하지 않는다.
- profile 미지정 시 `/swagger-ui/index.html`이 200을 반환하지 않는다.

단순 프로퍼티 값 확인이 아니라 실제 endpoint 응답을 검사하므로 기본 비활성화 정책의 회귀를
보호한다.

현재 자동 테스트에는 `development` profile에서 `/v3/api-docs`가 실제 OpenAPI JSON을 반환하는지,
Swagger UI가 열리는지, SecurityConfig를 통과하는지 확인하는 반대 방향의 검증은 없다.

#### PR head CI 결과

PR head `fb0e9b592d09336024d9ddf8ac44ecb97f375cdd`의 확인된 check run은 모두 성공했다.

| Check | 상태 |
| --- | --- |
| 빌드 검증 | success |
| Test Results | success |
| JUnit Test Report | success |

PR 설명에는 `./gradlew build`가 테스트 43개, 실패 0으로 통과했다고 기록되어 있다. 코드 리뷰에서
blocking 문제는 발견되지 않았다.

### ⚠️ 제한사항 및 후속 확인

- PR 체크리스트상 Swagger 수동 동작 확인은 아직 완료로 표시되지 않았다. 따라서 Swagger UI의
  실제 렌더링, 태그 표시, 자물쇠 표시 및 Authorize 후 관리자 API 호출을 완료했다고 기록하지 않는다.
- 머지 전 또는 development 배포 후 다음을 수동 확인할 필요가 있다.
  - `/swagger-ui.html`에서 UI가 열리고 `/v3/api-docs`가 생성되는지
  - 관리자 주최 API에는 JWT 인증 표시가 있고 로그인 API에는 없는지
  - 로그인 응답의 access token을 Authorize에 입력한 뒤 관리자 API 호출이 성공하는지
- `development` profile의 실제 Swagger UI/API docs 접근을 검증하는 자동 테스트는 현재 없다.
- README의 환경변수 필수/선택 설명은 실제 기본값과 일부 차이가 있다.
- 문서화 범위는 PR을 분기한 최신 develop에 있던 `AdminAuthController`와
  `HostAdminController`다. 다른 브랜치에서 진행 중이던 관리자 API를 이번 PR이 문서화한 것으로
  간주하지 않는다.
- 노션 API 명세 동결 안내는 저장소 밖 후속 작업이며 PR #50 코드 변경에 포함되지 않았다.
- PR 설명과 구현 리포트에 언급된 기존 404/500 처리 문제는 PR #50에서 발견했을 뿐 이번 구현으로
  해결하거나 새로 만든 문제가 아니다.

### 📦 변경 범위 요약

- 의존성: springdoc-openapi WebMVC UI starter 3.1.0 추가
- 설정: 공통 비활성화, development 활성화
- 문서 구성: OpenAPI 기본 정보 및 관리자 JWT bearerAuth 등록
- Controller: 관리자 로그인·주최 API 문서 annotation 추가
- 배포: GitHub Actions → `app.env` → Compose → app container로 profile 전달
- 문서: 로컬 실행, 환경변수, DB, Swagger 사용법 README 보강
- 테스트: 기본 profile의 Swagger/OpenAPI 비노출 HTTP 테스트 2개 추가

