# festa-backend

축제 정보 서비스 백엔드. Spring Boot 기반이며 배포는 아직 구성 전입니다.

## 하드 제약

깨면 안 되는 것들입니다. 어긴 채 진행하지 마세요.

- **`main`에 직접 푸시 금지.** `main`은 릴리스 브랜치입니다
- **`version.yml`의 `options.deploy`는 `none`으로 유지.** `docker-ssh`로 되돌리면 `npx projectops` 업데이트 때 Docker 워크플로우 4종이 재설치됩니다
- **커밋 메시지에 `Co-Authored-By` 금지**
- **커밋·푸시는 사용자가 요청할 때만.** 알아서 하지 않습니다

## 셋업

```bash
./gradlew build       # 빌드 + 테스트
./gradlew bootRun     # 애플리케이션 실행
./gradlew test        # 테스트만
```

## 기술 스택

- Spring Boot 4.1 · Java 21
- Gradle 9.6 (Kotlin DSL)
- Spring Data JPA · Spring Security · OAuth2 Client · Validation

## 코드 스타일

- 기존 파일의 스타일을 따릅니다. 주변 코드와 다른 방식을 새로 들이지 않습니다
- 포맷터는 아직 도입 전입니다. 도입되면 이 문단과 `/commit`의 포맷 단계를 함께 채웁니다
- 요청하지 않은 리팩터링·추상화를 끼워 넣지 않습니다

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

프론트에는 작업을 대신하는 커맨드 7종(`/issue` `/issue-branch` `/commit` `/report`
`/pr-description` `/rp` `/cr`)이 있고, **이 레포에는 아직 이식 전**입니다.
그때까지는 위 흐름을 직접 따릅니다.

## CI/CD

| 언제 | 무엇이 |
| --- | --- |
| 이슈 생성·라벨 변경 | 브랜치명·커밋 메시지 댓글 |
| PR → `develop` 머지 | 이슈 자동 종료 |
| `develop` → `main` PR | CHANGELOG 생성 후 자동 머지 |
| push `main` | 버전 태그 + README 갱신 |
| **배포** | **미설정** — 백엔드팀이 구성합니다 |

**배포를 붙일 자리는 `main` push입니다.** `on: push: branches: [main]` 워크플로우를
추가하면 릴리스 자동 머지가 일으키는 `main` 푸시에서 함께 실행됩니다. 프론트의 Vercel
배포가 정확히 그 자리에 있습니다.

`push` 이벤트는 **푸시된 커밋에서** 워크플로우를 읽으므로, CD 워크플로우 자체도 `main`에
올라가 있어야 합니다. 같은 릴리스 흐름을 타면 자연히 해결됩니다.

## 주의할 점

- 릴리스 워크플로우는 `git add -A`로 커밋합니다. 워킹 트리에 남긴 임시 파일이 릴리스 커밋에 쓸려 들어갑니다
- `.github/scripts/`와 워크플로우는 `npx projectops` 업데이트 시 덮어써집니다. 설정은 코드 기본값이 아니라 `version.yml`에 둡니다
- `version.yml`의 `deploy:` 블록은 런타임에 아무도 읽지 않습니다. `npx projectops` 재실행 때만 쓰이는 메모입니다
