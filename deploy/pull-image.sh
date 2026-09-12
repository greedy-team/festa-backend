#!/usr/bin/env bash
set -Eeuo pipefail

# PAT는 표준 입력에서 읽는다. 서버의 기존 Docker 인증 설정은 건드리지 않는다.
config_dir="$(mktemp -d)"
trap 'rm -rf -- "$config_dir"' EXIT
docker --config "$config_dir" login ghcr.io --username "$1" --password-stdin
docker --config "$config_dir" pull "$2"
