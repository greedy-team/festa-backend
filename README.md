# FESTA

축제 정보 서비스 백엔드입니다.

## 기술 스택

- Spring Boot 4.1 · Java 21
- Gradle 9.6 (Kotlin DSL)
- Spring Data JPA · Spring Security · OAuth2 Client

## 로컬 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=development'   # 애플리케이션 실행
./gradlew build                                                   # 빌드 + 테스트
```

### 필요한 환경변수

아래 값이 하나라도 없으면 애플리케이션이 뜨지 않습니다. IntelliJ로 실행한다면
Run Configuration의 Environment variables에 넣습니다.

| 변수 | 로컬 예시 값 | 설명 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `development` | 없으면 API 문서가 닫힌 채로 뜬다 |
| `DB_URL` | `jdbc:postgresql://localhost:5432/festa` | 아래 「로컬 데이터베이스」의 포트와 맞춘다 |
| `DB_USERNAME` | `festa` | |
| `DB_PASSWORD` | `festa` | |
| `GOOGLE_CLIENT_ID` | `dummy` | 구글 로그인을 실제로 쓸 때만 진짜 값이 필요하다 |
| `GOOGLE_CLIENT_SECRET` | `dummy` | |
| `JWT_SECRET` | `local-dev-jwt-secret` | |
| `ADMIN_JWT_SECRET` | `ZmVzdGEtYWRtaW4tand0LXRlc3Qtc2VjcmV0LWtleS0zMg==` | 관리자 토큰 서명 키. base64로 32바이트 이상 |
| `AES_KEY` | `local-dev-aes-key` | |
| `ADMIN_INITIAL_USERNAME` | `admin` | 관리자 계정 시딩용. 아래 주의 참고 |
| `ADMIN_INITIAL_PASSWORD` | `admin1234` | |

값은 전부 **로컬 전용 임의 값**입니다. 배포 환경의 실제 값은 GitHub Environments에 있습니다.

`ADMIN_INITIAL_*` 두 개는 **함께 넣거나 함께 비워야** 합니다. 한쪽만 있으면 기동에 실패합니다.
둘 다 비우면 시딩을 건너뛰므로 관리자 로그인을 할 계정이 없습니다 — 관리자 API를 눌러보려면
채워야 합니다. 이미 있는 계정은 덮어쓰지 않습니다.

### 로컬 데이터베이스

```bash
docker run --rm -d --name festa-local -p 5432:5432   -e POSTGRES_DB=festa -e POSTGRES_USER=festa -e POSTGRES_PASSWORD=festa   postgres:16-alpine
```

PC에 Postgres가 이미 깔려 있으면 5432가 점유돼 있습니다. 그때는 `-p 55432:5432`처럼 다른 포트로
띄우고 `DB_URL`의 포트도 함께 바꿉니다. **네이티브 Postgres에 그대로 붙으면 `festa` 계정이 없어
`28P01` 인증 실패가 납니다.** 스키마는 기동할 때 Flyway가 만듭니다.

## API 문서 (Swagger)

`development` 프로파일로 띄우면 열립니다.

- 화면: <http://localhost:8080/swagger-ui.html>
- 문서(JSON): <http://localhost:8080/v3/api-docs>

`/admin` 으로 시작하는 API는 토큰이 필요합니다. `POST /admin/auth/login`으로 받은
`accessToken`을 화면 우측 상단 **Authorize**에 넣으면 그 아래 요청에 자동으로 붙습니다.

**프로파일을 지정하지 않으면 문서는 열리지 않습니다.** 꺼진 쪽이 기본값이라 운영 배포는
따로 막지 않아도 노출되지 않고, `develop` 자동 배포만 `development` 프로파일로 떠서 열립니다.

## 협업 규칙

이슈 → 브랜치 → 커밋 → PR → 릴리스가 자동으로 이어집니다. **이슈부터 만드세요** —
이슈를 만들면 봇이 브랜치명과 커밋 메시지를 댓글로 알려줍니다.

- 작업 브랜치는 `develop`에서 분기하고 `develop`으로 PR을 올립니다
- 브랜치명은 `타입_이슈번호_제목슬러그` (예: `docs_1_readme_개요_추가`)
- 배포는 `develop` → `main` PR로만 진행합니다

---

<!-- AUTO-VERSION-SECTION: DO NOT EDIT MANUALLY -->
## 최신 버전 : v0.0.4 (2026-08-03)

[전체 버전 기록 보기](CHANGELOG.md)
