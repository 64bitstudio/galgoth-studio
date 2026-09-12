# REQUERIMIENTO — Rediseño de Inicio de Galgoth Studio

Quiero rediseñar la pantalla **Inicio** de Galgoth Studio tomando como fuente de verdad visual la **imagen de referencia adjunta**.

Este requerimiento debe trabajarse con **fidelidad visual estricta**.

La referencia NO es inspiración.  
La referencia NO es una guía aproximada.  
La referencia representa el diseño que debe implementarse.

---

# REGLA DE AUTORIZACIÓN — MUY IMPORTANTE

En esta primera etapa:

- NO modifiques código.
- NO crees archivos de implementación.
- NO hagas commits.
- NO hagas push.
- NO crees ramas.
- NO abras Pull Requests.
- NO generes PR.
- NO ejecutes cambios automáticos.
- NO implementes componentes todavía.

Puedes:

- inspeccionar el proyecto;
- revisar la implementación actual de Inicio;
- revisar componentes existentes;
- revisar estilos;
- revisar el design system;
- revisar rutas;
- revisar assets;
- revisar dependencias;
- comparar la implementación actual con la referencia;
- proponer el plan exacto de implementación.

Después debes detenerte y esperar mi autorización.

Solo podrás comenzar a modificar código cuando yo escriba explícitamente:

> AUTORIZO IMPLEMENTACIÓN LOCAL

Incluso después de esa autorización:

- trabajarás exclusivamente en local;
- NO harás commit automáticamente;
- NO harás push;
- NO crearás PR;
- NO abrirás ramas remotas;
- NO publicarás nada.

Cuando termines la implementación local, debes detenerte nuevamente para que yo revise el resultado.

---

# OBJETIVO

Reconstruir la pantalla **Inicio** para que visualmente coincida lo más cerca posible con la imagen de referencia adjunta.

La prioridad es:

1. Fidelidad visual.
2. Reutilización de los assets proporcionados.
3. Consistencia con Galgoth Studio.
4. Funcionalidad.
5. Responsive sin destruir el diseño desktop.

No quiero una reinterpretación del diseño.

---

# ASSETS EXISTENTES

Los fondos/gráficos necesarios para los botones/cards principales ya existen en:

```text
requerimientos/1_req_rediseno_inicio/assets/
```

Debes inspeccionar esa carpeta antes de proponer cualquier implementación.

## Regla obligatoria

Si el asset visual ya existe ahí:

**DEBES USARLO.**

No debes:

- recrearlo con CSS;
- reemplazarlo con una aproximación;
- generar otra ilustración;
- sustituirlo con un icono genérico;
- cambiar sus colores;
- cambiar su composición;
- reinterpretarlo.

El diseño de la referencia fue pensado alrededor de esos assets.

Debes identificar exactamente qué asset corresponde a cada sección.

---

# FUENTE DE VERDAD VISUAL

La captura adjunta representa el resultado deseado.

Debes comparar contra ella:

- layout;
- posiciones;
- tamaños;
- proporciones;
- spacing;
- alineación;
- cards;
- borders;
- radius;
- colores;
- tipografía;
- jerarquía;
- iconografía;
- fondos;
- estados;
- densidad;
- distribución horizontal y vertical.

No considero correcta una implementación que solamente “se parezca”.

Debe reconocerse inmediatamente como la misma pantalla.

---

# ESTRUCTURA GENERAL

La pantalla debe conservar el sidebar actual de Galgoth Studio.

Conceptualmente:

```text
┌───────────────┬─────────────────────────────────────────────────┐
│               │                                                 │
│ Galgoth       │ Buenos días                                     │
│ Studio        │ ¿Qué quieres crear hoy?                         │
│               │                                                 │
│ Inicio        │ [ Crear mob con IA ] [ Crear nuevo proyecto ]  │
│ Proyectos     │                                                 │
│ Explorar      │ Continuar trabajando                            │
│ Plantillas    │ [ mob ] [ mob ] [ mob ]                        │
│               │                                                 │
│               │ Proyectos recientes                             │
│ Config        │ [ proyecto ] [ proyecto ]                       │
│ Usuario       │                                                 │
└───────────────┴─────────────────────────────────────────────────┘
```

---

# 1. SIDEBAR

El sidebar debe conservar el lenguaje visual actual de Galgoth Studio.

Debe mostrar:

```text
Galgoth Studio

Inicio
Mis proyectos
Explorar
Plantillas

...

Configuración
Usuario
```

En esta pantalla `Inicio` debe aparecer seleccionado.

## Selected state

Debe utilizar:

