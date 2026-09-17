#!/usr/bin/env bash
# 수동 되돌리기 — 트래픽을 직전 색으로 돌린다. 서버에서 배포 경로에 두고 실행한다.
#
#   cd /opt/festa && bash switch-back.sh
#
# 되돌릴 수 있는 것은 직전 버전 하나다. 옛 색 컨테이너는 배포가 멈추기만 하고 지우지 않으므로,
# 그 컨테이너가 남아 있는 동안에만 동작한다(다음 배포가 그 자리를 새 이미지로 다시 만든다).
# 옛 색 확인이 실패하면 전환하지 않는다 — 지금 뜬 색이 트래픽을 그대로 받는다.
#
# 운영(A1, 겹침) 전용이다. 옛 색을 먼저 띄운 뒤 지금 색을 멈추므로 1GB 개발 서버(E2, 순차)에서는
# JVM이 둘 뜨고, 개발 서버 기동(약 2분)이 아래 확인 대기(약 90초)보다 길어 확인도 실패한다.
# 개발 서버는 직전 커밋을 다시 배포해서 되돌린다. Actions 배포가 도는 중에는 실행하지 않는다.
set -Eeuo pipefail

cd "${OCI_DEPLOY_PATH:-/opt/festa}"

if [[ ! -s upstream/active.caddy ]]; then
  echo "upstream/active.caddy가 없습니다. 이 서버는 아직 색 전환 배포를 한 적이 없습니다." >&2
  exit 1
fi

if grep -q 'app-blue' upstream/active.caddy; then
  current_service=app-blue
  target_service=app-green
  target_port=8082
elif grep -q 'app-green' upstream/active.caddy; then
  current_service=app-green
  target_service=app-blue
  target_port=8081
else
  echo "지금 트래픽을 받는 것이 색 서비스가 아닙니다(첫 전환 전). 되돌릴 대상이 없습니다." >&2
  exit 1
fi

# compose.yaml이 APP_IMAGE를 필수로 선언해서 값이 없으면 어떤 하위 명령도 그 자리에서 죽는다.
# 여기서는 파싱용일 뿐이다 — start는 이미 만들어진 컨테이너를 그 이미지 그대로 되살린다.
image_for_compose="$(cat .last-successful-image 2>/dev/null || true)"
if [[ -z "$image_for_compose" ]]; then
  echo ".last-successful-image가 없습니다. 배포 기록 없이는 compose를 부를 수 없습니다." >&2
  exit 1
fi
run_compose() { APP_IMAGE="$image_for_compose" docker compose --env-file app.env "$@"; }

target_container="$(run_compose ps -a -q "$target_service")"
if [[ -z "$target_container" ]]; then
  echo "$target_service 컨테이너가 없습니다. 되돌릴 수 있는 것은 직전 버전 하나뿐입니다." >&2
  exit 1
fi

echo "$target_service 를 되살립니다."
# start는 컨테이너를 띄우기만 하고 기동을 기다리지 않는다. 앱이 뜨는 동안 포트는 열려 있어도
# 응답이 없어 curl이 52(empty reply)로 떨어지므로, 그 오류에도 재시도하도록 --retry-all-errors를 둔다.
if ! run_compose start "$target_service" \
  || ! curl --fail --silent --show-error --retry 30 --retry-delay 3 --retry-all-errors --retry-connrefused \
    "http://localhost:$target_port/actuator/health" \
  || ! run_compose exec -T caddy \
    wget -q -O /dev/null "http://$target_service:8080/actuator/health" </dev/null; then
  echo "$target_service 확인 실패. 전환하지 않습니다 — $current_service 가 계속 트래픽을 받습니다." >&2
  run_compose logs --tail=200 --no-color "$target_service" >&2 || true
  run_compose stop --timeout 40 "$target_service" || true
  exit 1
fi

cp upstream/active.caddy .active.caddy.previous
printf 'reverse_proxy %s:8080\n' "$target_service" > upstream/active.caddy
# exec는 -T여도 stdin을 넘긴다. 이 스크립트를 파이프로 실행(ssh ... bash -s)해도 남은 줄을 먹지 않게 막는다.
if ! run_compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile </dev/null; then
  echo "Caddy reload 실패. 트래픽 대상을 되돌립니다." >&2
  mv .active.caddy.previous upstream/active.caddy
  run_compose stop --timeout 40 "$target_service" || true
  exit 1
fi
rm -f .active.caddy.previous

run_compose stop --timeout 40 "$current_service" || true

restored_image="$(docker inspect --format '{{.Config.Image}}' "$target_container")"
printf '%s\n' "$restored_image" > .last-successful-image
echo "되돌렸습니다: $current_service → $target_service ($restored_image)"
