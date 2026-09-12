#!/usr/bin/env bash
# Run only after checking out the exact reviewed commit. Does not delete untracked model/dictionary files.
set -Eeuo pipefail
umask 077

SHA="${1:?Pass the exact release commit SHA}"
[[ "$SHA" =~ ^[0-9a-f]{40}$ ]] || { echo "Invalid release SHA" >&2; exit 2; }
[[ "$(git rev-parse HEAD)" == "$SHA" ]] || { echo "Checkout does not match release SHA" >&2; exit 2; }
: "${DEPLOY_ENV_B64:?Pass a base64 encoded environment file}"
IMAGE="translacat-api:$SHA"
CONTAINER="translacat-api-container"
BACKUP="${CONTAINER}-rollback-$(date -u +%Y%m%dT%H%M%S)-$$"
ENV_FILE="$(mktemp .deploy-env.XXXXXX)"
HAD_PREVIOUS=false
PREVIOUS_RUNNING=false
BACKED_UP=false
REPLACEMENT_ATTEMPTED=false

cleanup() { rm -f "$ENV_FILE"; }
trap cleanup EXIT

rollback() {
    local original_exit="$1"
    trap - ERR INT TERM
    set +e
    echo "Deployment failed (exit $original_exit); restoring the previous container." >&2
    if [ "$REPLACEMENT_ATTEMPTED" = true ]; then
        docker logs --tail 200 "$CONTAINER" >&2
        docker rm -f "$CONTAINER"
    fi
    if [ "$BACKED_UP" = true ]; then
        if docker rename "$BACKUP" "$CONTAINER"; then
            if [ "$PREVIOUS_RUNNING" = true ]; then
                docker start "$CONTAINER" || echo "CRITICAL: automatic rollback start failed" >&2
            fi
        else
            echo "CRITICAL: automatic rollback rename failed; preserved container: $BACKUP" >&2
        fi
    elif [ "$HAD_PREVIOUS" = true ] && [ "$PREVIOUS_RUNNING" = true ]; then
        # Stop may have succeeded just before rename failed.
        docker start "$CONTAINER" || echo "CRITICAL: could not restart existing container" >&2
    fi
    exit "$original_exit"
}
trap 'rollback $?' ERR
trap 'rollback 130' INT
trap 'rollback 143' TERM

printf '%s' "$DEPLOY_ENV_B64" | base64 --decode > "$ENV_FILE"
test -s "$ENV_FILE"
docker network inspect translacat-network >/dev/null 2>&1 || docker network create translacat-network
# All potentially destructive steps are after a successful immutable-tag build.
docker build --label "org.opencontainers.image.revision=$SHA" -t "$IMAGE" .

if docker container inspect "$CONTAINER" >/dev/null 2>&1; then
    HAD_PREVIOUS=true
    PREVIOUS_RUNNING="$(docker inspect --format '{{.State.Running}}' "$CONTAINER")"
    docker stop --time 30 "$CONTAINER"
    docker rename "$CONTAINER" "$BACKUP"
    BACKED_UP=true
fi

REPLACEMENT_ATTEMPTED=true
docker run -d --name "$CONTAINER" --restart unless-stopped \
    --network translacat-network --env-file "$ENV_FILE" \
    -e "SPRING_PROFILES_ACTIVE=prod" --memory="1.5g" -p 8080:8080 "$IMAGE"

actual_revision="$(docker inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$CONTAINER")"
[[ "$actual_revision" == "$SHA" ]] || { echo "Running container revision mismatch" >&2; false; }

ATTEMPTS="${DEPLOY_HEALTH_ATTEMPTS:-60}"
DELAY="${DEPLOY_HEALTH_DELAY_SECONDS:-5}"
[[ "$ATTEMPTS" =~ ^[1-9][0-9]*$ && "$DELAY" =~ ^[0-9]+$ ]]
for ((attempt = 1; attempt <= ATTEMPTS; attempt++)); do
    code="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 5 http://localhost:8080/ || true)"
    if [[ "$code" == "200" || "$code" == "401" ]]; then
        echo "Released $SHA as $CONTAINER."
        if [ "$BACKED_UP" = true ]; then
            echo "Rollback container retained (stopped): $BACKUP"
        fi
        exit 0
    fi
    sleep "$DELAY"
done

echo "New container did not become ready" >&2
false # ERR trap rolls back and preserves a failing exit code for CI.
