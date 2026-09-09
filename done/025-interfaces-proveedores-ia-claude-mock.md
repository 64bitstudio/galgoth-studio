# 025 — Interfaces de proveedores IA + `ClaudeProvider` + `MockProvider`

**Milestone:** M4 · **Depende de:** 004 · **HUs:** HU-13

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (Diseño técnico §5). Implementar las interfaces `VisionModelProvider`/`StructuredReasoningProvider`/`ImageGenerationProvider`, con `ClaudeProvider` (default/activo) y `MockProvider` (uso exclusivo en tests) implementados este ciclo. `OpenAIProvider`/`RunPodProvider` quedan fuera, sin bloquear la abstracción.

## Criterios de aceptación (TDD)
- Dado el `VisionModelProvider` configurado como `ClaudeProvider`, cuando se invoca con una imagen de referencia, entonces retorna un `ModelIntent` crudo (sin validar todavía — eso es responsabilidad de 028).
- Dado el `MockProvider`, cuando se usa en tests, entonces retorna respuestas deterministas configurables sin llamar a ningún servicio externo.
- Dado el proveedor seleccionado por variable de entorno (`AI_VISION_PROVIDER`, `AI_REASONING_PROVIDER`), cuando se cambia de `ClaudeProvider` a `MockProvider`, entonces ningún código de `ai-orchestrator` ni del dominio necesita modificarse.
- Dado cada llamada a un proveedor, cuando se completa, entonces se registran proveedor, modelo, versión de prompt y versión de esquema (sección 20 del master prompt).

## Hecho

**Decisión de verificación, VoBo explícito del Product Owner vía `AskUserQuestion`**: dado que no existía ningún `ANTHROPIC_API_KEY` configurado en el proyecto, y el PO evitó Anthropic por costo en otro proyecto (NutriTrack), se preguntó explícitamente cómo proceder -- eligió proveer una key real y verificar `ClaudeProvider` en vivo (no solo contra tests). La key vive en `/Users/marcocortes/projects/.env` (archivo de secretos compartidos fuera de cualquier repo git, mismo patrón ya usado para `TELEGRAM_BOT_TOKEN`), nunca commiteada.

### Implementado
- `backend/.../aiorchestrator/provider/`: tres interfaces estables (`VisionModelProvider`, `StructuredReasoningProvider`, `ImageGenerationProvider`) que devuelven `AiProviderResponse` (rawContent + provider + model + promptVersion + schemaVersion) SIN VALIDAR -- responsabilidad de los tickets 028/031.
- `ClaudeMessagesClient` (mecánica HTTP compartida) + `ClaudeVisionProvider`/`ClaudeReasoningProvider` (wrappers delgados por interfaz): llamadas reales a `POST https://api.anthropic.com/v1/messages` vía `RestClient` (sin SDK oficial de Anthropic para Java en Maven Central, verificado). Modelo default `claude-sonnet-5`, configurable vía `ai.claude.model`.
- `MockVisionProvider`/`MockReasoningProvider`/`MockImageProvider`: dobles deterministas configurables (`setNextResponse(...)`), con ejemplos por defecto tomados literalmente del master prompt §9.1/§9.3 -- nunca llaman a ningún servicio externo.
- `AiProviderConfig`: selección por `AI_VISION_PROVIDER`/`AI_REASONING_PROVIDER` (`@ConditionalOnProperty`, default `claude`) -- ningún código de `ai-orchestrator` ni del dominio depende de cuál esté activo.
- `docker/.env.example` (nuevo): documenta `ANTHROPIC_API_KEY` como variable requerida para `ClaudeProvider` real.
- `docs/ARQUITECTURA.md` actualizado. Sin cambios de API/Postman (sin endpoint REST expuesto en este ticket -- interfaces de dominio puras, consumidas recién por 028/031).

