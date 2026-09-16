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

각 Environment의 Variables에는 서빙 도메인을 등록한다. 비밀이 아니므로 Variable이다.
둘 중 하나라도 없거나 형식이 틀리면 배포는 서버에 닿기 전에 멈춘다.

| Variable | development | production |
| --- | --- | --- |
| `API_DOMAINS` | `dev-api.every-festa.com` | `api.every-festa.com` |
| `PUBLIC_BASE_URL` | `https://dev-api.every-festa.com` | `https://api.every-festa.com` |

표는 운영 서버 전환(#173)이 끝난 뒤의 값이다. 전환 중에는 development가 `api.every-festa.com`을 함께 서빙한다.

`API_DOMAINS`는 Caddy가 인증서를 받아 서빙할 도메인이다. 쉼표 뒤에 공백을 두어 여러 개를 줄 수 있어(`api.every-festa.com, dev-api.every-festa.com`),
도메인을 옮기는 동안에는 한 서버가 옛 도메인과 새 도메인을 함께 서빙한다.
`PUBLIC_BASE_URL`은 배포 마지막 HTTPS 확인의 주소다. 접속은 DNS가 아니라 방금 배포한
`OCI_HOST`로 고정하므로, 도메인이 아직 다른 서버를 가리키면 이 확인은 실패한다.
`PUBLIC_BASE_URL`의 도메인은 `API_DOMAINS` 중 하나여야 하며, 어긋나면 서버에 닿기 전에 멈춘다.
두 값을 바꿀 때는 `API_DOMAINS`에 도메인을 먼저 더하고 나서 `PUBLIC_BASE_URL`을 옮긴다.

배포 순서는 Environment Variable `DEPLOY_STRATEGY`가 정한다. 값이 없으면 순차다.

| Variable | development | production |
| --- | --- | --- |
| `DEPLOY_STRATEGY` | (없음 — 순차) | `overlap` |

- **`overlap`**: 새 색을 먼저 띄우고 확인한 뒤 트래픽을 옮기고 옛 색을 멈춘다. 끊김이 없다.
- **없음(순차)**: 옛 색을 먼저 멈추고 새 색을 띄운다. 1GB 개발 서버에 JVM을 하나만 둔다.
  기본값이 순차라 Variable을 빠뜨려도 메모리가 두 배로 들지 않는다.

## blue/green 전환

앱은 `app-blue`와 `app-green` 두 서비스다. 설정 한 벌을 공유하고 이미지와 아래 둘만 다르다.

| | app-blue | app-green |
| --- | --- | --- |
| 루프백 포트 | `127.0.0.1:8081` | `127.0.0.1:8082` |
| 파일 로그 | `festa-blue.log` | `festa-green.log` |

**트래픽을 받는 색은 서버의 `upstream/active.caddy` 한 줄이 정한다**(`reverse_proxy app-blue:8080`).
CD는 이 파일을 덮지 않는다 — 배포 때만 바꾸고 `caddy reload`로 전환한다. 서버가 재부팅돼
Caddy가 다시 떠도 같은 색을 본다. 이 파일이 없으면 CD가 첫 전환으로 보고 만든다.

배포는 이 순서다.

1. `upstream/active.caddy`를 읽어 지금 색을 알고, 반대 색을 이번 대상으로 고른다
2. (순차) 옛 색을 멈춘다
3. 새 색을 띄우고 **두 번 확인한다** — 서버의 루프백 포트로 한 번, Caddy 컨테이너 안에서
   `app-<색>:8080`으로 한 번. 뒤쪽은 Caddy가 실제로 붙을 이름과 네트워크를 확인한다
4. `upstream/active.caddy`를 새 색으로 바꾸고 `caddy reload`
5. (겹침) 옛 색을 멈춘다. **지우지 않는다** — `switch-back.sh`가 그 컨테이너를 되살린다
6. 외부 HTTPS 확인 → 정상 이미지 기록과 오래된 이미지 정리

실패는 이렇게 갈린다.

- **새 색 기동·확인 실패**: 로그를 남기고 새 색을 멈춘다. 겹침은 Caddy를 건드리기 전이라
  트래픽이 옛 색 그대로다. 순차는 멈춰 둔 옛 색을 직전 정상 이미지로 되살린다
- **reload 실패**: Caddy가 기존 설정을 유지한다. `active.caddy`를 되돌리고 새 색을 멈춘다
- **HTTPS 확인 실패**: 되돌리지 않는다. TLS·443·DNS 문제는 색을 바꿔도 고쳐지지 않는다

**수동 되돌리기**는 서버에서 `cd /opt/festa && bash switch-back.sh`다. 스크립트는 CD가 배포마다
`compose.yaml`·`Caddyfile`과 함께 배포 경로로 보낸다. 옛 색을 되살려 확인한 뒤
트래픽을 옮기고 지금 색을 멈춘다. 옛 색 확인이 실패하면 전환하지 않는다.
**운영(A1, 겹침) 전용이다.** 옛 색을 먼저 띄우므로 1GB 개발 서버(E2)에서는 JVM이 둘 뜨고,
개발 서버 기동(약 2분)이 확인 대기(약 90초)보다 길어 실패한다. 개발 서버는 직전 커밋을 다시 배포해서 되돌린다.
Actions 배포가 도는 중에는 실행하지 않는다.
되돌릴 수 있는 것은 **직전 버전 하나**다 — 다음 배포가 그 색 자리를 새 이미지로 다시 만든다.

**첫 전환**(기존 `app` → `app-blue`)은 한 번만 일어난다. `active.caddy`가 없으면 CD가 기존 `app`을
현재 색으로 취급해 그쪽으로 트래픽을 유지한 채 blue를 띄운다. Caddy는 마운트가 바뀌어 이때 한 번
재생성되고(443이 몇 초, 인증서는 `caddy-data` 볼륨에 남는다), 기존 `app` 컨테이너는 HTTPS 확인까지
통과한 뒤 지운다. develop 머지로 E2가 먼저 겪고 릴리스로 A1이 뒤따른다.
기존 `app`은 compose.yaml에 없어 라벨로 찾는데, **이 배포 경로의 compose 프로젝트로 좁혀서** 찾는다
(`docker compose config`의 `name`). 같은 서버의 다른 프로젝트에 서비스 이름이 `app`인 컨테이너가 있어도
멈추거나 지우지 않는다. 프로젝트 이름을 못 읽으면 배포는 컨테이너와 트래픽을 바꾸기 전에 멈춘다
(설정 파일은 앞 스텝에서 이미 전송된 뒤다).

원격 스크립트는 `ssh ... bash -s`로 **stdin에서 읽힌다.** `docker compose exec`는 `-T`여도 stdin을
컨테이너로 넘기므로, 원격 스크립트 안의 `exec`에는 반드시 `</dev/null`을 붙인다. 없으면 `exec`가 남은
스크립트를 먹어 전환 없이 성공(0)으로 끝난다. 파일로 실행하는 테스트·리허설로는 드러나지 않는다.

서버에서 상태를 볼 때 컨테이너 이름은 `festa-app-blue-1`·`festa-app-green-1`이다.
`festa-app-1`을 보는 기존 명령(기동 시간·자원 측정 등)은 색 이름으로 바꿔야 한다.

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
