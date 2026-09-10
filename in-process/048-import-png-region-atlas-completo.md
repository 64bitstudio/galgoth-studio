# 048 — Import de PNG: región seleccionada y atlas completo

**Milestone:** M8 · **Depende de:** 046, 047 · **HUs:** HU-28 · **Épica:** J (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Nace de `docs/definiciones/galgoth-studio-fase3-textura.md` (Diseño técnico §8, HU-28 — cierra la pregunta abierta que Fase 3 v1 había dejado pendiente). Soporta importar una imagen sobre una región UV seleccionada (con ajuste antes de confirmar) y, nuevo este ciclo, sobre el atlas completo (crop/pad seguro, NUNCA escalado — para no violar el canvas pixel-perfect ya decidido en 047).

## Criterios de aceptación (TDD)
- **(A) Región seleccionada**: dado que tengo una región UV seleccionada y pego una imagen de tamaño distinto, cuando esto ocurre, entonces se me ofrece ajustarla a las dimensiones exactas de la región antes de confirmar; al confirmar, la región queda reemplazada, registrado como un único `TexturePatchCommand` (046) acotado a esa región.
- **(B) Atlas completo, dimensiones exactas**: dado que importo un PNG cuyas dimensiones coinciden exactamente con `TextureDocument.width`/`height` vigentes, cuando lo hago, entonces se muestra una confirmación/diff (mismo patrón visual que el diff Antes/Después de IA) antes de reemplazar el atlas completo.
- **(B) Atlas completo, dimensiones distintas**: dado que importo un PNG de dimensiones distintas, cuando lo hago, entonces se me ofrece ÚNICAMENTE crop (recortar excedente) y/o pad (agregar margen transparente) — **nunca** un camino de escalado/resize, verificado explícitamente (test que confirma que no existe ninguna opción de escalar en la UI ni en el backend de esta operación).
- Dado cualquiera de los casos (A o B), cuando no confirmo explícitamente el ajuste ofrecido, entonces no se aplica nada — el atlas/región permanece sin cambios.
- Dado que confirmo un import de atlas completo (con o sin crop/pad de por medio), cuando se aplica, entonces es EXACTAMENTE UN `TexturePatchCommand` con `rect` = atlas completo — nunca múltiples comandos.
- Dado que el atlas ya está en Estado B (congelado, ver ticket 042 — existe al menos una región `PAINTED`), cuando importo un PNG de dimensiones distintas a las congeladas, entonces se aplica el mismo criterio de crop/pad hacia esas dimensiones fijas — nunca las cambia.
- Exportar la textura como PNG independiente sigue fuera de alcance — este ticket es exclusivamente sobre IMPORT (ninguna UI/endpoint de export de textura se agrega aquí).

## Hecho
