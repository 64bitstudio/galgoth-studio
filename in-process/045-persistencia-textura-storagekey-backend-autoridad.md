# 045 — Persistencia content-addressed de textura — backend autoridad de storageKey

**Milestone:** M8 · **Depende de:** 020, 023, 024, 040 · **HUs:** HU-30, HU-31 · **Épica:** K (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Nace de `docs/definiciones/galgoth-studio-fase3-textura.md` (Diseño técnico §4/§5/§6). El bitmap de textura se persiste content-addressed en MinIO (`textures/{sha256}.png`), pero el **backend es la única autoridad** del hash/`storageKey` — nunca confía en un valor propuesto por el cliente. Además, "Guardar"/Apply deben hacer flush del `PUT /texture` pendiente antes de crear cualquier `mob_revision` — una Revision jamás apunta a un `storageKey` no persistido.

## Criterios de aceptación (TDD)
- Dado `PUT /api/mobs/{mobId}/texture` con bytes de un PNG válido, cuando el backend lo procesa, entonces decodifica/valida la imagen, calcula ÉL MISMO el SHA-256 sobre los bytes decodificados, sube a MinIO bajo `textures/{sha256-hex}.png` (idempotente — solo si la clave no existe ya), y devuelve ese `storageKey` en la respuesta.
- Dado un cliente que envía un `storageKey` propio (calculado localmente) junto con los bytes, cuando el backend responde, entonces el `storageKey` devuelto es SIEMPRE el que el backend calculó — el valor del cliente se ignora por completo (test que envía un `storageKey` deliberadamente incorrecto y verifica que la respuesta lo corrige).
- Dado bytes que no decodifican como PNG válido, cuando se envían a `PUT /texture`, entonces se rechaza explícitamente (4xx) — nunca confía en el `Content-Type` declarado por el cliente.
- Dado el dirty-check de autosave (`DraftPersistenceService`), cuando compara el estado de textura, entonces compara el `storageKey` (string) en vez del bitmap completo — O(1), sin cambios al mecanismo ya existente para geometría.
- Dado un `PUT /texture` todavía en vuelo (promesa pendiente en el cliente), cuando el usuario hace clic en "Guardar", entonces la creación de la `mob_revision` espera (await) la respuesta y su `storageKey` oficial ANTES de invocarse — nunca se dispara la creación de la Revision en paralelo sin ese valor.
- Dado un intento (simulado, ej. por bug del cliente) de crear una Revision referenciando un `storageKey` que NO existe en MinIO, cuando el servicio de creación de Revision lo detecta (defensa en profundidad, verificación de existencia antes de escribir), entonces lanza un error explícito y NO escribe la fila — nunca una referencia colgante.
- Dado dos "Guardar" consecutivos sin cambio de bitmap entre medio, cuando se persisten, entonces ambas revisiones comparten el mismo `storageKey` — dedup automático, sin código de deduplicación explícito adicional.

## Hecho
