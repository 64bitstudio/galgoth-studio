# 027 — Wizard 4 pasos UI

**Milestone:** M4 · **Depende de:** 024, 002 · **HUs:** HU-10 · **Épica:** 026

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (mockups 02-04). Construir la navegación y UI de los 4 pasos: Referencia → Configuración → Generación → Resultado, fiel al Visual Contract.

## Criterios de aceptación (TDD)
- Dado el paso "Referencia" con una imagen subida (vía 024), cuando se avanza, entonces se llega a "Configuración".
- Dado "Configuración", cuando se completan nombre/tipo base/resolución, entonces el tipo base propuesto por IA (de 028) aparece pre-seleccionado y editable manualmente.
- Dado que se confirma la configuración, cuando se avanza, entonces se dispara el job de generación (029) y se entra a "Generación".

## Hecho

**Decisión de alcance, VoBo explícito del Product Owner vía `AskUserQuestion`**: el ticket depende formalmente solo de 024/002 (ambos done), pero su propio AC menciona comportamiento de 028 ("tipo base propuesto por IA") y 029 ("dispara el job de generación") -- ninguno de los dos existe todavía (son subtareas POSTERIORES de la misma épica 026, ver `pending/028-*.md`/`029-*.md`). Se preguntó explícitamente cómo resolver esta tensión -- el PO eligió: construir la navegación completa de los 4 pasos, con "Referencia" y "Configuración" totalmente reales (funcionando contra el backend real), y "Generación"/"Resultado" como shells visuales fieles a los mockups 03/04 pero sin ningún job real detrás -- 029/030 los conectan de verdad más adelante.

### Implementado
- `frontend/src/ai/`: `WizardStepper.vue` (indicador de progreso, 4 pasos fijos, no navegable hacia adelante) + `AiMobWizard.vue` (orquestador) + `steps/{ReferenceStep,ConfigurationStep,GenerationStep,ResultStep}.vue`.
- **Orden real de efectos secundarios** (resuelve un problema de secuencia real: subir la referencia necesita un `mobId`, pero el nombre del mob se pide recién en el paso 2): la imagen se retiene SOLO en memoria durante "Referencia"; al confirmar "Configuración" se crea el mob de verdad (`POST /api/projects/{id}/mobs`, ticket 022) y DESPUÉS se sube la imagen ya retenida (`POST /api/mobs/{mobId}/references`, ticket 024) -- ambas llamadas reales, en ese orden, en la misma acción de usuario.
- `frontend/src/api/referenceImagesApi.ts` (nuevo): primer cliente frontend de la API de imágenes de referencia (024).
- `ProjectDetail.vue`: nuevo botón "Crear con IA" en el header (junto a "+ Agregar mob"), navega a `/projects/:projectId/mobs/new-ai`.
- **Simplificación consciente en "Configuración"**: el mockup muestra "Detectar con IA" como una opción de tipo base pre-seleccionada -- se omite este ciclo (nada real que "detectar" sin 028) y el tipo base es una selección manual de los 5 valores reales, igual que `AddMobModal.vue` (022). "Resolución de textura" es solo UI por ahora, sin backing en el backend.
- `/dev/wizard-result-harness` (nuevo, mismo patrón que `/dev/design-system`/`/dev/viewport-harness`): `ResultStep.vue` no es alcanzable desde el flujo real todavía (sin 029/030) -- esta ruta permite verificar su fidelidad visual contra el mockup 04 con datos de ejemplo explícitamente marcados como tales.
- `docs/COMPONENTES.md`/`docs/API.md`/`docs/ARQUITECTURA.md` actualizados. Épica 026 marcada `in-process/` junto con este ticket (mismo patrón que epic 015→016) -- sigue in-process hasta que 028/029/030 cierren también.

### Tests
44 tests nuevos (`WizardStepper` 3, `ReferenceStep` 3, `ConfigurationStep` 6, `GenerationStep` 3, `ResultStep` 3, `AiMobWizard` 6, `referenceImagesApi` 2, más 1 test agregado a `ProjectDetail.spec.ts` para el nuevo enlace "Crear con IA"). **243 tests frontend, 0 fallos.** `vue-tsc -b`, `eslint --max-warnings 0` y `npm run build` limpios.

### Verificación en vivo (Claude in Chrome, backend + Postgres + MinIO reales vía `docker compose` + `./gradlew bootRun`)
Subida del archivo real del build pack (`carcomido_reference.png`, 3,093,533 bytes) desde "Referencia" → avanza automáticamente a "Configuración" mostrando la imagen real → nombre "Carcomido Wizard" + tipo "Arácnido" seleccionados → "Generar con IA" crea el mob de verdad (confirmado en Postgres: `base_type='arachnid'`, `status='draft'`) y sube la referencia de verdad (confirmado: `width:1254,height:1254,content_type='image/png'` en `reference_images`, vinculada al mob real) → avanza a "Generación" con su aviso honesto de que el job real no está disponible todavía → "Ir al proyecto" navega de vuelta a `ProjectDetail.vue`, donde el mob nuevo ("Carcomido Wizard") aparece en el grid real junto al mob preexistente. Sin errores de consola en ningún paso. `/dev/wizard-result-harness` verificado visualmente fiel al mockup 04.

### AC verificados
- ✅ Imagen válida subida en "Referencia" avanza a "Configuración" (verificado en vivo con un archivo real de 3MB).
- ⚠️ "Configuración" no pre-selecciona un tipo base propuesto por IA (028 no existe) -- es una selección manual de los 5 valores reales, decisión explícita documentada arriba con VoBo del PO. El resto del AC (nombre/tipo/resolución completables) sí se cumple.
- ⚠️ Confirmar "Configuración" NO dispara un job real de generación (029 no existe) -- en su lugar crea el mob+referencia reales y entra a un shell honesto de "Generación", decisión explícita documentada arriba con VoBo del PO.
