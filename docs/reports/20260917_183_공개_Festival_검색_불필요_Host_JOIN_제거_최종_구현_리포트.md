# 공개 Festival 검색 불필요 Host JOIN 제거 최종 구현 리포트

## 목적

Issue #183의 공개 Festival 목록·count 검색에서 결과 생성, 필터, 정렬에 직접 쓰이지 않던 Host INNER JOIN을 제거해 쿼리 계획을 단순화한다. 기존 검색 결과, 정렬, count, Host DTO 응답 계약은 유지한다.

- Issue: [#183](https://github.com/greedy-team/festa-backend/issues/183)
- PR: [#185](https://github.com/greedy-team/festa-backend/pull/185)
- merge commit: `59decc17b5fe59ee035e0a6280c97154c1e6281f`

## 최종 변경 내용

- `FestivalRepository`의 공개 목록·count 쿼리에서 Host JOIN을 제거했다.
- 기존 INNER JOIN이 보장하던 Host 없는 Festival 제외는 `f.host IS NOT NULL`로 명시하고, 주최 필터는 `f.host.id` 경로로 유지했다.
- `FestivalService`는 현재 페이지의 Host ID를 모아 한 번에 preload한 뒤 DTO로 변환하도록 해, JOIN FETCH 제거 뒤 발생할 수 있는 N+1을 방지했다.
- PostgreSQL 통합 테스트로 기존 INNER JOIN 쿼리와 목록 ID·count 동치 및 생성 SQL의 Host JOIN 부재를 검증했고, 서비스 테스트로 Host preload의 3-statement 계약을 고정했다.

## 설계 이유

Host는 목록 응답 DTO에 필요하지만, 목록·count의 결과 집합을 만들기 위해 매 행과 JOIN할 필요는 없다. 따라서 검색 쿼리에서는 불필요한 JOIN을 제거하고, DTO 변환에 필요한 Host만 현재 페이지 범위에서 별도 preload했다. 이 방식은 Host 없는 Festival의 기존 제외 의미와 응답 계약을 보존하면서 검색 쿼리의 join-filter 비용을 줄인다.

## 리뷰 반영 핵심

- API 하나를 위해 전역 지연 로딩 동작을 바꾸는 `@BatchSize`는 제거하고, 영향 범위가 명확한 페이지 단위 preload만 남겼다.
- `f.host IS NOT NULL`과 프록시 ID 접근 전제를 코드 주석과 회귀 테스트로 드러내어, Host 없는 Festival 처리와 N+1 방지 근거를 유지했다.
- 기존 JOIN 기반 결과와의 동치, Host 필터, 특수문자 검색, 정렬·count, Host preload statement 수를 테스트로 보강했다.

## 테스트/검증

이번 문서 작업에서는 테스트를 재실행하지 않았다. PR #185에서 다음 검증이 완료됐다.

- `FestivalPublishedRowsPostgresIntegrationTest`: PostgreSQL에서 기존 Host INNER JOIN 대비 목록 ID·count 동치와 Host JOIN 미생성 검증
- `FestivalServiceTest`: 현재 페이지 Host preload로 목록·count·preload 총 3개 statement 검증
- `SearchSpacePostgresTest` 20건, `FestivalServiceTest` 51건, `FestivalControllerTest` 15건 통과
- PR CI: 84 suites, 712 tests, 754 runs 성공

## 성능 측정

동일 `festa_perf` fixture 기준 BEFORE 측정은 완료했다.

| 경로 | BEFORE 중앙값 |
| --- | ---: |
| 목록 | 99.669ms |
| count | 93.961ms |

Host preload까지 포함한 엔드포인트 기준 AFTER 측정은 후속 작업에서 수행할 예정이다. 따라서 이 리포트에는 AFTER 수치나 개선율을 확정값으로 기록하지 않는다.
