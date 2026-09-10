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

Implementado tal como lo describe el ticket, con una decisión de diseño
explícita documentada abajo (hashear bytes canónicos re-codificados, no
los bytes crudos subidos) y el alcance de "orquestador de espera del
lado del cliente" (AC de "await" antes de Guardar) dejado fuera por
diseño — este ticket es puramente backend, la garantía real que sí
entrega es la defensa en profundidad del lado servidor.

**Endpoint nuevo:**
- `PUT /api/mobs/{mobId}/texture` (`MobTextureController`, paquete
  `project.api`) — mismo estilo mob-scoped y de bytes crudos (sin
  multipart) que `MobThumbnailController` (023): `consumes =
  image/png`, sin JSON. Autoridad de negocio: `TextureService` (paquete
  nuevo `project.texture`), sobre `AssetStorageService` ya existente
  (023) — reutilizada sin cambios de configuración (mismo bucket/
  endpoint MinIO).
- `TextureService.upload`: verifica que el mob exista (`MobNotFoundException`
  si no), decodifica los bytes recibidos vía `ImageIO` (rechaza con
  `InvalidTextureException` → 400 `INVALID_TEXTURE` si no decodifican
  como imagen válida — nunca confía en el `Content-Type` declarado),
  RE-CODIFICA la imagen decodificada a PNG canónico, calcula el SHA-256
  sobre esos bytes canónicos y sube a MinIO bajo `textures/{sha256-hex}.png`
  solo si la key no existe ya (`AssetStorageService.exists`, método
  nuevo vía `HeadObjectRequest`) — nunca vuelve a escribir si ya existe.
  Devuelve `TextureUploadResponse{storageKey}` — el contrato del
  endpoint no tiene NINGÚN campo para que el cliente proponga un
  `storageKey` propio, así que "el valor del cliente se ignora" es una
  garantía estructural del contrato, no solo de comportamiento.

