#!/usr/bin/env bash
# EC2에서 직접 실행하는 운영 배포 스크립트.
#   cd ~/evergarden-backend && ./scripts/deploy.sh
#
# 중간에 하나라도 실패하면 바로 멈춘다(set -e) — 절반만 배포된 상태로
# 조용히 넘어가지 않는다.
set -euo pipefail

cd "$(dirname "$0")/.."

echo "1. 최신 코드 받기"
git pull origin main

echo "2. 이미지 빌드 + 재기동"
docker compose -f docker-compose.prod.yml up -d --build

echo "3. 기동 확인 (최대 30초 대기)"
# app 컨테이너는 포트를 호스트에 열지 않는다(Caddy를 통해서만 들어온다) —
# 그래서 localhost가 아니라 실제 도메인으로 Caddy → app까지 통째로 확인한다
for _ in $(seq 1 15); do
    if curl -sf https://evergarden-api.space/actuator/health > /dev/null; then
        echo "배포 완료 — 헬스체크 통과"
        exit 0
    fi
    sleep 2
done

echo "헬스체크 실패 — docker compose -f docker-compose.prod.yml logs app 로 확인하세요" >&2
exit 1
