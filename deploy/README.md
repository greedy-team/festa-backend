# 비공개 GHCR 배포

## 최초 설정

각 배포용 GitHub Environment에 아래 Secrets를 등록한다. 앱 환경변수가 아니므로
`app.env`나 Compose의 `environment`에는 넣지 않는다.

| Secret | 값 |
| --- | --- |
| `GHCR_USERNAME` | 다운로드용 PAT를 발급한 GitHub 계정명 |
| `GHCR_READ_TOKEN` | 패키지 읽기 권한이 있는 계정의 PAT **classic**, `read:packages` 권한 |

조직에서 SSO를 사용하면 PAT에 조직 접근도 승인한다. 만료 전에 Secret을 교체한다.
PAT 값은 저장소·채팅·명령 인자에 넣지 않고 GitHub Settings의 Secret 입력란에서 등록한다.

러너의 업로드는 자동 발급 `GITHUB_TOKEN`의 `packages: write`를 사용한다.
이미지는 `ghcr.io/greedy-team/festa-backend`에 생성하며 최초 가시성은 private이다.
기존 패키지가 있다면 private 여부, 저장소 연결 및 Actions 쓰기 권한을 먼저 확인한다.
조직의 패키지 생성 정책이 허용되어야 하며, PAT 계정에도 해당 패키지 읽기 권한이 있어야 한다.
공개 저장소와 연결해도 패키지를 public으로 전환하지 않는다.

서버는 SSH 표준 입력으로 PAT를 받아 임시 Docker 설정으로 로그인하고 pull한다.
성공·실패 모두 임시 인증 디렉터리를 삭제하며 서버의 기존 Docker 설정은 변경하지 않는다.
로그인 또는 pull 실패 시 서버 설정 전송·컨테이너 교체 전에 배포가 종료된다.

## 이미지와 롤백

- 배포 태그: `<commit SHA>-<amd64|arm64>-<run ID>-<run attempt>`.
  같은 커밋을 다른 환경에 배포하거나 재실행해도 기존 태그를 덮어쓰지 않는다.
- 의존성·로더·앱 코드를 별도 레이어로 빌드한다. 빌드 캐시는 아키텍처별
  `buildcache-amd64`, `buildcache-arm64` 태그로 보관하며 실행에 사용하지 않는다.
- pull이 끝나면 로컬 이미지 존재를 확인하고 Compose를 재기동한다.
  앱의 `pull_policy: never`는 롤백 시에도 레지스트리 접근을 막는다.
- `.last-successful-image`에는 전체 이미지 이름과 태그를 기록한다.
  첫 전환에서는 기존 `festa-backend:<SHA>` 기록도 그대로 롤백에 사용한다.
- 앱 헬스체크 실패 시 로그를 남기고 직전 정상 이미지로 복구한다.
  롤백 자체가 실패하면 `.deployment-in-progress`를 남기고 작업은 실패한다.
- 외부 HTTPS 확인까지 성공해야 정상 이미지 기록을 바꾼다. HTTPS 실패 때는
  기존 정책대로 앱을 되돌리지 않고 이전 정상 기록과 배포 중 기록을 보존한다.
- 서버에서는 현재·직전 정상 이미지를 보존하고 같은 저장소의 오래된 태그와
  예전 `festa-backend` 태그를 정리한다. 다른 서비스 이미지는 정리하지 않는다.
- GHCR의 배포 버전은 자동 삭제하지 않는다. 서버 정리와 별개이며, 수동 정리 시
  두 환경의 현재·직전 정상 태그와 아키텍처별 빌드 캐시를 보존한다.

롤백 대상은 이미지뿐이다. DB 스키마, `app.env`, Compose 설정은 되돌리지 않는다.

## 검증

`python3 -B -m unittest discover -s deploy -p 'test_*.py' -v`는 실제 워크플로우의
원격 Bash에 Docker/HTTP 대역을 붙여 성공·실패·첫 전환·롤백·정리·인증 삭제를 검증한다.
실제 이미지 빌드와 서버 동작은 아래 순서로 별도 확인한다.

1. Secrets와 패키지 접근 권한을 준비한 뒤 development 배포를 실행한다.
2. 현재 컨테이너 이미지와 `.last-successful-image`가 새 태그인지 확인한다.
3. 운영 서버가 아닌 격리된 Compose 프로젝트에서 헬스체크에 실패하는 이미지를
   배포해 직전 정상 이미지로 복구되는지 확인한다. 실제 서버에서 장애를 강제로 만들지 않는다.
4. 변경 없는 의존성으로 다음 배포를 실행해 push/pull의 레이어 재사용을 확인한다.
5. Actions의 전체 배포 시간과 빌드·업로드·다운로드·재기동 시간을 비교한다.
   기존 이슈의 전송 34초·적재 23초와 비교하되 새 업로드 시간도 포함한다.
   최초 배포는 캐시가 없으므로 반복 배포와 구분한다. 30초 단축은 측정 전 예상치다.

인증 근거: [GitHub Container registry 문서](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry).
레이어 구성: [Spring Boot Dockerfiles 문서](https://docs.spring.io/spring-boot/reference/packaging/container-images/dockerfiles.html).
