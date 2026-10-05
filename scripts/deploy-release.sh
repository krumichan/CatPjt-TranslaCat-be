#!/usr/bin/env bash
# 검증된 commit만 배포하고 기존 container의 env/mount를 rollback용으로 보존한다.
set -Eeuo pipefail
umask 077

SHA="${1:?Pass the exact release commit SHA}"
[[ "$SHA" =~ ^[0-9a-f]{40}$ ]] || { echo "Invalid release SHA" >&2; exit 2; }
: "${DEPLOY_ENV_B64:?Pass a base64 encoded environment file}"
IMAGE="translacat-api:$SHA"
CONTAINER="translacat-api-container"
AUDIO_VOLUME="translacat-be-novel-audio-spool"
BACKUP="${CONTAINER}-rollback-$(date -u +%Y%m%dT%H%M%S)-$$"
ATTEMPTS="${DEPLOY_HEALTH_ATTEMPTS:-60}"
DELAY="${DEPLOY_HEALTH_DELAY_SECONDS:-5}"
ROLLBACK_ATTEMPTS="${DEPLOY_ROLLBACK_ATTEMPTS:-12}"
[[ "$ATTEMPTS" =~ ^[1-9][0-9]*$ && "$DELAY" =~ ^[0-9]+$ && "$ROLLBACK_ATTEMPTS" =~ ^[1-9][0-9]*$ ]]
(( ATTEMPTS <= 60 && DELAY <= 5 && ROLLBACK_ATTEMPTS <= 12 ))

# workflow의 checkout 잠금을 상속하거나 직접 실행에서도 같은 잠금을 획득한다.
if [[ "${DEPLOY_LOCK_FD:-}" == "9" ]]; then
    flock --nonblock 9 || { echo "BE release lock is unavailable" >&2; exit 2; }
else
    mkdir -p "$HOME/.translacat-deploy"
    exec 9> "$HOME/.translacat-deploy/be.lock"
    flock --nonblock 9 || { echo "Another BE deployment holds the lock" >&2; exit 2; }
fi

# 직접 실행에서도 잠금 획득 후 검증하며 tracked 수정이 있는 checkout은 사용하지 않는다.
[[ "$(git rev-parse HEAD)" == "$SHA" ]] || { echo "Checkout does not match release SHA" >&2; exit 2; }
git diff --quiet
git diff --cached --quiet
if git ls-files --error-unmatch .env >/dev/null 2>&1; then
    echo "Tracked production environment blocks the deployment" >&2
    exit 2
fi

# 임시 비밀 파일은 repo 밖에 두어 checkout gate와 build context에서 분리한다.
ENV_FILE="$(mktemp "${TMPDIR:-/tmp}/translacat-be-env.XXXXXX")"
HEALTH_FILE=""
WORKTREE_FILE=""
HAD_PREVIOUS=false
PREVIOUS_RUNNING=false
BACKED_UP=false
REPLACEMENT_ATTEMPTED=false
PREVIOUS_ID=""

cleanup() {
    rm -f "$ENV_FILE" "$HEALTH_FILE" "$WORKTREE_FILE"
}
trap cleanup EXIT
HEALTH_FILE="$(mktemp "${TMPDIR:-/tmp}/translacat-be-health.XXXXXX")"
WORKTREE_FILE="$(mktemp "${TMPDIR:-/tmp}/translacat-be-worktree.XXXXXX")"

# workflow 이외의 직접 호출에서도 .env/사전 외 untracked 파일을 묵인하지 않는다.
git ls-files --others --exclude-standard -z > "$WORKTREE_FILE"
while IFS= read -r -d '' path; do
    case "$path" in
        .env|sudachi/system_full.dic)
            [[ -f "$path" && ! -L "$path" ]] || { echo "Untracked runtime input must be a regular file" >&2; exit 2; }
            ;;
        *) echo "Unreviewed untracked files block the deployment" >&2; exit 2 ;;
    esac
done < "$WORKTREE_FILE"

health_ready() {
    local code
    # 생존 endpoint의 상태와 body를 함께 검사한다. 의존 서비스의 업무 성공을 뜻하지 않는다.
    if ! code="$(curl --silent --output "$HEALTH_FILE" --write-out '%{http_code}' \
        --connect-timeout 2 --max-time 5 http://localhost:8080/api/v1/health)"; then
        return 1
    fi
    [[ "$code" == "200" ]] || return 1
    python3 - "$HEALTH_FILE" <<'PY'
import json
import sys
try:
    with open(sys.argv[1], encoding="utf-8") as source:
        body = json.load(source)
    valid = isinstance(body, dict) and body.get("resultCode") == 200 and body.get("body") == "OK"
except (OSError, ValueError):
    valid = False
sys.exit(0 if valid else 1)
PY
}

