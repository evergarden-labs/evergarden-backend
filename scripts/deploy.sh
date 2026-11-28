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

echo "2. 최신 이미지 받기 + 재기동"
# GitHub Actions의 호스팅 러너가 이미 빌드해서 GHCR에 올려둔 이미지를 받아오기만
# 한다 — EC2가 직접 빌드하지 않는다(그래야 비밀값이 이미지에 안 박히고, EC2
# CPU도 안 쓴다).
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d

echo "3. 기동 확인 (최대 30초 대기)"
# app 컨테이너는 포트를 호스트에 열지 않는다(Caddy를 통해서만 들어온다) —
# 그래서 localhost가 아니라 실제 도메인으로 Caddy → app까지 통째로 확인한다
for _ in $(seq 1 15); do
    if curl -sf https://evergarden-api.space/actuator/health > /dev/null; then
        echo "배포 완료 — 헬스체크 통과"
        echo "4. 옛 이미지 정리 (디스크 공간이 작아 매 배포마다 치워야 한다)"
        # 지금 떠 있는 컨테이너가 쓰는 이미지는 안 지워진다. 실패했을 때는 이 단계를
        # 건너뛴다 — 롤백할 때 쓸 이전 이미지를 건드리지 않기 위해서다. 더 이상
        # EC2에서 직접 빌드하지 않으니 builder prune은 필요 없다.
        docker image prune -af
        exit 0
    fi
    sleep 2
done

echo "헬스체크 실패 — docker compose -f docker-compose.prod.yml logs app 로 확인하세요" >&2
exit 1
