# 024 — Asset-service: subida de imagen de referencia

**Milestone:** M4 · **Depende de:** 022 · **HUs:** HU-10

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (Épica C). Endpoint y UI para subir la imagen de concept art de referencia, asociada a un mob (`reference_images`), como primer paso del wizard de generación IA.

## Criterios de aceptación (TDD)
- Dado el paso "Referencia" del wizard, cuando se sube una imagen válida, entonces se persiste en storage S3-compatible (MinIO) y queda referenciada al mob.
- Dado un archivo con formato o tamaño no soportado, cuando se intenta subir, entonces se rechaza con un mensaje claro (límites concretos definidos en este ticket, no en el documento de definición).
- Dado una imagen subida, cuando se consulta el mob, entonces la referencia incluye ancho/alto/content-type persistidos.