rollback() {
    local original_exit="$1" rollback_failed=false restored_id attempt ready=false
    trap - ERR INT TERM
    set +e
    echo "Deployment failed (exit $original_exit); restoring the previous container." >&2

    # 원문 애플리케이션 로그에는 credential/사용자 데이터가 있을 수 있어 CI에 출력하지 않는다.
    if [ "$REPLACEMENT_ATTEMPTED" = true ]; then
        if ! timeout --kill-after=5s 30s docker rm -f "$CONTAINER" >/dev/null; then
            echo "CRITICAL: failed replacement could not be removed" >&2
            rollback_failed=true
        fi
    fi
    if [ "$BACKED_UP" = true ]; then
        if ! timeout --kill-after=5s 30s docker rename "$BACKUP" "$CONTAINER"; then
            echo "CRITICAL: rollback rename failed; preserved container: $BACKUP" >&2
            rollback_failed=true
        fi
    fi
    if [ "$HAD_PREVIOUS" = true ]; then
        restored_id="$(timeout --kill-after=5s 15s docker inspect --format '{{.Id}}' "$CONTAINER")"
        if [[ "$restored_id" != "$PREVIOUS_ID" ]]; then
            echo "CRITICAL: original container was not restored" >&2
            rollback_failed=true
        elif [ "$PREVIOUS_RUNNING" = true ]; then
            if ! timeout --kill-after=5s 45s docker start "$CONTAINER" >/dev/null; then
                echo "CRITICAL: automatic rollback start failed" >&2
                rollback_failed=true
            else
                for ((attempt = 1; attempt <= ROLLBACK_ATTEMPTS; attempt++)); do
                    if health_ready; then
                        ready=true
                        break
                    fi
                    sleep "$DELAY"
                done
                if [ "$ready" != true ]; then
                    echo "CRITICAL: original container restored but health did not recover" >&2
                    rollback_failed=true
                fi
            fi
        fi
    fi
    if [ "$rollback_failed" = true ]; then
        exit 70
    fi
    exit "$original_exit"
}
trap 'rollback $?' ERR
trap 'rollback 130' INT
trap 'rollback 143' TERM

# Secret은 셸 평가 없이 decode/검사하며 실제 운영 사전이 없으면 기존 서비스를 건드리지 않는다.
printf '%s' "$DEPLOY_ENV_B64" | base64 --decode > "$ENV_FILE"
unset DEPLOY_ENV_B64
python3 scripts/validate-release-env.py "$ENV_FILE"
[[ -f sudachi/system_full.dic && ! -L sudachi/system_full.dic ]]
[[ "$(wc -c < sudachi/system_full.dic)" -gt 1000000 ]]
if ! timeout --kill-after=5s 30s docker network inspect translacat-network >/dev/null 2>&1; then
    timeout --kill-after=5s 30s docker network create translacat-network >/dev/null
fi

# build와 image revision 검증이 성공하기 전에는 기존 container를 멈추지 않는다.
timeout --kill-after=30s 90m docker build --label "org.opencontainers.image.revision=$SHA" -t "$IMAGE" .
image_revision="$(timeout --kill-after=5s 15s docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$IMAGE")"
[[ "$image_revision" == "$SHA" ]] || { echo "Built image revision mismatch" >&2; false; }
timeout --kill-after=5s 45s docker run --rm --network none --read-only --memory=128m \
    --entrypoint sh "$IMAGE" -c 'test -s /app/dictionaries/system_full.dic && test ! -e /app/.env && keytool -list -cacerts -storepass changeit -alias translacat-service-ca >/dev/null'

# 업로드 재시도용 음성 원본은 앱 container 교체·rollback 뒤에도 같은 volume에 남긴다.
# 기존 이름의 타인 volume은 자동 채택하지 않고 기존 앱을 멈추기 전에 거부한다.
if timeout --kill-after=5s 30s docker volume inspect "$AUDIO_VOLUME" >/dev/null 2>&1; then
    volume_owner="$(timeout --kill-after=5s 30s docker volume inspect --format '{{index .Labels "translacat.be.owner"}}' "$AUDIO_VOLUME")"
    [[ "$volume_owner" == "translacat-be-release" ]] || { echo "Novel audio volume ownership mismatch" >&2; false; }
else
    timeout --kill-after=5s 30s docker volume create --label translacat.be.owner=translacat-be-release "$AUDIO_VOLUME" >/dev/null
fi

existing_id="$(timeout --kill-after=5s 15s docker container ls --all --filter "name=^/${CONTAINER}$" --format '{{.ID}}')"
if [[ -n "$existing_id" ]]; then
    HAD_PREVIOUS=true
    PREVIOUS_ID="$(timeout --kill-after=5s 15s docker inspect --format '{{.Id}}' "$CONTAINER")"
    PREVIOUS_RUNNING="$(timeout --kill-after=5s 15s docker inspect --format '{{.State.Running}}' "$CONTAINER")"
    timeout --kill-after=5s 45s docker stop --time 30 "$CONTAINER" >/dev/null
    timeout --kill-after=5s 30s docker rename "$CONTAINER" "$BACKUP"
    BACKED_UP=true
fi

REPLACEMENT_ATTEMPTED=true
timeout --kill-after=5s 45s docker run -d --name "$CONTAINER" --restart unless-stopped \
    --network translacat-network --env-file "$ENV_FILE" \
    --mount "type=volume,src=$AUDIO_VOLUME,dst=/app/data/novel-audio-spool" \
    -e "SPRING_PROFILES_ACTIVE=prod" --memory="1.5g" -p 8080:8080 "$IMAGE" >/dev/null

actual_revision="$(timeout --kill-after=5s 15s docker inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$CONTAINER")"
[[ "$actual_revision" == "$SHA" ]] || { echo "Running container revision mismatch" >&2; false; }
for ((attempt = 1; attempt <= ATTEMPTS; attempt++)); do
    if health_ready; then
        echo "Released $SHA as $CONTAINER (HTTP liveness only; post-release acceptance is still required)."
        if [ "$BACKED_UP" = true ]; then
            echo "Rollback container retained (stopped): $BACKUP"
        fi
        exit 0
    fi
    sleep "$DELAY"
done

echo "New container did not become ready" >&2
false # ERR trap은 rollback 후에도 실패를 CI에 반환한다.