- fondo verde oscuro/translúcido;
- accent mint;
- borde/acento lateral;
- icono mint;
- texto mint.

Respetar exactamente la referencia.

---

# 2. HEADER DE INICIO

Debe existir:

```text
Buenos días

¿Qué quieres crear hoy?
```

Con jerarquía visual equivalente a la referencia.

No agregar:

- breadcrumbs;
- cards extra;
- banners;
- estadísticas;
- textos que no aparecen en la referencia.

---

# 3. CARD PRINCIPAL — CREAR UN MOB CON IA

Esta es la acción principal de Inicio.

Debe ser la card visualmente dominante.

Contenido aproximado:

```text
✦ CREA CON IA

Crear un mob con IA

Convierte una imagen en un modelo
de Minecraft

[ Empezar ahora → ]
```

## Importante

Utilizar el fondo correspondiente de:

```text
requerimientos/1_req_rediseno_inicio/assets/
```

La ilustración forma parte de la composición.

No intentar reconstruirla manualmente.

## Apariencia

Debe tener:

- borde mint;
- fondo oscuro/verde;
- ilustración integrada;
- CTA mint;
- composición igual a la referencia;
- hover sutil.

### Acción

`Empezar ahora` debe navegar al flujo IA existente:

```text
Referencia
→ Configuración
→ Generación
→ Resultado
```

No crear un flujo nuevo si ya existe.

---

# 4. CARD — CREAR NUEVO PROYECTO

A la derecha debe existir la card:

```text
NUEVO PROYECTO

Crear nuevo proyecto

Organiza varios mobs dentro de un
mismo proyecto

[ Crear proyecto → ]
```

Utilizar también el asset correspondiente ubicado en:

```text
requerimientos/1_req_rediseno_inicio/assets/
```

Esta acción es secundaria respecto a Crear mob con IA.

Visualmente:

```text
Crear mob con IA = PRIMARY
Crear nuevo proyecto = SECONDARY
```

No invertir esa jerarquía.

---

# 5. CONTINUAR TRABAJANDO

Debajo debe existir:

```text
Continuar trabajando                       Ver todos →
```

Mostrar mobs recientes.

En la referencia aparecen tres cards horizontales.

Cada card debe mostrar:

- thumbnail real;
- nombre;
- fecha de última edición;
- estado;
- menú `⋮`.

Ejemplo:

```text
[thumbnail]  Carcomido
             Editado hace 2 horas

                         En progreso
                         ⋮
```

Estados posibles:

```text
En progreso
Draft
Listo
```

Utilizar el sistema visual existente de badges.

---

# 6. PROYECTOS RECIENTES

Debajo:

```text
Proyectos recientes                         Ver todos →
```

Cards horizontales de proyectos.

Cada una debe mostrar hasta tres previews de mobs.

Ejemplo:

```text
[mob] [mob] [mob]

Galgoth
3 mobs · Editado hoy

                         ⋮
```

Si hay más de tres mobs:

```text
+N
```

según el patrón ya definido para `ProjectCard`.

---

# 7. PROPORCIONES

La distribución principal debe parecerse a la referencia.

Aproximadamente:

```text
Card IA          ~55%
Nuevo proyecto   ~45%
```

No utilizar cards pequeñas centradas dejando enormes espacios muertos.

El contenido debe aprovechar el ancho disponible.

---

# 8. DENSIDAD VISUAL

Esta pantalla no debe parecer:

- dashboard SaaS vacío;
- formulario CRUD;
- scaffold;
- panel administrativo.

Debe sentirse como una herramienta creativa.

La referencia tiene:

- contenido visual;
- previews;
- jerarquía fuerte;
- poco espacio desperdiciado;
- cards horizontales;
- acciones claras.

---

# 9. DESIGN SYSTEM

Mantener el sistema visual actual de Galgoth Studio.

Referencia aproximada:

```css
--bg: #0B0F14;
--panel: #111820;
--surface: #171F29;
--accent: #48E5A0;
```

No inventar una segunda paleta.

El verde menta se utiliza para:

- estado activo;
- CTA;
- selección;
- IA;
- confirmación.

---

# 10. COMPONENTES

Antes de implementar, inspeccionar componentes existentes.

Preferir reutilizar o extender:

```text
AppButton
IconButton
ProjectCard
MobCard
StatusBadge
SidebarItem
ContextMenu
Tooltip
```

No duplicar componentes si ya existe una implementación equivalente.

Si hace falta crear componentes específicos de Inicio, proponer algo equivalente a:

```text
HomeHeroAiCard
HomeNewProjectCard
RecentMobCard
RecentProjectCard
HomeSectionHeader
```

