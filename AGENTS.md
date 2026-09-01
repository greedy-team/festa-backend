# festa-backend

축제 정보 서비스 백엔드. Spring Boot 기반이며 OCI 인스턴스에 Docker Compose로 배포합니다.

## 빠른 시작

```bash
./gradlew build       # 빌드 + 테스트
./gradlew bootRun     # 애플리케이션 실행
./gradlew test        # 테스트만
```

## 작업 원칙

**코드를 만지기 전에 [`.claude/rules/coding-principles.md`](./.claude/rules/coding-principles.md)를 읽으세요.**

가정을 말하고 시작하기 / 최소한으로 만들기 / 외과적으로 바꾸기 / 검증 기준 먼저 세우기 /
같은 규칙을 두 곳에 적지 않기 — 다섯 가지이며, 각각 이 프로젝트에서 실제로 터진 사례가
근거로 붙어 있습니다.

## 지침 파일 지도

**내용은 전부 `.claude/` 아래에 둡니다. 다른 도구는 그것을 읽습니다.**
같은 규칙을 두 벌로 관리하면 반드시 어긋나기 때문입니다.

| 파일 | 담는 것 | Claude Code | Codex |
| --- | --- | --- | --- |
| `AGENTS.md` | 프로젝트 사실·제약 (이 파일) | `CLAUDE.md`가 import | 세션 시작 시 자동 |
| `.claude/rules/coding-principles.md` | 작업 원칙 | `CLAUDE.md`가 import | **이 파일의 링크를 따라 읽으세요** |
| `TEAM-CONVENTIONS.md` | 이슈·브랜치·커밋·PR 규칙 | 필요 시 | 필요 시 |
| `.claude/commands/*.md` | 커맨드 워크플로우 | 슬래시 커맨드 | `.agents/skills/`가 가리킴 |

Codex는 `AGENTS.md`와 `.agents/skills/`만 자동으로 읽습니다. `.claude/` 아래 파일은
자동으로 열리지 않으니, 위 표의 경로를 직접 읽으세요.

## 하드 제약

깨면 안 되는 것들입니다. 어긴 채 진행하지 마세요.

- **`main`에 직접 푸시 금지.** `main`은 릴리스 브랜치입니다
- **`version.yml`의 `options.deploy`는 `none`으로 유지.** `docker-ssh`로 되돌리면 `npx projectops` 업데이트 때 Docker 워크플로우 4종이 재설치됩니다
- **커밋 메시지에 AI 태그 금지.** `Co-Authored-By: Claude`·`Generated with …`·GPT 서명 등 전부 — 규칙 원본은 [`TEAM-CONVENTIONS.md`](./TEAM-CONVENTIONS.md) §4
- **커밋·푸시는 사용자가 요청할 때만.** 알아서 하지 않습니다

## 기술 스택

- Spring Boot 4.1 · Java 21
- Gradle 9.6 (Kotlin DSL)
- Spring Data JPA · Spring Security · OAuth2 Client · Validation

## 코드 스타일

- 기존 파일의 스타일을 따릅니다. 주변 코드와 다른 방식을 새로 들이지 않습니다
- 포맷터는 아직 도입 전입니다. 도입되면 이 문단과 `/commit`의 포맷 단계를 함께 채웁니다
- 요청하지 않은 리팩터링·추상화를 끼워 넣지 않습니다

## 로그를 남길 때

**어느 계층에 남기는가**

| 계층 | 남기나 | 이유 |
| --- | --- | --- |
| 컨트롤러 | 아니오 | 요청·응답은 공통 지점이 이미 본다. 메서드마다 찍으면 같은 사건이 두 줄이 된다 |
| 엔티티·리포지토리 | 아니오 | 호출 맥락을 모른다. 무엇 때문에 불렸는지 없는 줄은 나중에 못 읽는다 |
| 서비스 | 중요한 도메인 사건만 | 되돌릴 수 없는 작업(삭제·병합·발행 취소)과 일괄 작업 요약 |
| 예외 처리 | 예 | `GlobalExceptionHandler`가 요청 경로와 함께 한 번에 남긴다 |
| 인증 진입점 | 예 | 토큰 오류는 보안 필터에서 끝나 전역 예외 처리까지 가지 않는다 |

