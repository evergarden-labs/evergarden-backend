#!/usr/bin/env bash
# EC2에서 직접 실행하는 운영 배포 스크립트 (Blue/Green).
#   cd ~/evergarden-backend && ./scripts/deploy.sh
#
# 중간에 하나라도 실패하면 바로 멈춘다(set -e) — 절반만 배포된 상태로
# 조용히 넘어가지 않는다.
#
# git pull은 여기서 안 한다 — .github/workflows/deploy.yml이 이 스크립트를
# "실행하기 전에" 별도 단계로 미리 해둔다. 여기서 git pull을 하면 실행 중인
# 이 파일 자신을 덮어쓰게 되는데, 리눅스에서는 실행 중인 파일이 교체돼도
# 이미 열어서 읽던 프로세스는 옛 내용을 계속 읽는다 — 그래서 이 스크립트의
# 뒷부분이 업데이트 전 내용으로 실행되는 사고가 실제로 있었다.
#
# 흐름: 지금 꺼져 있던 색(blue 또는 green)에 새 이미지를 올려서 띄우고,
# 컨테이너 안에서 직접 헬스체크(호스트 포트는 안 연다) → 통과하면 Caddy를
# 그쪽으로 돌리고 → 그제서야 이전 색을 끈다. 그래서 "구버전 종료 → 신버전
# 기동" 사이의 공백이 없고, 헬스체크에서 떨어지면 기존 색이 그대로 운영
# 중이라 서비스가 안 끊긴다.
set -euo pipefail

cd "$(dirname "$0")/.."

COMPOSE="docker compose -f docker-compose.prod.yml"
STATE_FILE=".active-color"

echo "1. 현재 활성 색 확인"
# 상태 파일이 없으면(최초 배포) blue가 활성이라고 가정한다 — Caddyfile도
# 처음엔 app-blue를 가리키도록 맞춰둔다.
ACTIVE=$(cat "$STATE_FILE" 2>/dev/null || echo "blue")
if [ "$ACTIVE" = "blue" ]; then
    IDLE="green"
else
    IDLE="blue"
fi
echo "   활성: app-$ACTIVE / 이번에 새로 띄울 쪽: app-$IDLE"

echo "2. redis·caddy 기동 확인"
$COMPOSE up -d redis caddy

echo "3. app-$IDLE 최신 이미지 받기"
$COMPOSE pull "app-$IDLE"

echo "4. app-$IDLE 새 이미지로 기동"
$COMPOSE up -d "app-$IDLE"

echo "5. app-$IDLE 헬스체크 (컨테이너 내부에서, 최대 30초 대기)"
# 호스트에 포트를 안 열어두니, docker compose exec로 컨테이너 안에서 직접
# curl을 쏜다 — 바깥에서 app-$IDLE에 직접 접근할 수 있는 경로는 없다.
HEALTHY=false
for _ in $(seq 1 15); do
    if $COMPOSE exec -T "app-$IDLE" curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
        HEALTHY=true
        break
    fi
    sleep 2
done

if [ "$HEALTHY" != "true" ]; then
    echo "app-$IDLE 헬스체크 실패 — $COMPOSE logs app-$IDLE 로 확인하세요" >&2
    echo "app-$ACTIVE는 그대로 운영 중이라 서비스는 안 끊겼습니다" >&2
    $COMPOSE stop "app-$IDLE"
    exit 1
fi

echo "6. Caddy를 app-$IDLE로 전환"
sed -i.bak "s/app-$ACTIVE:8080/app-$IDLE:8080/" Caddyfile
rm -f Caddyfile.bak
docker exec evergarden-caddy-prod caddy reload --config /etc/caddy/Caddyfile

echo "7. 전환 확인 (실제 도메인 기준 최종 헬스체크)"
sleep 2
if ! curl -sf https://evergarden-api.space/actuator/health > /dev/null; then
    echo "전환 후 헬스체크 실패 — Caddy를 app-$ACTIVE로 되돌립니다" >&2
    sed -i.bak "s/app-$IDLE:8080/app-$ACTIVE:8080/" Caddyfile
    rm -f Caddyfile.bak
    docker exec evergarden-caddy-prod caddy reload --config /etc/caddy/Caddyfile
    $COMPOSE stop "app-$IDLE"
    exit 1
fi

echo "8. 활성 색 기록 갱신 (app-$IDLE)"
echo "$IDLE" > "$STATE_FILE"

echo "9. 이전 색(app-$ACTIVE) 종료"
$COMPOSE stop "app-$ACTIVE"

echo "10. 옛 이미지 정리 (디스크 공간이 작아 매 배포마다 치워야 한다)"
docker image prune -af

echo "배포 완료 — 현재 활성: app-$IDLE"
