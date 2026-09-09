#!/usr/bin/env bash
# Ticket 033 (HU-23) -- levanta el stack real completo (Docker Compose
# Postgres+MinIO, backend con providers mock, frontend) para la suite
# Playwright de aceptación, SIN correrla ni apagar nada -- separado de
# `e2e-down.sh` para que CI pueda correr `npx playwright test` en un
# contenedor Docker distinto (con Chromium+dependencias ya instaladas,
# ver Jenkinsfile) apuntando a este mismo stack por red del host,
# mientras el backend/frontend siguen en el agente normal.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATE_FILE="${GALGOTH_E2E_STATE_FILE:-/tmp/galgoth-e2e-state.env}"
BACKEND_LOG="$(mktemp -t galgoth-e2e-backend.XXXXXX)"
FRONTEND_LOG="$(mktemp -t galgoth-e2e-frontend.XXXXXX)"

wait_for() {
	local url="$1" label="$2" attempts=60
	for _ in $(seq 1 "$attempts"); do
		if curl -sS -o /dev/null -m 2 "$url"; then
			echo "$label: listo ($url)"
			return 0
		fi
		sleep 2
	done
	echo "$label: nunca respondió en $url" >&2
	return 1
}

# MINIO_HOST_PORT=0 -- Docker asigna un puerto de host libre (el fijo
# 9000/9001 de docker-compose.yml puede estar ya ocupado en un agente de
# CI compartido, hallazgo real de este ticket). Se descubre el puerto
# real ya asignado ANTES de levantar el backend, y se lo pasa por env var
# (GALGOTH_STORAGE_MINIO_ENDPOINT, ver application.properties).
echo "--- levantando Postgres + MinIO ---"
(cd "$ROOT_DIR" && MINIO_HOST_PORT=0 MINIO_CONSOLE_HOST_PORT=0 docker compose -f docker/docker-compose.yml up -d)
MINIO_PORT="$(cd "$ROOT_DIR" && docker compose -f docker/docker-compose.yml port minio 9000 | cut -d: -f2)"
echo "MinIO real en el puerto $MINIO_PORT"

echo "--- levantando backend (AI_VISION_PROVIDER=mock, AI_REASONING_PROVIDER=mock) ---"
(cd "$ROOT_DIR/backend" && AI_VISION_PROVIDER=mock AI_REASONING_PROVIDER=mock GALGOTH_STORAGE_MINIO_ENDPOINT="http://localhost:$MINIO_PORT" ./gradlew bootRun --console=plain >"$BACKEND_LOG" 2>&1) &
BACKEND_PID=$!
wait_for "http://localhost:8080/actuator/health" "backend"

echo "--- levantando frontend ---"
(cd "$ROOT_DIR/frontend" && npm run dev >"$FRONTEND_LOG" 2>&1) &
FRONTEND_PID=$!
wait_for "http://localhost:5173/" "frontend"

cat >"$STATE_FILE" <<EOF
BACKEND_PID=$BACKEND_PID
FRONTEND_PID=$FRONTEND_PID
BACKEND_LOG=$BACKEND_LOG
FRONTEND_LOG=$FRONTEND_LOG
ROOT_DIR=$ROOT_DIR
EOF
echo "--- stack real arriba, estado en $STATE_FILE ---"