컨트롤러의 `@ExceptionHandler`는 예외 처리 지점이지 컨트롤러 로직이 아닙니다. 전역 핸들러로
넘기지 않고 직접 응답하는 분기는 거기서 남깁니다.

**도메인 맥락은 응답이 아니라 로그로**

응답 본문의 `message`는 언제나 `ErrorCode`의 문구입니다. 어느 축제인지, 어떤 값이 문제였는지는
`FestaException`의 `logMessage`에 담고, 삼킨 원인은 세 번째 인자로 넘깁니다.

```java
throw new FestaException(ImportErrorCode.IMPORT_INVALID_CSV, "CSV 본문 파싱 실패", e);
```

**잡은 자리에서 찍지 않습니다.** 거기서는 어느 요청이 실패했는지 모릅니다. 예외에 실어 보내면
`GlobalExceptionHandler`가 요청 경로와 함께 한 줄로 남깁니다. 두 곳에서 찍으면 한 사건이 두 줄이 됩니다.

`GlobalExceptionHandler.toResponse`는 메시지를 인자로 받지 않습니다. 구조가 규칙을 강제하므로,
맥락을 붙였다는 이유로 응답이 달라지는 경로가 없습니다.

**남기지 않는 것**

- 비밀번호·토큰·시크릿은 어떤 형태로도 남기지 않습니다. 로그인 실패에는 시도한 아이디만 남깁니다
- 업로드된 CSV 본문은 남기지 않습니다. 어느 단계에서 깨졌는지와 예외만 남깁니다

**레벨**