Pero NO implementarlos todavía.

Primero necesito aprobar tu propuesta.

---

# 11. RESPONSIVE

La captura desktop es la fuente principal de verdad.

Primero conseguir fidelidad en escritorio.

Después definir adaptación responsive.

No modificar la versión desktop para simplificar mobile.

Desktop-first.

---

# 12. INTERACCIONES

Preparar estados:

```text
default
hover
focus-visible
active
loading
```

Especialmente para:

- Empezar ahora;
- Crear proyecto;
- Ver todos;
- cards;
- menú `⋮`.

No usar controles nativos sin estilizar.

---

# 13. DATOS REALES

Las secciones:

```text
Continuar trabajando
Proyectos recientes
```

deben consumir los datos reales que ya tenga la aplicación.

No hardcodear:

```text
Carcomido
Áugur
Tejedora
Galgoth
```

salvo fixtures/demo existentes.

La captura muestra contenido conceptual.

La UI debe representar los datos reales.

---

# 14. ESTADOS VACÍOS

Si no existen mobs recientes:

No ocultar completamente la sección de forma arbitraria.

Proponer un empty state consistente.

Ejemplo:

```text
Aún no has creado ningún mob.

[ ✦ Crear mi primer mob con IA ]
```

Si no existen proyectos:

```text
Todavía no tienes proyectos.

[ + Crear proyecto ]
```

El diseño de estos estados debe seguir Galgoth Studio.

---

# 15. VALIDACIÓN VISUAL OBLIGATORIA

Cuando posteriormente autorice la implementación local, antes de declarar Done:

1. ejecutar la aplicación;
2. abrir Inicio;
3. utilizar un viewport igual o equivalente al screenshot;
4. tomar captura;
5. compararla lado a lado con la referencia;
6. corregir:
   - posiciones;
   - widths;
   - heights;
   - paddings;
   - gaps;
   - font sizes;
   - borders;
   - radii;
   - colores;
   - alineaciones;
7. repetir hasta obtener una similitud visual alta.

Si es posible, utilizar overlay/difference visual.

No declarar Done después de una sola pasada.

---

# 16. NO HACER

Durante esta etapa de análisis:

- NO editar código;
- NO crear componentes;
- NO modificar CSS;
- NO instalar dependencias;
- NO modificar backend;
- NO modificar DB;
- NO crear commits;
- NO hacer push;
- NO crear ramas;
- NO abrir PR;
- NO publicar cambios;
- NO generar assets nuevos.

Durante la futura implementación local tampoco:

- NO commit;
- NO push;
- NO PR;

salvo que yo lo autorice posteriormente de manera explícita.

---

# 17. PRIMER ENTREGABLE — SOLO ANÁLISIS

En tu siguiente respuesta quiero ÚNICAMENTE:

## A. Archivos identificados

Lista de archivos actuales relacionados con Inicio.

Ejemplo:

```text
frontend/src/views/HomeView.vue
frontend/src/components/...
...
```

Usa las rutas reales del proyecto.

## B. Assets encontrados

Lista exacta de archivos encontrados en:

```text
requerimientos/1_req_rediseno_inicio/assets/
```

y qué parte del diseño corresponde a cada uno.

## C. Diferencias actuales vs referencia

| Área | Estado actual | Diseño requerido | Cambio |
|---|---|---|---|

## D. Componentes reutilizables

Qué componentes existentes pueden reutilizarse.

## E. Archivos que sería necesario modificar

Lista exacta, pero **sin modificarlos todavía**.

## F. Plan de implementación local

En pasos pequeños y ordenados.

## G. Riesgos

Solo riesgos concretos.

Ejemplo:

```text
Asset ratio distinto
Datos recientes no disponibles
Componente ProjectCard actual no permite layout horizontal
```

## H. Confirmación

Finaliza exactamente con:

> Análisis completado. No he modificado ningún archivo, no he creado commits, ramas ni Pull Requests. Espero tu autorización explícita para comenzar la implementación local.

---

# GATE DE AUTORIZACIÓN

Después de entregar el análisis:

**DETENTE.**

No continúes automáticamente.

No implementes nada aunque consideres que el cambio es sencillo.

Debes esperar a que yo escriba explícitamente:

> AUTORIZO IMPLEMENTACIÓN LOCAL

Solo entonces podrás comenzar a modificar archivos localmente.

Incluso después de esa autorización:

```text
Código local: SÍ
Ejecutar tests: SÍ
Levantar app local: SÍ
Tomar screenshots: SÍ

Commit: NO
Push: NO
Branch remota: NO
Pull Request: NO
Deploy: NO
```

Hasta nueva autorización.
