# Changelog

**현재 버전:** 0.0.10  
**마지막 업데이트:** 2026-09-18T14:31:35Z  

---

## [0.0.10] - 2026-09-18

**PR:** #222  

**새 기능**
- optimize upcoming festival query
- add verified query indexes

**문서**
- 5차 데모데이 README 피드백 반영
- 공개 API BEFORE/AFTER 최종 구현 리포트

**기타**
- Merge pull request from greedy-team/chore_219_관측_설정이_배포로_반영되지_않고_에러율_패널이_빈다
- Merge pull request from greedy-team/docs_5차_데모데이_github_서비스_레포_readme_작성
- chore : 관측 설정이 배포로 반영되게 하고 에러율 패널을 고친다
- Merge branch 'develop' into docs_5차_데모데이_github_서비스_레포_readme_작성
- Merge pull request from greedy-team/docs_192_public_api_before_after_final_report
- Merge pull request from greedy-team/docs_193_api_tps_측정_완료_리포트
- Merge pull request from greedy-team/feat_154_조회_쿼리_인덱스_검증
- docs : README 디자인과 주요 기능 이미지 적용
- docs : README 로고와 팀원 사진 연결
- docs : GitHub 서비스 레포 README.md 작성
- docs : API TPS 측정 완료 리포트

---

## [0.0.9] - 2026-09-18

**PR:** #217  

**기타**
- Merge pull request from greedy-team/chore_215_앱_메트릭을_a1_prometheus가_직접_스크랩하게_한다
- chore : 배포가 Prometheus에 설정을 다시 읽힌다
- chore : 패널 제목에서 설명을 빼고 description으로 옮긴다
- chore : 커뮤니티 대시보드의 N/A를 고치고 보이는 제목을 한국어로 바꾼다
- chore : Prometheus가 두 색을 스크랩하고 부하 테스트 대시보드를 둔다
- chore : 앱에 /actuator/prometheus를 노출한다

---

## [0.0.8] - 2026-09-18

**PR:** #213  

**버그 수정**
- harden performance fixture validation

**개선**
- 공개 축제 검색 host join을 제거한다

**문서**
- k6 공개 API 부하 테스트 완료 리포트
- 공개 Festival 검색 Host JOIN 제거 완료 리포트

**기타**
- Merge pull request from greedy-team/chore_211_관측_스택_비밀을_cd가_app_env에_쓰게_한다
- chore : 관측 스택 비밀을 CD가 app.env에 쓰게 한다
- Merge pull request from greedy-team/chore_209_caddy에서_actuator_prometheus를_404로_차단한다
- Merge pull request from greedy-team/chore_205_a1에_prometheus_grafana_exporter_스택을_올린다_1단계_앱_제외
- chore : 모니터링 설정을 CD가 전송하고 job 이름 계약을 고정
- chore : PostgreSQL 대시보드 추가 (12485, 프로비저닝용으로 가공)
- chore : Caddyfile 테스트를 컨테이너 안에서 끝내도록 슬림화
- chore : Caddyfile 테스트가 사이트 매칭 실패를 통과로 보지 않게 고침
- chore : Caddy에서 /actuator/prometheus를 404로 차단
- chore : A1에 Prometheus·Grafana·exporter 스택 추가 (1단계, 앱 제외)
- Merge pull request from greedy-team/feat_192_154_규모_fixture의_공개_api_before_after_부하_측정을_수행한다
- feat : 공개 API AFTER 부하 측정 검증 보강
- feat : 규모 fixture 공개 API 전후 부하 측정 도구 보강
- Merge pull request from greedy-team/docs_188_k6_public_api_load_test_final_report
- Merge pull request from greedy-team/docs_183_public_festival_host_join_final_report
- Merge pull request from greedy-team/feat_188_공개_api_부하_테스트
- Merge pull request from greedy-team/feat_183_공개_festival_검색_불필요_host_join_제거
- 공개 축제 조회 회귀 검증을 보강한다
- 공개 축제 목록 검증 책임을 분리한다
- 공개 축제 목록 조회 검증을 보강한다
- fix : 공개 API 부하 테스트 최신 리뷰를 반영한다
- fix : 공개 API 부하 테스트 실행 경로를 보완한다
- fix : 공개 API 부하 테스트 리뷰를 반영한다
- feat : k6 기반 공개 API 부하 테스트 도구 추가
- Merge remote-tracking branch 'origin/develop' into feat_183_공개_festival_검색_불필요_host_join_제거