`INFO`는 화면(stdout)으로만 나가고 컨테이너에 딸려 있어, 배포로 컨테이너가 교체되면 사라집니다.
파일로 보존되는 것은 `WARN` 이상입니다 (#116 / PR #125가 머지된 뒤부터. 그 전에는 파일 로그 자체가 없습니다).

따라서 **감사 목적의 `INFO` 줄은 다음 배포까지만 남습니다.** 영구 보존이 필요하면 로그가 아니라
감사 테이블입니다 — 임포트가 그렇게 하고 있습니다(DEC-0077).

## 작업 흐름

이슈 → 브랜치 → 커밋 → PR → 릴리스가 GitHub Actions로 이어집니다.
**규칙 원본은 [`TEAM-CONVENTIONS.md`](./TEAM-CONVENTIONS.md)입니다.** 여기서 반복하지 않습니다.

요약하면:

- 작업은 **이슈부터** 만듭니다. 봇이 브랜치명과 커밋 메시지를 댓글로 알려줍니다
- 브랜치는 `develop`에서 분기하고, **커밋 전에 빈 채로 먼저 푸시**합니다
- 브랜치명은 `타입_이슈번호_슬러그` (한글 그대로 씁니다)
- 커밋은 `<타입> : <변경 사항 설명> #<이슈번호>`
- PR은 `develop`으로. 머지하면 이슈가 자동으로 닫힙니다

`main`으로 가는 PR은 `develop`에서만 엽니다. 이것만 워크플로우로 강제됩니다.

이슈·브랜치·커밋·릴리스 규칙은 `festa-frontend`와 **완전히 동일**합니다. 다른 것은 배포뿐입니다.

## 커맨드

위 흐름을 대신 실행하는 커맨드가 있습니다.

| | |
| --- | --- |
| Claude Code | `.claude/commands/*.md` — `/issue` `/issue-branch` `/commit` `/report` `/pr-description` `/rp` `/cr` |
| Codex | `.agents/skills/*/SKILL.md` — 같은 파일을 읽습니다 |

**규칙 원본은 `.claude/commands/` 한 곳뿐입니다.** Codex 스킬은 내용을 복사하지 않고 그 파일을 가리킵니다.

산출물은 레포에 커밋합니다 (숨김 폴더 아님):

```
docs/issues/    이슈 초안
docs/reports/   구현 보고서
docs/pr/        PR 본문 초안
```

`/commit`의 포맷 단계는 비어 있습니다 — 포맷터(spotless 등) 도입 후 채웁니다.

## CI/CD

| 언제 | 무엇이 |
| --- | --- |
| 이슈 생성·라벨 변경 | 브랜치명·커밋 메시지 댓글 |
| PR → `develop` 머지 | 이슈 자동 종료 |
| `develop` → `main` PR | CHANGELOG 생성 후 자동 머지 |
| push `main` | 버전 태그 + README 갱신 |
| push `develop` | `PROJECT-SPRING-CD` — OCI E2 인스턴스로 배포 (`development` 환경) |
| push `main` | `PROJECT-SPRING-CD` — OCI A1 인스턴스로 배포 (`production` 환경) |

배포는 `PROJECT-SPRING-CD.yaml`이 담당합니다. GitHub 러너에서 Docker 이미지를 빌드해 SSH로
인스턴스에 전달하고, 서버는 `deploy/compose.yaml`로 app과 postgres를 함께 띄웁니다 — 서버는
컴파일하지 않습니다. 헬스체크에 실패하면 직전 성공 이미지로 자동 롤백하되, **되돌리는 것은
이미지뿐이고 이미 적용된 DB 스키마는 그대로 남습니다.**

`production` Environment는 아직 만들어져 있지 않습니다. 그 상태로 `main`에 푸시되면
`DEPLOY_ENABLED` 가드에서 배포가 중단됩니다.

`push` 이벤트는 **푸시된 커밋에서** 워크플로우를 읽으므로, CD 워크플로우 자체도 `main`에
올라가 있어야 합니다. 같은 릴리스 흐름을 타면 자연히 해결됩니다.

### 앱에 환경변수를 추가할 때

`application.yml`에 **기본값 없는** `${VAR}`를 추가하면 세 곳을 함께 고쳐야 합니다.
하나라도 빠지면 앱이 기동하지 못해 배포가 실패하고 롤백됩니다.

1. GitHub Environment(`development`)에 Secret 등록
2. `.github/workflows/PROJECT-SPRING-CD.yaml` — `env:` 블록과 `write_env` 목록
3. `deploy/compose.yaml` — app 서비스의 `environment:`

테스트는 이런 값을 인라인으로 주입하므로 **CI는 통과합니다.** 누락은 실제 배포에서만
드러납니다 (이슈 #47).

기본값을 주면(`${VAR:}`) 이 절차 없이도 앱은 뜨지만, 그 값에 의존하는 기능은 동작하지
않습니다.

## 주의할 점

- 릴리스 워크플로우는 `git add -A`로 커밋합니다. 워킹 트리에 남긴 임시 파일이 릴리스 커밋에 쓸려 들어갑니다
- `.github/scripts/`와 워크플로우는 `npx projectops` 업데이트 시 덮어써집니다. 설정은 코드 기본값이 아니라 `version.yml`에 둡니다
- `PROJECT-SPRING-CI.yaml`은 우리가 쓴 파일이지만 ProjectOps가 차지할 수 있는 이름입니다. 프론트의 `PROJECT-REACT-CI.yaml`이 ProjectOps 제공 템플릿이라 대칭을 택했습니다. 업데이트 후 이 파일이 낯설게 바뀌어 있다면 그 경우입니다
- `version.yml`의 `deploy:` 블록은 런타임에 아무도 읽지 않습니다. `npx projectops` 재실행 때만 쓰이는 메모입니다
