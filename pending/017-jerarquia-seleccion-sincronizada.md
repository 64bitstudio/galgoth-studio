# 017 — Jerarquía + selección sincronizada

**Milestone:** M2 · **Depende de:** 016 · **HUs:** HU-05 · **Épica:** 015

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (layout `hierarchy | viewport | inspector`, Visual Contract punto 7). Panel lateral con el árbol de bones/cuboides, sincronizado bidireccionalmente con la selección del viewport (016).

## Criterios de aceptación (TDD)
- Dado un `MobProjectModel` cargado, cuando se renderiza el panel de jerarquía, entonces muestra bones y sus cuboides hijos en la estructura correcta.
- Dado un clic en un nodo del árbol, cuando se selecciona, entonces el elemento correspondiente se resalta también en el viewport 3D.
- Dado un clic en un cuboid del viewport, cuando se selecciona, entonces el nodo correspondiente se resalta en el árbol de jerarquía.
