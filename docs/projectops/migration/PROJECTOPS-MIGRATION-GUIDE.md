# ProjectOps 마이그레이션 가이드

이 문서는 projectops 마법사(`npx projectops`)가 **실행을 마칠 때마다 자동으로 기록**하는
마이그레이션 이력입니다. 실행 엔트리는 아래에 시간순으로 append되며, 기존 엔트리는 수정되지 않습니다.

## 사람이 읽는 법

- 각 엔트리의 **확인 체크리스트**가 그 실행 이후 사람이 마무리해야 하는 수동 작업입니다.
- 체크리스트는 실행에서 실제 발생한 것만 나옵니다. 항목이 없으면 할 일이 없다는 뜻입니다.
- 상세 실행 기록(파일별 결정과 치환 전후값)은 엔트리 메타의 `trace_file`(JSONL)과 `log_file`(터미널 원문)에 있습니다.

## AI Agent 해석 가이드라인

각 엔트리의 ```yaml 메타 블록(`# projectops-migration`)을 파싱해 아래 규칙으로 판단하고 조치한다.

| 필드 | 의미 | 권장 조치 |
|------|------|----------|
| `workflows.leftover_old_gen` | 구세대 워크플로우가 현역 가능성으로 보존됨 | 신형과 트리거 중복 여부 검사 → 전환 확인 후 구 파일 삭제 제안 |
| `workflows.skipped_conflict` | 사용자 수정본이 유지됨 (신형 미적용) | 사용자본과 신형 템플릿 diff → 병합 제안 |
| `workflows.replaced_bak` | .bak 백업 후 신형으로 교체됨 | .bak과 신형 비교 → 사용자 커스텀 유실분 복원 검토 후 .bak 정리 |
| `env_applied` | 워크플로우에 적용된 환경값 | 실제 워크플로우 env와 대조 → 드리프트 발견 시 경고 |
| `breaking_traversed` | 이 실행이 통과한 호환성 변경 (조치 방법 전문은 사람용 섹션) | `action_required: true` 항목의 조치 완료 여부 확인 |
| `manual_actions_pending` | 남은 수동 작업 코드 목록 | 비어 있지 않으면 사용자에게 상기 |
| `trace_file` | 파일별 결정과 치환 전후값 JSONL (Layer 2) | "왜 이 파일이 이렇게 됐나"는 파일명으로 grep |
| `log_file` | 터미널 출력 원문 (Layer 3) | 실행 재현, 포렌식 디버깅용 |

- 스키마는 `schema` 필드로 버저닝된다. 모르는 필드는 무시하고, 아는 필드만 사용한다.
- 여러 엔트리가 있으면 **가장 최근 엔트리**가 현재 상태의 기준이다. 과거 엔트리는 이력 참고용.

---

## 2026-08-02 14:19:15 - vnew → v4.2.44 (full)

- 타입: spring / 배포: docker-ssh / publish: 없음
- 워크플로우: 신규/갱신 11개, 유지(unchanged/충돌스킵) 0개

### 확인 체크리스트

- [ ] **새/갱신 CICD가 요구하는 GitHub Secrets 등록 확인** (Settings → Secrets → Actions, `_GITHUB_PAT_TOKEN` 포함)
- [ ] **적용된 배포 환경값 검증** (실제 환경과 다르면 워크플로우 env를 직접 수정):
  - spring `PROJECT_NAME` = `festa-backend`
  - spring `SERVICE_DOMAIN` = `example.com`
  - spring `JAVA_VERSION` = `21`
  - spring `APPLICATION_YML_DIR` = `src/main/resources`
  - spring `VOLUME_HOST_PATH` = `/volume1/projects/festa-backend`
  - spring `SSH_AUTH_METHOD` = `password`
  - spring `VOLUME_CONTAINER_PATH` = `/mnt/festa-backend`
  - spring `APPLICATION_YML_PATH` = `src/main/resources/application.yml`
  - spring `DEPLOY_PORT` = `8080`
- [x] 개발(릴리스 소스) 브랜치 `develop`: 마법사가 생성 및 확인 완료

### AI 메타데이터

```yaml
# projectops-migration (machine-readable)
schema: 1
run_at: "2026-08-02 14:19:15"
template: { from: "new", to: "4.2.44" }
mode: full
types: ["spring"]
options: { deploy: "docker-ssh", publish: [], secret_backup: false, coderabbit: false, changelog_provider: "github-ai", intent: "app" }
branches: { default: "main", deploy: "develop", deploy_branch_created: true }
workflows:
  added: ["PROJECT-COMMON-PROJECTS-SYNC-MANAGER.yaml", "PROJECT-COMMON-QA-ISSUE-CREATION-BOT.yaml", "PROJECT-COMMON-README-VERSION-UPDATE.yaml", "PROJECT-COMMON-RELEASE-CHANGELOG.yaml", "PROJECT-COMMON-SUH-ISSUE-HELPER.yaml", "PROJECT-COMMON-SYNC-ISSUE-LABELS.yaml", "PROJECT-COMMON-VERSION-CONTROL.yaml", "PROJECT-SPRING-NONSTOP-NGINX-CICD.yaml", "PROJECT-SPRING-NONSTOP-TRAEFIK-CICD.yaml", "PROJECT-SPRING-PR-PREVIEW.yaml", "PROJECT-SPRING-SIMPLE-CICD.yaml"]
  replaced_bak: []
  skipped_conflict: []
  template_added: []
  legacy_neutralized: []
  leftover_old_gen: []
env_applied:
  spring:
    PROJECT_NAME: "festa-backend"
    SERVICE_DOMAIN: "example.com"
    JAVA_VERSION: "21"
    APPLICATION_YML_DIR: "src/main/resources"
    VOLUME_HOST_PATH: "/volume1/projects/festa-backend"
    SSH_AUTH_METHOD: "password"
    VOLUME_CONTAINER_PATH: "/mnt/festa-backend"
    APPLICATION_YML_PATH: "src/main/resources/application.yml"
    DEPLOY_PORT: "8080"
breaking_traversed: []
manual_actions_pending: ["register-secrets"]
trace_file: "docs/projectops/migration/20260802_141915_vnew_to_v4.2.44.jsonl"
log_file: "docs/projectops/migration/20260802_141915_vnew_to_v4.2.44.log"
```
