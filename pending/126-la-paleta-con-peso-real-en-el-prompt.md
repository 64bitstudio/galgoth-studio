# 126 — La paleta deja de ser una línea decorativa en el prompt

## Objetivo
Referencia: `docs/definiciones/fidelidad-de-color-en-la-generacion-de-texturas.md` (HU-3), aprobado con VoBo del PO.

Hoy la paleta entra al prompt como **una sola línea, en la primera posición, sin ningún lenguaje imperativo** (`TextureSheetPromptComposer.java:73-86`):

```
Paleta: dominante #4A3B2C, acento #8FA05B.
```

En el mismo prompt, las instrucciones sobre llenar los rectángulos sí usan mayúsculas y verbos fuertes ("Pintá CADA región COMPLETA… NO dibujes marcos, bordes oscuros"). La paleta no tiene ni un "usá exclusivamente estos colores", ni repetición, ni refuerzo al final.

El ticket 113 ya midió que reforzar el prompt **ayuda pero no alcanza** (las bandas negras bajaron ~45 % y quedó 37 % de caras con banda). Así que esto es una mejora barata y esperable, **no la solución** — la solución de fondo depende del experimento del 124.

**Se puede adelantar** sin esperar al 124. Se beneficia del 125 (más colores que ofrecer), pero no lo necesita.

## Alcance
**Incluye:**
- Reescribir la sección de paleta del prompt con lenguaje imperativo y en una posición con peso real.
- Medir el efecto con la métrica del 123 antes y después, sobre el mismo mob.

**No incluye:**
- Derivar la paleta (ticket 125) ni hacerla cumplir por post-proceso (ticket 127).
- Tocar el resto del prompt: el 113 ya ajustó la parte de bordes y está medida.

## Criterios de aceptación (TDD)
- Dado un sheet, cuando se compone el prompt, entonces la paleta aparece con instrucción imperativa explícita y con sus colores, verificable por test sobre el texto generado.
- Dado el prompt resultante, entonces las instrucciones de borde del ticket 113 siguen presentes e intactas — no se pisa una mejora ya medida con otra.
- **Verificación en vivo con número**: regenerar la textura de un mob y comparar la distancia de croma del 123 contra la corrida previa. Si no mejora, se dice; el ticket se cierra igual con el número real, porque el valor de este cambio es acotado por diseño.

## Hecho