### Hallazgos reales, todos corregidos en este mismo ticket
1. **Wiring de Spring, con lección reutilizable para cualquier futura interfaz con 2+ implementaciones seleccionables por entorno**: un primer diseño (`ClaudeProvider implements VisionModelProvider, StructuredReasoningProvider` en una sola clase) rompía la selección por `@ConditionalOnProperty` -- Spring resuelve `getBean(Interfaz.class)` inspeccionando el TIPO REAL de un singleton ya instanciado, no solo el tipo declarado del método `@Bean`, así que la misma instancia seguía siendo candidata de ambas interfaces sin importar cuál bean la expuso (confirmado real: 4 candidatos encontrados en algunos escenarios). Ni `@Primary` en las 4 fábricas lo resolvía. Solución real: una clase por interfaz (`ClaudeVisionProvider`/`ClaudeReasoningProvider`, `MockVisionProvider`/`MockReasoningProvider`), compartiendo la mecánica HTTP vía `ClaudeMessagesClient` (que no implementa ninguna interfaz de proveedor, por eso es seguro compartirlo).
2. **Dependencia de Gradle faltante**: Spring Boot 4.x separó el soporte de cliente HTTP (`RestClient`/`RestTemplate`) del starter de servidor MVC -- sin `spring-boot-starter-restclient` agregado explícitamente, `RestClient.Builder` no existe como bean y ROMPE toda la suite de `@SpringBootTest` del proyecto (no solo los tests de IA), mismo patrón de "un bean nuevo en el paquete raíz rompe todo" ya visto con MinIO en el ticket 023.
3. **Serialización ambigua de `byte[]` con Content-Type JSON**: pre-serializar a mano (`objectMapper.writeValueAsBytes(body)`) y pasar el resultado como `byte[]` es ambiguo para un converter Jackson con prioridad alta -- lo trata como "un valor a serializar" y lo codifica en base64, no como "bytes crudos a escribir". Corregido pasando el `ObjectNode` directamente a `.body(...)`.
4. **Confirmado que el hallazgo #3 NO afecta producción**: se agregó `ClaudeMessagesClientWiringTest` (permanente, con Testcontainers) que usa el `RestClient.Builder` REAL inyectado por Spring (autoconfigurado con el `MappingJackson2HttpMessageConverter` de `JacksonConfig`, ticket 020) -- confirma que la app real serializa correctamente; el problema era exclusivo de tests que construían `RestClient.builder()` "a pelo".
5. **Hallazgo de cuenta (no de código)**: una API key de Anthropic a nivel de ORGANIZACIÓN (no scoped a un workspace) es rechazada por la API real con 400 (`anthropic-workspace-id` requerido) -- resuelto generando una key scoped a un workspace específico desde `console.anthropic.com`, sin cambios de código necesarios.

### Tests
17 tests nuevos (`AiProviderConfigTest` 4, `ClaudeMessagesClientTest` 4, `ClaudeMessagesClientWiringTest` 1, `ClaudeVisionProviderTest` 1, `ClaudeReasoningProviderTest` 1, `MockVisionProviderTest` 2, `MockReasoningProviderTest` 2, `MockImageProviderTest` 1, más ajustes). **137 tests backend, 0 fallos** -- la suite automatizada NUNCA llama a la API real (AC #2), todo verificado contra `MockRestServiceServer`.

### Verificación en vivo real (única llamada real de esta sesión, costo mínimo -- imagen de 1x1px)
`ClaudeVisionProvider` contra la API real de Anthropic (key scoped a un workspace): analizó una imagen real y devolvió una descripción coherente (`"La imagen aparece en blanco o no contiene contenido visible..."`, correcta para el 1x1px enviado), confirmando `provider="claude"`, `model="claude-sonnet-5"`, autenticación, formato de request y parseo de respuesta end-to-end contra el servicio real -- no solo contra un mock. El script de verificación fue TEMPORAL (borrado tras confirmar, nunca parte del repo, per AC #2).

### AC verificados
- ✅ `VisionModelProvider` (Claude) invocado con una imagen real devuelve un `ModelIntent`-shaped raw JSON, sin validar (validación es responsabilidad de 028) -- verificado en vivo contra la API real.
- ✅ `MockProvider`(s) devuelven respuestas deterministas configurables sin ningún servicio externo -- verificado (ausencia de `RestClient`/`HttpClient` en los 3 tipos Mock ES la prueba).
- ✅ Cambiar de proveedor por `AI_VISION_PROVIDER`/`AI_REASONING_PROVIDER` no toca código de `ai-orchestrator` ni del dominio -- verificado con `ApplicationContextRunner` en las 4 combinaciones posibles.
- ✅ Cada `AiProviderResponse` incluye provider/model/promptVersion/schemaVersion (mismos 4 campos que `ai_jobs`, listos para que 028+ los persista tal cual).
