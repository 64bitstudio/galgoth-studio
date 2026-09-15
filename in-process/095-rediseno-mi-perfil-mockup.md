# 095 — Rediseño de "Mi Perfil" para apegarse al mockup original

## Objetivo
El ticket 093 (Pantalla Usuario) implementó "Mi Perfil" funcionalmente completo,
pero visualmente se alejó bastante del mockup original que Marco compartió:
layout de una sola columna en vez de 2 columnas, cabecera sin tarjeta/avatar
real, Seguridad expandida inline en vez de filas compactas que abren modal, y
sin menús "···" en cuentas conectadas/sesiones. Marco lo señaló explícitamente
comparando el mockup contra el estado actual y pidió apegarse más al diseño.

Este ticket cubre solo la reestructuración VISUAL/DE INTERACCIÓN de
`UserView.vue` y el nuevo trabajo puntual que esa reestructuración requiere
(dos modales nuevos, desvincular proveedor). Toda la lógica de negocio ya
construida en el 093 (accountApi, productProfileApi, cambio de correo, etc.)
se reutiliza tal cual — no se re-implementa.

## Decisiones ya confirmadas por Marco (AskUserQuestion, no se re-preguntan)
1. **Seguridad**: filas compactas que abren un modal/vista aparte (no el
   patrón inline-expandido actual) — "Cambiar contraseña" y "Ver sesiones"
   pasan a ser dos modales nuevos.
2. **Nombre/Apellidos**: se mantienen como dos campos separados (no se
   fusionan en "Nombre completo" — evita tocar el modelo de datos de
   auth-core-mc).
3. **Ubicación en sesiones**: sí se agrega ciudad/país reales — cubierto por
   el ticket 070 de auth-core-mc (GeoLite2, campos `city`/`country` en
   `SessionSummary`, degradación a `null` si no hay ubicación).
4. **Servicio de geolocalización**: base de datos local MaxMind GeoLite2 (ya
   implementado en auth-core-mc, ticket 070).
5. **Desvincular cuenta social**: se construye ahora (no se pospone) —
   backend ya tiene `DELETE /connected-providers/{provider}` (auth-core-mc
   ticket 069, mergeado en PR #128).

## Criterios de aceptación (TDD)
- Layout de 2 columnas (Información personal + Cuentas conectadas a la
  izquierda; Seguridad + Preferencias a la derecha), colapsando a 1 columna
  en móvil.
- Cabecera con avatar real (foto si existe, iniciales si no), nombre,
  badge de rol, "Miembro desde" con fecha, botones "Editar perfil" y
  "Cambiar foto".
- Panel "Seguridad" compacto: fila "Cambiar contraseña" (o "Establecer
  contraseña" si `!hasPassword`, reusando `SetPasswordDialog`/flujo
  existente) que abre un modal nuevo; fila "Verificación en dos pasos" con
  pill "Próximamente" (sin cambio funcional); fila "Sesiones activas" que
  abre un modal nuevo con la lista completa (dispositivo, navegador,
  ciudad/país si están disponibles, "Activa ahora"/fecha, "Actual", menú
  "···" con "Cerrar sesión" vía `GMenu.vue`).
- "Cuentas conectadas": cada proveedor vinculado muestra menú "···"
  (`GMenu.vue`) con opción "Desvincular", llamando al nuevo
  `accountApi.unlinkProvider(provider)` → `DELETE /connected-providers/{provider}`;
  error 409 (`cannot_unlink_last_login_method`) se muestra como mensaje
  claro, no como error genérico.
- "Preferencias" conserva los toggles ya funcionales (093); tema oscuro
  sigue deshabilitado/fijo (la app no tiene tema claro implementado — sin
  cambio de alcance aquí).
- "Zona de peligro" se mantiene igual (093 ya la implementó completa).
- Ningún dato/llamada ya probada en 093 cambia de comportamiento — solo la
  capa visual/estructural alrededor.
- Tests de componente actualizados/nuevos para: apertura de los 2 modales,
  flujo de desvincular proveedor (éxito y error 409), render de
  ciudad/país cuando el backend los manda y cuando no (null).

## Hecho
[Se completa al cerrar el ticket.]
