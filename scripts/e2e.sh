#!/usr/bin/env bash
# Ticket 033 (HU-23) -- uso LOCAL: levanta el stack real completo, corre
# Playwright directo en este Mac (Chromium ya instalado por
# `npx playwright install`, sin las restricciones de un agente de CI sin
# root), y apaga todo siempre al salir (éxito o fallo). CI (Jenkinsfile)
# usa `e2e-up.sh`/`e2e-down.sh` por separado para poder correr
# `npx playwright test` en un contenedor Docker con Chromium+deps ya
# resueltas -- ver el comentario de cabecera de esos dos scripts.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cleanup() {
	local exit_code=$?
	if [ "$exit_code" -ne 0 ]; then
		"$ROOT_DIR/scripts/e2e-down.sh" --dump-logs
	else
		"$ROOT_DIR/scripts/e2e-down.sh"
	fi
	exit "$exit_code"
}
trap cleanup EXIT

"$ROOT_DIR/scripts/e2e-up.sh"

echo "--- corriendo la suite Playwright ---"
(cd "$ROOT_DIR/frontend" && npx playwright test)
