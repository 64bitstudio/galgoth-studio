# 091 — Perfil de producto: avatar, preferencias y purge-projects

## Objetivo
Ver `docs/definiciones/perfil-de-usuario.md` (VoBo de Marco recibido) —
HU-3, HU-9, y la mitad de galgoth-studio de HU-10. auth-core-mc no tiene
almacenamiento de archivos; galgoth-studio sí (`AssetStorageService`,
ya usado para imágenes de referencia/thumbnails/texturas). Este ticket
crea el "perfil de producto" de cada usuario (avatar + preferencias) y
el endpoint interno que auth-core-mc necesita para eliminar cuentas
(ticket `064` de ese repo) en cascada.

**Depende de:** nada nuevo — reutiliza `AssetStorageService` y el
mecanismo de `ProjectAccessGuard`/JWT ya construidos (tickets 077/084/085).

## Alcance
- **Sí incluye:**
  - Migración: tabla `user_profile` (`user_id` = `sub` del JWT de
    auth-core-mc, PK; `avatar_key` nullable; 3 columnas booleanas de
    preferencias, default `true`; `updated_at`).
  - `POST /api/account/avatar` (autenticado, multipart): sube la imagen
    vía `AssetStorageService` (mismos límites de formato/tamaño ya
    usados para imágenes de referencia), guarda `avatar_key`.
  - `GET /api/account/profile` / `PATCH /api/account/preferences`
    (autenticado): leer/guardar las 3 preferencias de notificación.
    **No cambian el comportamiento real de envío de correos todavía**
    (documentado explícitamente en el documento de definición como
    ticket de seguimiento aparte — este ticket solo persiste el valor).
  - `POST /api/internal/users/{userId}/purge-projects`: protegido por un
    secreto compartido servidor-a-servidor (nueva variable de entorno,
    NO por JWT de usuario — lo llama auth-core-mc, no un navegador),
    soft-deletea TODOS los proyectos de ese `userId` (públicos o
    privados) y borra su fila de `user_profile`.
- **No incluye:** la UI de la pantalla "Usuario" (ticket `093`), mostrar
  el avatar en Explorar (ticket `092`), que las preferencias afecten
  envíos reales de correo.

## Criterios de aceptación (TDD)
- Subir un avatar válido → `avatar_key` se guarda, la imagen es
  recuperable vía la URL pública de MinIO (mismo mecanismo que
  referencias/thumbnails).
- Guardar preferencias → se reflejan en el `GET` subsecuente.
- `purge-projects` sin el secreto correcto → rechazado (`401`/`403`, no
  ejecuta nada).
- `purge-projects` con el secreto correcto sobre un usuario con
  proyectos públicos y privados → todos quedan soft-deleted, incluidos
  los públicos (dejan de aparecer en Explorar).
- Suite completa en verde.
- Verificación en vivo contra DEV.

## Hecho
- Migración `V6__user_profile.sql`: tabla `user_profile` (`user_id` PK = `sub` del JWT, sin FK -- mismo criterio que `projects.owner_ref`), `avatar_key`/`avatar_content_type` nullable, 3 columnas booleanas de preferencias (`notify_email`/`notify_product_news`/`notify_save_reminders`, default `true`), `updated_at`. Una fila solo nace en el primer `PATCH`/`POST` real -- `GET /api/account/profile` devuelve los defaults sin crear nada.
- `POST /api/account/avatar` (autenticado, body crudo -- mismo estilo que `MobReferenceImageController`, no multipart) / `GET /api/account/avatar/{userId}` (público, sin `Authorization`). Solo PNG/JPEG, máx. 5MB (menor que imágenes de referencia, 10MB).
  - **Desviación deliberada del AC tal como estaba escrito**: "recuperable vía la URL pública de MinIO" se implementó como recuperable vía `/api/account/avatar/{userId}` (nuestra propia API), NUNCA una URL de MinIO directa -- mismo patrón exacto que `thumbnailKey`/imágenes de referencia (023/024): la key interna de S3 nunca se expone al cliente.
- `GET /api/account/profile` / `PATCH /api/account/preferences` (autenticados). Las preferencias solo se persisten -- no afectan ningún envío real de correo todavía, tal como estaba explícitamente fuera de alcance.
- `POST /api/internal/users/{userId}/purge-projects`: autenticado por `X-Internal-Secret` (comparación en tiempo constante) contra `galgoth.internal.secret`, nunca JWT. Soft-deletea TODOS los proyectos del usuario (públicos y privados) y borra su `user_profile`. Rechazo con secreto incorrecto/ausente es `401` -- el código correcto para "no autenticado" en un endpoint servidor-a-servidor, no hay nada que ocultar como sí ocurre con `403` vs `404` en los recursos de un usuario.
- `GALGOTH_INTERNAL_SECRET`: nueva variable de entorno, agregada a `docker-compose.{dev,qa,prod}.yml` y `.env.*.example`. **Hallazgo real post-merge**: el valor real nunca se configuró en la VM al mergear -- el primer deploy de `dev` con este ticket falló (`docker compose` rechazó el env faltante). Corregido generando y colocando un secreto real de 64 caracteres hexadecimales en `deploy/.env.{dev,qa,prod}` de la VM (uno distinto por ambiente) antes del siguiente deploy; el mismo valor de DEV deberá coordinarse con auth-core-mc al implementar su ticket `064`.
- Suite completa: backend 474/474, frontend 765/765, ambos en verde.
- **Verificación en vivo contra DEV** (2 cuentas de prueba desechables, creadas y eliminadas vía SQL al terminar): `GET /api/account/profile` sin fila previa devuelve los defaults; `PATCH /api/account/preferences` persiste y se refleja en el `GET` siguiente; subir un avatar real (PNG 1x1) y descargarlo sin `Authorization` devuelve los mismos bytes; `purge-projects` con secreto incorrecto responde `401` sin tocar nada; con el secreto correcto soft-deletea el proyecto del usuario (confirmado `404` después, incluso para su propio dueño) y borra su `user_profile` (el siguiente `GET /profile` vuelve a los defaults).
- **Nota de secuencia real**: el propio ticket decía "Depende de: nada nuevo -- reutiliza ... el mecanismo de `ProjectAccessGuard`/JWT ya construidos (tickets 077/084/085)" -- **085 en realidad no estaba construido todavía** cuando se escribió ese texto (se implementó justo después, en paralelo, al detectarse el hueco). En la práctica este ticket no dependía de 085 para nada de lo que implementa (avatar/preferencias/purge son independientes del guard de acceso a proyectos), así que no hubo bloqueo real -- se corrige acá la nota original, que era optimista/incorrecta.
