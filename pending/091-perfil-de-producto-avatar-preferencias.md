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
