# 105 — Fixture de integración obligatorio: benchmark Carcomido end-to-end

## Objetivo
Nace de `docs/definiciones/anatomia-por-capas-generacion-mobs.md` (HU-5, HU-8). Cierra la epic de anatomía y textura por capas: corre el pipeline completo (referencia → geometría por capas → textura V2 → `.bbmodel` → validación FMM) contra el benchmark Carcomido, de punta a punta, como criterio de aceptación de toda la implementación — no como test opcional.

**Depende de:** 097, 098, 099, 100, 101, 102, 103, 104 (todos). Es el último ticket de la secuencia — valida que el conjunto funciona junto, no solo cada pieza por separado.

## Alcance
**Incluye:**
- Fixture de integración con la imagen de referencia real del Carcomido (confirmar si ya existe en el proyecto o aportarla — pregunta abierta señalada en el documento de definición).
- Test de integración que corre el pipeline completo end-to-end contra ese fixture, y adjunta el `ModelGenerationQualityReport` (ticket 104) resultante como evidencia versionada.
- Verificación explícita, no asumida, de los criterios visuales del documento original: cabeza grande cúbica, mandíbula, brazos largos, manos grandes con garras, ropa desgarrada con jirones en hombros/piernas, faldón, piel verde/gris, grietas violeta, ojos violeta — vía `featureCoverage` + inspección del modelo resultante, no solo "se ve mejor".
- Verificación de que el resultado usa HU-8 (continuidad de textura) según la estrategia que haya ganado la validación empírica del ticket 102.
- Verificación de que no hay código específico hardcodeado para "Carcomido" en ninguno de los componentes — el fixture prueba generalización, no un caso especial.

**No incluye:**
- Nueva funcionalidad — este ticket es puramente de verificación end-to-end de lo ya construido en 097-104.

## Criterios de aceptación (TDD)
- Dado el fixture Carcomido, cuando se corre el pipeline completo, entonces el test pasa en verde y el reporte de calidad queda adjunto como evidencia.
- Dado el modelo resultante, cuando se inspecciona `featureCoverage`, entonces las categorías principales del personaje (garras, ropa desgarrada, mandíbula, grietas emisivas) están representadas — se documenta explícitamente cualquiera que no lo esté, con la razón.
- Dado el modelo resultante, cuando se exporta a `.bbmodel`, entonces pasa `FmmCompatibilityValidator` sin issues críticos.
- Dado el código de los componentes 097-104, cuando se revisa, entonces no existe ninguna rama condicional específica para "Carcomido" — la mejora es generalizable.
- Suite completa del proyecto (backend + frontend) sigue en verde tras integrar todos los tickets de la epic.

## Hecho