**Decisión de diseño — qué bytes hashear (crudos vs. decodificados),
con justificación:** se hashean/almacenan los bytes PNG CANÓNICOS
re-codificados por `ImageIO` tras decodificar, nunca los bytes crudos
tal como los subió el cliente. Justificación: el Diseño técnico §6 pide
explícitamente "el SHA-256 sobre los bytes decodificados/canónicos", y
esa palabra "canónicos" importa en la práctica — dos clientes/encoders
distintos (o el mismo canvas serializado por dos motores de navegador
distintos) pueden producir bytes PNG *diferentes* para el *mismo*
contenido de píxeles (compresión, chunks auxiliares/metadata). Hashear
los bytes crudos tal cual rompería silenciosamente el dedup
content-addressed que HU-31 exige ("dos revisiones consecutivas sin
cambio de bitmap comparten la misma clave automáticamente") en el
momento en que cambiara de encoder — un bug latente difícil de
reproducir. Re-codificar a una forma canónica antes de hashear/
almacenar garantiza que el mismo contenido visual siempre produce el
mismo `storageKey`, sin importar qué encoder lo produjo. El propio
`ImageIO` PNG writer es determinista dentro de esta JVM (no embebe
timestamps ni metadata variable por defecto), lo cual se confirma con
el test de idempotencia (ver Tests).

**Flush obligatorio antes de crear una Revision (defensa en
profundidad, `DraftPersistenceService`):**
- Nuevo método privado `requireTextureAssetPersisted(MobProjectModel)`,
  invocado en AMBOS métodos que escriben una fila en `mob_revisions`
  (`saveRevision` — "Guardar" — y `applyGenerationProposal` — "Usar
  este modelo"/Apply, ticket 030): si `model.texture().storageKey()`
  no es `null`, verifica su existencia real en MinIO
  (`AssetStorageService.exists`) ANTES de construir/guardar la entidad
  de Revision. Si no existe, lanza `DanglingTextureReferenceException`
  (nueva, mismo estilo que `DraftNotFoundException`/
  `UvAtlasOverflowException`) → 400 `DANGLING_TEXTURE_REFERENCE` — no
  se escribe ninguna fila.
- El AC de "await antes de invocar Guardar" (orquestación del lado
  cliente) queda explícitamente FUERA de alcance de este ticket, tal
  como lo aclara el propio ticket ("este ticket solo garantiza que el
  backend nunca acepta una Revision con una referencia rota, no
  implementa el orquestador de espera del lado del cliente") — no se
  tocó ningún código de frontend.

**Dirty-check de autosave (`DraftPersistenceService.autosave`) — AC ya
cumplido sin cambios:** confirmado que el dirty-check ya compara por
igualdad estructural completa de `MobProjectModel` (mecanismo del
ticket 020, sin modificar). `TextureDocument` es un `record` con
`storageKey: String` — el `equals()` generado por el compilador ya lo
compara como cualquier otro campo de valor, así que la comparación de
textura es automáticamente O(1) por string, nunca por bitmap. No hizo
falta ningún cambio en `autosave` para este AC — solo se documentó
explícitamente en el javadoc de la clase (evitando que un lector futuro
asuma que falta trabajo acá).

**Dedup entre revisiones consecutivas (AC de "sin lógica de
deduplicación explícita"):** se cumple por construcción de las dos
piezas de arriba juntas — el mismo bitmap sube siempre al mismo
`storageKey` (content-addressed) y `saveRevision` ya compara el
`MobProjectModel` completo contra la última revisión antes de decidir
si crea una nueva fila (mecanismo del ticket 020, sin cambios) — dos
"Guardar" consecutivos sin cambio de `storageKey` (ni de ningún otro
campo) ya no duplican revisión, y si sí hay otro cambio, la nueva
revisión referencia el mismo `storageKey` sin ningún código adicional.

**Docs:**
- `docs/API.md` — sección nueva "Persistencia content-addressed de
  textura (ticket 045, HU-30/HU-31)" con el contrato completo del
  endpoint y la nota de flush obligatorio.
- `postman/galgoth-studio/galgoth-studio.postman_collection.json` —
  carpeta nueva "Textura (ticket 045)" con la petición `PUT .../texture`,
  mismo estilo que "Thumbnails (ticket 023)".
- `docs/ARQUITECTURA.md` — entrada nueva en la sección "Fase 3".

**Tests (backend, `./gradlew test`): 301 tests, 0 failures** (+9 nuevos
sobre la baseline de 292 tras el ticket 044):
- `MobTextureControllerTest` (integración real, Testcontainers Postgres
  + MinIO, mismo patrón que `MobThumbnailControllerTest`/023): sube un
  PNG real y confirma el `storageKey` devuelto es el SHA-256 esperado
  sobre los bytes canónicos; confirma **idempotencia real** subiendo el
  mismo contenido dos veces — entre ambas subidas se sobreescribe
  manualmente la key ya existente con un "centinela" de bytes distinto,
  y se confirma que el centinela SOBREVIVE intacto tras la segunda
  subida (prueba real de que el servicio detectó que la key ya existía
  y NO volvió a escribir, sin depender de un spy/mock sobre
  `AssetStorageService`); confirma que un header con un `storageKey`
  "propuesto por el cliente" deliberadamente incorrecto se ignora por
  completo (el contrato no tiene ningún campo para ese valor); rechaza
  bytes no-PNG con 400 `INVALID_TEXTURE`; 404 `MOB_NOT_FOUND` si el mob
  no existe.
- `DraftPersistenceServiceTest` (+2 tests): `applyGenerationProposal`
  con un `storageKey` que NO existe en MinIO lanza
  `DanglingTextureReferenceException` y no persiste ni revisión ni
  draft; con uno que SÍ existe, crea la revisión normalmente.
- `MobDraftControllerTest` (+2 tests, HTTP de punta a punta): `POST
  /revisions` ("Guardar") con un `storageKey` de textura inexistente en
  MinIO responde 400 `DANGLING_TEXTURE_REFERENCE` y no crea la fila;
  con uno que sí existe, crea la revisión normalmente (201).
- Se confirmó que ningún test preexistente pasa un `storageKey` no-nulo
  a `saveRevision`/`applyGenerationProposal` (todos usan `null`, el
  valor por defecto de la textura placeholder) — cero riesgo de
  regresión por la nueva verificación defensiva.

**Hallazgos:** ninguno que no cuadrara con el ticket o el diseño
técnico — el único punto que requería una decisión propia (bytes
crudos vs. decodificados para el hash) está resuelto y justificado
arriba, consistente con la redacción de "decodificados/canónicos" del
Diseño técnico §6.

**Propuesta de mejora continua:** el patrón "sube bytes crudos,
decodifica/valida server-side, nunca confíes en `Content-Type`" ya se
repite 3 veces en este backend (`ReferenceImageService`/024,
`ThumbnailService`/023 parcialmente, y ahora `TextureService`/045) con
la misma forma (`ImageIO.read` + `null` check + excepción de dominio
propia por feature). Un helper compartido (`ImageDecodingSupport` o
similar, `project`/`asset`) que centralice "decodificar-o-lanzar" con
un mensaje parametrizable evitaría que una futura feature de imágenes
repita la misma docena de líneas una cuarta vez — candidato a un
ticket de refactor chico, no bloqueante.
