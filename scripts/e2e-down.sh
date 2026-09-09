#!/usr/bin/env bash
# Ticket 033 (HU-23) -- apaga lo que `e2e-up.sh` levantó. Lee el mismo
# archivo de estado; SIEMPRE se llama, éxito o fallo (ver Jenkinsfile
# `finally` / `e2e.sh` `trap`).
set -uo pipefail

STATE_FILE="${GALGOTH_E2E_STATE_FILE:-/tmp/galgoth-e2e-state.env}"
if [ -f "$STATE_FILE" ]; then
	# shellcheck disable=SC1090
	source "$STATE_FILE"
fi

echo "--- e2e-down.sh: limpiando ---"
[ -n "${FRONTEND_PID:-}" ] && kill "$FRONTEND_PID" 2>/dev/null || true
[ -n "${BACKEND_PID:-}" ] && kill "$BACKEND_PID" 2>/dev/null || true
[ -n "${ROOT_DIR:-}" ] && (cd "$ROOT_DIR/backend" && ./gradlew --stop >/dev/null 2>&1) || true
# `npm run dev`/`gradlew bootRun` en background dejan procesos hijos
# (vite/java) que NO reciben el kill de arriba (matan solo el wrapper de
# shell, no el proceso real que abrió el puerto) -- red de seguridad
# real, encontrada en la primera corrida de este script: dos `vite`
# quedaron vivos pese a que el script reportó limpieza OK.
lsof -tiTCP:5173 -sTCP:LISTEN 2>/dev/null | xargs -r kill 2>/dev/null || true
lsof -tiTCP:8080 -sTCP:LISTEN 2>/dev/null | xargs -r kill 2>/dev/null || true
if [ -n "${ROOT_DIR:-}" ]; then
	(cd "$ROOT_DIR" && docker compose -f docker/docker-compose.yml down >/dev/null 2>&1) || true
fi

if [ "${1:-}" = "--dump-logs" ]; then
	echo "--- backend log (últimas 100 líneas) ---"
	[ -n "${BACKEND_LOG:-}" ] && tail -n 100 "$BACKEND_LOG" || true
	echo "--- frontend log (últimas 50 líneas) ---"
	[ -n "${FRONTEND_LOG:-}" ] && tail -n 50 "$FRONTEND_LOG" || true
fi

[ -n "${BACKEND_LOG:-}" ] && rm -f "$BACKEND_LOG"
[ -n "${FRONTEND_LOG:-}" ] && rm -f "$FRONTEND_LOG"
rm -f "$STATE_FILE"