---

## [0.0.7] - 2026-09-17

**PR:** #197  

**기타**
- Merge pull request from greedy-team/chore_195_v0_0_6_릴리스에서_빠진_changelog와_develop_버전을_맞춘다
- chore : v0.0.6 릴리스 기록과 develop 버전을 맞춘다
- Merge pull request from greedy-team/chore_180_운영_배포를_blue_green으로_바꿔_배포_중_끊김을_없앤다
- fix : 새 Caddyfile을 Caddy 재생성 직전에 적용한다
- fix : blue/green 배포 스크립트의 결함 4건을 고친다
- docs : blue/green 전환 절차와 DB 스키마 호환 규칙을 적는다
- chore : 직전 색으로 되돌리는 switch-back 스크립트를 추가한다
- chore : 운영 배포를 blue/green 색 전환으로 바꾼다

---

## [0.0.6] - 2026-09-17

**PR:** #194  

**새로운 기능**
- 백엔드 메트릭을 Grafana Cloud로 push하고 외부 헬스 감시를 붙인다 (#161)

**개선**
- 릴리스를 보호 브랜치 준비 PR 방식으로 전환한다 (#179)

**기타**
- v0.0.5 릴리스 기록과 develop 버전을 복구한다 (#178)
- Testcontainers PostgreSQL 테스트 커넥션 풀을 제한한다 (#187)

---

## [0.0.5] - 2026-09-14

**PR:** #175  

**개선**
- 약어 검색을 단일 쿼리로 통합한다

**기타**
- Merge pull request from greedy-team/chore_99_a1_프로덕션_배포_서버_온보딩
- chore : 운영 서버 전환 참조를 후속 이슈 으로 옮긴다
- chore : PUBLIC_BASE_URL 도메인이 API_DOMAINS에 없으면 서버에 닿기 전에 막는다
- chore : API_DOMAINS 쉼표 형식과 PUBLIC_BASE_URL 형식을 서버에 닿기 전에 막는다
- chore : 환경별 서빙 도메인 변수 절차를 문서에 반영한다
- chore : HTTPS 서빙 확인을 DNS가 아니라 배포 대상 서버로 고정한다
- chore : Caddy 서빙 도메인을 환경별 API_DOMAINS 변수로 받는다
- chore : 운영 프로파일에 공개 프론트 CORS 허용 출처를 둔다
- Merge pull request from greedy-team/chore_165_이미지를_통째로_전송하지_말고_레지스트리_경유로_바꾼다
- docs : GHCR 인증 설정과 이미지 검증 절차를 적는다
- chore : 배포 스크립트의 실패 경로를 CI에서 검증한다
- chore : 배포 이미지를 GHCR 경유로 전달한다
- Merge pull request from greedy-team/chore_164_이미지_빌드에서_매번_새로_받는_의존성을_러너_캐시로_옮긴다
- chore : 배포 JAR 빌드를 러너 Gradle 캐시로 옮긴다
- Merge pull request from greedy-team/feat_146_host_통용_약어_검색_지원
- docs : 통합검색 Swagger에 약어 검색 범위를 명시한다
- Merge pull request from greedy-team/chore_156_문서_전용_배포와_쓰이지_않는_qemu_설치를_걷어낸다
- chore : 문서 전용 배포를 제외하고 QEMU를 ARM 빌드에만 설정
- Merge pull request from greedy-team/chore_158_한_번도_쓰이지_않은_의존성_4개와_중복_선언_1개를_걷어낸다
- Merge branch 'develop' into chore_158_한_번도_쓰이지_않은_의존성_4개와_중복_선언_1개를_걷어낸다
- 약어 검색 계약 검증을 보강한다
- fix : 약어 검색 Host 정렬을 유지한다
- fix : 약어 검색 축제 정렬을 유지한다
- test : 목록 외 Host 약어 비추론을 검증한다
- refactor : HostAlias DB 설계를 제거한다
- feat : Host 통용 약어를 통합검색에 적용
- chore : AGENTS.md 기술 스택에서 Validation 표기를 지운다
- feat : Host 약어 검색을 상수 매핑으로 단순화
- Merge pull request from greedy-team/chore_157_쓰지_않는_google_oauth_클라이언트_설정을_걷어낸다
- chore : 한 번도 쓰이지 않은 의존성 4개와 중복 선언 1개를 걷어낸다
- chore : 쓰지 않는 Google OAuth 클라이언트 설정을 걷어낸다
- Merge pull request from greedy-team/fix_152_festival_search_start_date_order
- fix : Festival 검색 결과를 개최일 최신순으로 정렬
- Merge pull request from greedy-team/feat_147_검색어와_db_값의_공백_차이_무시
- Merge pull request from greedy-team/fix_143_매핑_없는_경로가_404가_아니라_500이라_스캐너_트래픽이_로그를_20배로_부풀린다
- feat : 검색어 공백 정규화를 공통 Java 계층으로 이동
- feat : 검색어와 DB 값의 띄어쓰기 차이를 무시한다
- fix : 매핑 없는 경로를 NoResourceFoundException으로 잡아 404로 응답하고 WARN으로 남긴다
- Merge pull request from greedy-team/feat_138_최신순_정렬_기준을_발행_시각에서_개최일로_바꾼다
- Merge pull request from greedy-team/feat_139_축제_상세_응답에_축제_인스타그램_url을_낸다
- feat : 축제 상세 응답에 축제 인스타그램 URL을 낸다
- feat : 최신순 정렬 기준을 발행 시각에서 개최일로 바꾼다
- docs : 가드와 계약 테스트 보강 최종 구현 리포트를 추가한다
- docs : 관리자 쓰기 엔드포인트 HTTP 계약 고정 최종 구현 리포트를 추가한다
- Merge pull request from greedy-team/chore_134_관리자_쓰기_엔드포인트의_http_계약을_고정한다
- Merge pull request from greedy-team/chore_132_실행된_적_없는_가드와_프로덕션과_다른_매퍼로_검증하는_계약_테스트를_메운다
- docs : 엔티티 공통 픽스처 최종 구현 리포트 작성
- Merge pull request from greedy-team/refactor_130_엔티티_생성_코드를_공통_픽스처로_묶는다
- chore : 관리자 쓰기 엔드포인트의 HTTP 계약을 고정한다
- chore : 실행된 적 없는 가드와 계약 테스트의 매퍼를 메운다
- refactor : 엔티티 생성 코드를 공통 픽스처로 묶는다
- Merge pull request from greedy-team/refactor_98_q_파라미터의_like_이스케이프_처리를_통일
- refactor : 검색어 정규화 정책을 공통화한다
- fix : LIKE 이스케이프의 null 계약을 명시한다
- Merge remote-tracking branch 'origin/develop' into refactor_98_q_파라미터의_like_이스케이프_처리를_통일
- docs : 기록되지 않던 실패 지점 로그 최종 구현 리포트 추가
- Merge pull request from greedy-team/feat_117_기록되지_않는_실패_지점에_로그_남기기
- Merge remote-tracking branch 'origin/develop' into feat_117_기록되지_않는_실패_지점에_로그_남기기
- fix : 관리 작업 로그를 커밋된 뒤에 남긴다

---

## [0.0.4] - 2026-08-03

**PR:** #11  

**기타**
- Merge pull request from greedy-team/chore_9_agents_md_claude_md_작성
- Merge pull request from greedy-team/docs_7_projectops_마이그레이션_로그_제거
- chore : 작업 원칙·커맨드 7종 이식
- docs : TEAM-CONVENTIONS.md 커밋
- chore : AGENTS.md·CLAUDE.md 작성
- docs : projectops 마이그레이션 로그 제거

---

## [0.0.3] - 2026-08-02

**PR:** #6  

**기타**
- Merge pull request from greedy-team/chore_4_프론트와_자동화_설정_정합
- chore : 프론트와 자동화 설정 정합

---

## [0.0.2] - 2026-08-02

**PR:** #3  

**기타**
- Merge pull request from greedy-team/docs_1_readme_프로젝트_개요_및_실행_방법_추가
- README 프로젝트 개요 및 실행 방법 추가 : docs : 기술 스택·로컬 실행·협업 규칙 섹션 추가

---

