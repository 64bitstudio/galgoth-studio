# 090 — Marca real: ícono en el sidebar + favicon

## Objetivo
Marco compartió el arte final de marca (ícono del cubo creeper con
efecto neón, y una variante horizontal con el wordmark "Galgoth
Studio") para reemplazar el favicon placeholder abstracto que quedó del
ticket 024 (diseño genérico, nunca fue la marca real) y para que la app
tenga el ícono de marca visible en el sidebar — hoy el header del
sidebar es solo texto plano, sin ningún ícono (gap ya documentado en el
Javadoc de `GSidebar.vue`, no relacionado al de "Usuario").

## Alcance
- **Sí incluye:**
  - `favicon.png` (256×256) + `apple-touch-icon.png` (180×180),
    generados desde el ícono cuadrado que compartió Marco — reemplazan
    `favicon.svg` (placeholder abstracto, borrado).
  - `GSidebar.vue`: el header ("Galgoth Studio") gana el ícono de marca
    a la izquierda del texto, en el estado expandido.
- **No incluye:**
  - El ícono en el estado colapsado del sidebar (68px de ancho no
    alcanza para ícono + botón de colapsar lado a lado sin rehacer el
    layout — se deja para un ajuste de diseño aparte si Marco lo pide).
  - Actualizar el logo de Login/Register (`galgoth-logo.png`, ticket
    081) — ya usa la marca real, no el placeholder; no se tocó.
  - La variante horizontal (ícono + wordmark completo) que también
    compartió Marco — no se encontró un lugar donde encaje sin verse
    forzado (el sidebar es demasiado angosto para esa proporción); queda
    sin usar por ahora, documentado aquí en vez de forzarla en algún
    lado.

## Criterios de aceptación
- El favicon real aparece en la pestaña del navegador.
- El sidebar expandido muestra el ícono junto a "Galgoth Studio".
- Suite completa del frontend en verde.
- Verificación en vivo contra DEV.

## Hecho
- `favicon.png`/`apple-touch-icon.png` generados desde el arte real de
  Marco, `favicon.svg` (placeholder) borrado, `index.html` actualizado.
- `GSidebar.vue`: ícono agregado junto a "Galgoth Studio" en el estado
  expandido.
- Suite completa del frontend: 765/765 en verde.
- **Verificación en vivo**: `favicon.png`/`apple-touch-icon.png`
  confirmados accesibles (200 OK) en
  `https://studio-dev.galgoth.64bitstudio.com/`.
