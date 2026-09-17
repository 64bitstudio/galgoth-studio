# 120 — `MobGenerationServiceStreamingTest` falla en CI por orden de etapas, y localmente nunca

## Objetivo
En el build #1 del PR #176, CI reportó `595 tests completed, 1 failed`:

```
MobGenerationServiceStreamingTest > las_2_etapas_nuevas_aparecen_como_eventos_reales_antes_de_completado_AC_ticket_038() FAILED
java.lang.AssertionError at MobGenerationServiceStreamingTest.java:250
```

La línea 250 es la aserción de **orden**, no la de presencia:

```java
assertThat(stages).contains("preparando_resultado", "validando_geometria");   // linea 245, PASÓ
assertThat(stages.indexOf("preparando_resultado")).isLessThan(stages.indexOf("validando_geometria")); // linea 250, FALLÓ
```

O sea: las dos etapas se emitieron, pero en el orden equivocado. En el build #2 del mismo PR el test pasó, así que es intermitente y depende de la carga de la máquina.

No lo causa el cambio del ticket 118 (que solo toca la matemática de footprint UV y no puede reordenar la emisión de eventos), y la clase ya resetea sus mocks en `@BeforeEach`, así que **tampoco es la fuga de estado documentada en el ticket 097**. Localmente pasa siempre, incluso corriendo la clase aislada tres veces seguidas.

El riesgo de dejarlo es doble: bloquea merges al azar, y entrena al equipo a re-lanzar builds sin mirar — que es justo como se deja pasar un fallo real.

## Alcance
**Incluye:**
- Determinar si el orden de emisión de eventos es realmente no determinista en el pipeline (`MobGenerationService.runPipeline`) o si es la LECTURA la que no lo garantiza (`findByJobIdAndSeqGreaterThanOrderBySeqAsc` ordena por `seq`: hay que verificar que dos eventos no puedan compartir `seq`, o que `seq` no se asigne de forma concurrente).
- Si el orden es una garantía real del producto (el comentario del test dice que el frontend depende de él para avanzar "current/done" de forma monótona), entonces hacerlo determinista de verdad, no solo estabilizar el test.
- Si NO es una garantía, cambiar el test para que verifique lo que sí se garantiza — dejándolo escrito.

**No incluye:**
- Wirear la suite E2E en Jenkins (gap conocido y aceptado, ver cabecera del `Jenkinsfile`).

## Criterios de aceptación (TDD)
- Dado el pipeline de generación corriendo, cuando se leen sus eventos, entonces `preparando_resultado` precede siempre a `validando_geometria` y este a `completado` — o queda documentado que ese orden no es una garantía y el test se ajusta a la real.
- La causa queda identificada por evidencia (no "se estabilizó solo"): si dos eventos pueden compartir `seq`, se demuestra; si la emisión es concurrente, se señala dónde.
- El test deja de fallar de forma intermitente en CI. **No vale** marcarlo `@Disabled`, agregarle reintentos ni relajar la aserción de orden si el orden sí es una garantía (regla 8: sin parches silenciosos).

## Hecho
