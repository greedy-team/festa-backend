# FESTA

축제 정보 서비스 백엔드입니다.

## 기술 스택

- Spring Boot 4.1 · Java 21
- Gradle 9.6 (Kotlin DSL)
- Spring Data JPA · Spring Security · OAuth2 Client

## 로컬 실행

```bash
./gradlew bootRun     # 애플리케이션 실행
./gradlew build       # 빌드 + 테스트
```

## 협업 규칙

이슈 → 브랜치 → 커밋 → PR → 릴리스가 자동으로 이어집니다. **이슈부터 만드세요** —
이슈를 만들면 봇이 브랜치명과 커밋 메시지를 댓글로 알려줍니다.

- 작업 브랜치는 `develop`에서 분기하고 `develop`으로 PR을 올립니다
- 브랜치명은 `타입_이슈번호_제목슬러그` (예: `docs_1_readme_개요_추가`)
- 배포는 `develop` → `main` PR로만 진행합니다

---

<!-- AUTO-VERSION-SECTION: DO NOT EDIT MANUALLY -->
## 최신 버전 : v0.0.3 (2026-08-02)

[전체 버전 기록 보기](CHANGELOG.md)
