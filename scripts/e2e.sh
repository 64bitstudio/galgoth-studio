#!/usr/bin/env bash
# Ticket 033 (HU-23) -- orquesta el stack real completo (Docker Compose
# Postgres+MinIO, backend con providers mock, frontend) para la suite
# Playwright de aceptación, y lo apaga SIEMPRE al salir (éxito o fallo) --
# mismo patrón manual ya usado en cada verificación en vivo de esta
# sesión, ahora repetible/script-able para CI (Jenkinsfile) y local.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_LOG="$(mktemp -t galgoth-e2e-backend.XXXXXX)"
FRONTEND_LOG="$(mktemp -t galgoth-e2e-frontend.XXXXXX)"
BACKEND_PID=""
FRONTEND_PID=""

cleanup() {
	local exit_code=$?
	echo "--- e2e.sh: limpiando (exit code $exit_code) ---"
	[ -n "$FRONTEND_PID" ] && kill "$FRONTEND_PID" 2>/dev/null || true
	[ -n "$BACKEND_PID" ] && kill "$BACKEND_PID" 2>/dev/null || true
	(cd "$ROOT_DIR/backend" && ./gradlew --stop >/dev/null 2>&1) || true
	# `npm run dev`/`gradlew bootRun` en background dejan procesos hijos
	# (vite/java) que NO reciben el kill de arriba (matan solo el wrapper
	# de shell, no el proceso real que abrió el puerto) -- red de
	# seguridad real, encontrada en la primera corrida de este script:
	# dos `vite` quedaron vivos pese a que el script reportó limpieza OK.
	lsof -tiTCP:5173 -sTCP:LISTEN 2>/dev/null | xargs -r kill 2>/dev/null || true
	lsof -tiTCP:8080 -sTCP:LISTEN 2>/dev/null | xargs -r kill 2>/dev/null || true
	(cd "$ROOT_DIR" && docker compose -f docker/docker-compose.yml down >/dev/null 2>&1) || true
	if [ "$exit_code" -ne 0 ]; then
		echo "--- backend log (últimas 100 líneas) ---"
		tail -n 100 "$BACKEND_LOG" || true
		echo "--- frontend log (últimas 50 líneas) ---"
		tail -n 50 "$FRONTEND_LOG" || true
	fi
	rm -f "$BACKEND_LOG" "$FRONTEND_LOG"
	exit "$exit_code"
}
trap cleanup EXIT

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

echo "--- levantando Postgres + MinIO ---"
(cd "$ROOT_DIR" && docker compose -f docker/docker-compose.yml up -d)

echo "--- levantando backend (AI_VISION_PROVIDER=mock, AI_REASONING_PROVIDER=mock) ---"
(cd "$ROOT_DIR/backend" && AI_VISION_PROVIDER=mock AI_REASONING_PROVIDER=mock ./gradlew bootRun --console=plain >"$BACKEND_LOG" 2>&1) &
BACKEND_PID=$!
wait_for "http://localhost:8080/actuator/health" "backend"

echo "--- levantando frontend ---"
(cd "$ROOT_DIR/frontend" && npm run dev >"$FRONTEND_LOG" 2>&1) &
FRONTEND_PID=$!
wait_for "http://localhost:5173/" "frontend"

echo "--- corriendo la suite Playwright ---"
(cd "$ROOT_DIR/frontend" && npx playwright test)
