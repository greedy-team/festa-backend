# #177 릴리스 워크플로우 전환

기준: [프론트 PR #199](https://github.com/greedy-team/festa-frontend/pull/199)의
준비 PR 방식. 일반 협업 규칙은 [TEAM-CONVENTIONS.md](../TEAM-CONVENTIONS.md)를 따른다.

## 반영 내용

`develop → main` 릴리스 PR을 열면 `release-prep-<PR 번호>`에서 버전·CHANGELOG·README를
준비하고 `develop` 대상 PR을 만든다. 검사 통과 후 준비 PR과 릴리스 PR을 차례로
병합한다. 이 워크플로우는 `main`·`develop` 직접 push나 `--admin`을 사용하지 않는다.
버전 태그는 `version.yml`이 바뀐 main push의 SHA에 생성한다.

백엔드에 필요한 차이:

- 2026-09-14 조회 기준 develop ruleset은 `빌드 검증`을 요구하며 main에는 ruleset이 없다.
  필수 검사 목록이 없을 때에도 `빌드 검증`의 성공을 기다린다. 실패·취소·건너뜀·시간 초과는
  병합을 중단한다. 준비 PR 생성은 후속 CI를 실행할 수 있도록 PAT를 사용한다.
- Spring 버전 스크립트에 `build.gradle.kts` 읽기·갱신을 추가했다. Groovy DSL과
  바로 아래 모듈 갱신도 유지한다. 실제 릴리스 준비 시 Gradle 버전이 함께 바뀐다.
- 검사 대기 전에 head SHA를 읽어 병합 시 일치 여부를 검증한다. 태그용 체크아웃도
  이동할 수 있는 main HEAD 대신 push 이벤트의 SHA를 사용한다.
- README 갱신은 버전 증가 이후 같은 준비 커밋에서 수행한다. 따라서 정상 릴리스에서
  별도 README 워크플로우가 버전 증가 전 값을 먼저 읽는 문제를 피한다.

기존 준비 PR이 열려 있으면 그 PR을 재사용한다. 이미 develop에 준비가 병합되었다면
CHANGELOG와 README 버전을 확인하고 릴리스 PR 병합 단계로 이어간다.

## 최초 전환과 운영 검증

1. 선행 [#176](https://github.com/greedy-team/festa-backend/issues/176)을 먼저 develop에
   병합해 v0.0.5 버전과 CHANGELOG를 맞춘다. 이번 구현 시점에는 아직 OPEN이었다.
2. #177 변경을 develop 대상 PR로 검증·병합한다.
3. 첫 `develop → main` PR의 `pull_request_target`은 main의 구형 워크플로우를 실행한다.
   새 파일이 develop에 있다는 것만으로 자동 전환되지 않는다. 이 전환 PR은 구형 실행을
   중단하고 CI 및 버전 정합성을 확인한 뒤 일반 PR 병합으로 main에 반영해야 한다.
   구형 워크플로우의 재실행이나 보호 규칙 완화로 해결하지 않는다.
4. main 병합은 A1 운영 배포를 발생시킨다. 배포 가능한 시점에 전환과 다음 릴리스를 진행한다.
5. 새 워크플로우가 main에 반영된 다음 릴리스에서 준비 PR 생성 → `빌드 검증` 성공 →
   develop 병합 → 갱신된 릴리스 PR의 검사 성공 → main 병합 → 태그 SHA →
   `PROJECT-SPRING-CD` production 성공 및 헬스체크를 확인한다.

로컬에서는 실제 PR 생성·병합·태그 push·운영 배포를 실행하지 않았다.
위 종단 검증은 아직 남아 있다. 기존 README 워크플로우의 별도 push/역병합 로직과
VERSION-CONTROL 안전망은 이번 변경에 포함하지 않았다.

## 검증 및 저장소 비교

```bash
python -B -m unittest discover -s .github/scripts -p 'test_*.py' -v
```

실제 버전 스크립트의 Kotlin/Groovy DSL 증가와 동기화, 워크플로우에서 추출한 Bash 함수의
검사 성공·지연·필수 규칙 없는 main·실패·취소·건너뜀·미등록·API 오류를 검증한다.
테스트는 임시 디렉터리와 gh 대역을 사용하며 Bash·jq가 필요하다. Spring CI에 연결했다.
GitHub Actions 문법은 actionlint로 별도 검증했다.

2026-09-14 원격 develop 비교에서 프론트만 새 릴리스 흐름이고 백엔드와 크롤러의
릴리스 워크플로우 blob은 `8c88ba14b25706421b95637d26a7f6fd3107d6ba`로 같았다.
버전 관리 Python/sh와 CHANGELOG 스크립트는 프론트·백엔드가 동일했다.
크롤러는 현황만 확인했으며 수정하지 않았다. 백엔드 보완 사항을 포함한 세 저장소의
완전한 동기화는 별도 반영이 필요하다. ProjectOps 업데이트가 커스텀 수정을 덮어쓸 수
있으므로 업데이트 후 이 파일과 릴리스 테스트를 확인한다.
