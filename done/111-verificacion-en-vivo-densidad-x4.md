# 111 — Verificación en vivo: ¿a X4 la IA tiene dónde pintar?

## Objetivo
Nace de `docs/definiciones/densidad-de-texel-y-resolucion-de-textura.md` (riesgo declarado "no verificado todavía"). Los tickets 109 y 110 arreglaron el motor y lo demostraron con números *internos* (área por cara en el benchmark con proveedores mock). Lo que NINGUNO de los dos puede probar es la pregunta que motivó todo: con caras de 16-1536 px² en vez de 1-8, ¿el generador de imagen produce contenido estructurado, o produce el mismo ruido a mayor resolución?

Precedente del proyecto (059-065, 102): contra la API real, solo vale lo que se ve corriendo.

**Depende de:** 109 y 110 desplegados en `studio-dev`.

## Alcance
**Incluye:**
- Regenerar la textura del mismo mob real (`Carcomido v2`, 46 cuboides / 15 bones) en `studio-dev`, con la densidad nueva.
- Comparar contra la corrida previa con los MISMOS números: tamaño de atlas, distribución de área por cara y aspecto del atlas compuesto.
- Registrar el resultado en este ticket, sea cual sea — incluido "no mejoró", que es un resultado válido y la razón por la que el documento dejó el riesgo declarado.

**No incluye:**
- Cambios de código. Si la verificación sale mal, el hallazgo alimenta una definición nueva, no un parche acá.

## Criterios de aceptación
- Dado el mob regenerado, cuando se inspecciona su atlas, entonces se registra su tamaño real y la distribución de área por cara, comparados con los de la corrida anterior (32×256, mediana 8 px², 178 caras bajo 16 px²).
- Dado el atlas resultante, cuando se lo compara visualmente con el anterior, entonces se documenta si el contenido es estructurado o sigue siendo ruido — con evidencia, no impresión.
- Dado cualquier resultado, cuando se cierra el ticket, entonces la conclusión queda escrita sin adornos: si la hipótesis del documento falló, se dice que falló.

## Hecho
Verificado el 16-sep-2026 contra `studio-dev`, generando un mob nuevo (`Carcomido v3`) con la MISMA imagen de referencia del benchmark, densidad Máxima (default) y detalle Medio — el flujo completo de usuario: vision → geometría → textura de modelo completo.

**Nota de método**: la densidad se aplica al asignar UV, o sea durante la generación de GEOMETRÍA. Regenerar solo la textura del mob viejo habría reusado su UV de 32×256 y no habría probado nada. Por eso se generó un mob nuevo.

### Resultado medido (mismo personaje, misma referencia)

| | Carcomido v2 (X1 efectivo) | Carcomido v3 (X4 real) |
|---|---|---|
| Atlas | 32 × 256 px | **256 × 1024 px** (32× el área) |
| Cuboides / bones | 46 / 15 | 39 / 15 |
| Área mínima por cara | 1 px² | **16 px²** |
| Mediana | 8 px² | **128 px²** |
| p75 | 16 px² | **384 px²** |
| Máxima | 96 px² | **1536 px²** |
| Caras bajo 16 px² | 178 de 276 (64%) | **0 de 202** |
| Caras pintadas por IA | 236 | 202 |
| FMM | compatible | compatible |

La mediana por cara se multiplicó por 16. Ninguna cara no degenerada quedó por debajo del mínimo legible.

### Lo que no puede decir una métrica: el contenido
Medido sobre el atlas propuesto (256×1024): cobertura alpha 18,3% del lienzo (el packing deja huecos, es esperado), luminancia media 51,5 con desviación 36,5 — o sea variación real, no plano.

Y mirándolo: **el contenido dejó de ser ruido**. Cada cara tiene una textura con estructura — base gris-parda moteada y **vetas violetas con forma de grieta** (ramificadas, con dirección y continuidad dentro de la cara), no píxeles violetas dispersos al azar como en v2. La hipótesis del documento se sostiene: el cuello de botella era el destino (téxeles disponibles), no la fuente.

### Hallazgo honesto que NO es un éxito
La paleta salió **bastante más oscura que la referencia**. El Carcomido real es piel gris-verdosa con tela marrón y grietas violeta; lo generado lee como carbón/gris muy oscuro con violeta. O sea: el problema de *resolución* se resolvió y quedó expuesto un problema distinto, de *fidelidad de color*, que antes no se podía ni ver porque no había píxeles suficientes para juzgarlo.

Esto no invalida el cambio — es progreso real y medible — pero tampoco es "la textura ya está resuelta". Candidato a definición futura, no a parche: el prompt del sheet declara la paleta dominante/acento del `TexturePlan`, así que el lugar a mirar es si esa paleta se está calculando bien desde la referencia y si el estilo "Fiel a la referencia" la está respetando.

### Otro hallazgo, menor pero real
32 caras quedaron `unpainted` porque son degeneradas (área cero). Son cuboides con una dimensión colapsada — el caso legítimo del ticket 064. Vale revisar en otro momento si parte de esas degeneradas son artefacto del redondeo a unidades enteras en `BoxUvMath.boxSizeAxis` (que redondea ANTES de multiplicar por la densidad, así que una pieza de 0,4 unidades colapsa a 0 sin importar la densidad). No se tocó acá porque este ticket es de verificación, no de código.

### Inconsistencia de UI detectada de paso
El rótulo visible del control sigue diciendo "Resolución de textura" y el texto de ayuda "Mayor resolución ofrece más detalle", mientras las opciones ya son Estándar/Alta/Máxima (densidad). El ticket 109 cambió el `label` del `GSelect` pero no ese markup. Corregido en el mismo PR que cierra este ticket.
