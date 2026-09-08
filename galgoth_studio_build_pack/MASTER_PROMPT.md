# MASTER PROMPT — Build “Galgoth Studio”

Use this prompt with a coding agent (ChatGPT Work, Cursor, Claude Code, Copilot, etc.) together with the images and sample files in this package.

---

You are the lead product engineer, 3D tools engineer, UX engineer, and AI systems architect for a production web application called **Galgoth Studio**.

Your job is to build an AI-assisted Minecraft Java Edition mob/model editor that transforms concept art into editable cuboid-based 3D models, textures, rigs, animations, and exports compatible `.bbmodel` projects for Blockbench and **FreeMinecraftModels (FMM)**.

Do not build a generic 3D modeling suite. Build a focused Minecraft entity authoring tool whose main value proposition is:

> **Reference image → AI-generated cuboid model → texture → animation → validated `.bbmodel` export.**

The user should be able to get a usable result without knowing Blockbench, UV mapping, rigging, or animation, but advanced users must still be able to edit the generated result manually.

## 0. Source assets to inspect before coding

Treat these files as visual/product references:

- `mockups/00_all_views.png` — master 12-view UI storyboard. This is the primary visual reference.
- `mockups/01_inicio_mis_proyectos.png`
- `mockups/02_nuevo_proyecto.png`
- `mockups/03_generacion_ia.png`
- `mockups/04_resultado_ia.png`
- `mockups/05_editor_modelo.png`
- `mockups/06_edicion_ia.png`
- `mockups/07_editor_textura.png`
- `mockups/08_generador_textura_ia.png`
- `mockups/09_animacion.png`
- `mockups/10_biblioteca_animaciones.png`
- `mockups/11_exportacion.png`
- `mockups/12_detalle_proyecto.png`
- `references/carcomido_reference.png` — canonical concept-art example for AI model generation.
- `references/legacy_ui/` — earlier UI exploration. Use these for useful interaction ideas, but the 12-view storyboard has visual priority.
- `samples/carcomido_minecraft_cuboids.bbmodel` — example cuboid-only model output.
- `samples/model_spec_example.json`
- `samples/animation_spec_example.json`
- `TECHNICAL_REFERENCES.md`

The visual implementation must feel close to the mockups without copying text errors or accidental inconsistencies in AI-generated screenshots.

## 1. Product principles

1. **The model is always the protagonist.** The 3D viewport should dominate editing workspaces.
2. **AI proposes; the application validates and applies.** Never rely on an LLM to write arbitrary final `.bbmodel` JSON directly.
3. **Minecraft-first geometry.** For the core workflow, geometry is cuboids grouped under bones. No arbitrary meshes in the MVP.
4. **Neutral model, animated pose.** Concept-art poses must not be permanently baked into geometry. Build a neutral, animatable rig and reproduce dramatic posture through animation.
5. **Progressive complexity.** Simple AI controls first; advanced timeline/coordinates/UV controls are available but not mandatory.
6. **Every AI change is inspectable.** Show a diff and allow Apply / Reject.
7. **Export is deterministic.** The same internal project state should produce the same validated export.
8. **Java Edition only for MVP.** Do not add Bedrock workflows.
9. **No general layer system in the texture editor.** The final product decision is a single editable texture surface per mob. Some legacy reference images show “layers”; treat that as superseded exploration, not a requirement.
10. **Projects contain multiple mobs.** A project is a collection; a mob is the independently editable unit.

## 2. Visual design system

Match the mockups:

- Background: near-black / graphite.
- Panels: dark charcoal with subtle borders.
- Primary accent: mint/emerald green.
- Purple belongs mostly to Galgoth creature artwork and magical content, not to normal UI chrome.
- Typography: modern sans serif such as Geist/Inter; use JetBrains Mono or equivalent for coordinates, UUIDs, UV values, animation times.
- Rounded panels, restrained shadows, no glossy gradients everywhere.
- Crisp 1px separators.
- Dense enough for a professional editor, but less intimidating than Blockbench.
- Icons should be simple line icons with Minecraft/voxel hints where useful.

Suggested tokens:

```css
--bg: #0B0F14;
--panel: #111820;
--surface: #171F29;
--surface-2: #1C2631;
--border: #293540;
--text: #F3F6F8;
--muted: #93A0AA;
--accent: #48E5A0;
--accent-hover: #65F2B5;
--danger: #F46B6B;
--warning: #F2C66D;
```

## 3. Global information architecture

Global sidebar items must remain simple:

- Nuevo proyecto
- Mis proyectos
- Recientes
- Configuración at the bottom

Do not add “Volver” as a sidebar item.

Core routes:

```text
/projects
/projects/new
/projects/:projectId
/projects/:projectId/mobs/new
/projects/:projectId/mobs/:mobId/model
/projects/:projectId/mobs/:mobId/texture
/projects/:projectId/mobs/:mobId/animation
/projects/:projectId/mobs/:mobId/export
```

Within a mob editor use the workspace tabs:

- Modelo
- Textura
- Animación
- Exportar

The AI assistant is contextual to the current workspace, not a disconnected chatbot page.

## 4. Project behavior

A project can contain many mobs.

Project cards:

- show project name
- show up to 3 mob previews
- if more exist, show `+N`
- no persistent full-width hero image requirement
- card/name opens project
- actions menu may include Rename, Duplicate, Export, Delete

Project detail view:

- project title + metadata
- `Agregar mob`
- project export action
- search mobs
- grid/list toggle optional
- mob cards with real 3D preview renders
- mob status such as Ready / In progress / Draft

## 5. New project vs. new AI mob

Keep project creation simple.

A project creation form should not turn into a giant technical wizard. It should primarily collect project name and initial mob choices.

The **AI generation wizard belongs to adding/creating a mob**, and uses the 4-step visual flow shown in mockups 02–04:

1. Reference
2. Configuration
3. Generation
4. Result

The wizard accepts a concept image, name, base type, and texture resolution. Base type may be AI-detected with manual override:

- Humanoid
- Arachnid
- Quadruped
- Flying
- Custom

## 6. AI generation progress view

Do not show only a spinner. Show visible stages:

- Analysing reference
- Detecting silhouette/anatomy
- Creating rig
- Generating cuboids
- Preparing UV
- Generating texture
- Optimising/validating model

Render the emerging model in the viewport as soon as enough geometry exists.

The result view should show:

- interactive 3D result
- cuboid count
- bone count
- texture resolution
- compatibility state
- Edit model
- Export now

## 7. Internal domain model — never use `.bbmodel` as the primary database model

Create an internal `MobProjectModel` representation.

At minimum:

```ts
interface MobProjectModel {
  id: string;
  projectId: string;
  name: string;
  baseType: 'humanoid' | 'arachnid' | 'quadruped' | 'flying' | 'custom';
  referenceImages: ReferenceImage[];
  bones: Bone[];
  cuboids: Cuboid[];
  texture: TextureDocument;
  uv: UvLayout;
  animations: AnimationSpec[];
  exportSettings: ExportSettings;
  revision: number;
}
```

The exporter converts this structure into `.bbmodel` only at the boundary.

## 8. Cuboid geometry rules

For MVP model generation:

- geometry = cuboids only
- hierarchy = bones/groups
- each cuboid has `from`, `to`, origin, optional rotation, per-face UV
- all UUIDs/IDs generated by application code, not by the AI model
- no negative dimensions
- validate parent/child references
- pivots must be useful for animation
- silhouette fidelity is more important than recreating every pixel

For humanoids, begin from Minecraft-like proportions but permit controlled changes.

Typical starting point:

```text
head: 8 x 8 x 8
body: 8 x 12 x 4
arms: 4 x 12 x 4
legs: 4 x 12 x 4
```

AI may resize/reposition and add cuboids for hands, jaw, torn clothing, shoulders, protrusions, armor, etc.

For `references/carcomido_reference.png`, the generated geometry should capture oversized hands, asymmetry, damaged clothing silhouette, body proportions, and an undead hunched identity while remaining neutral enough to animate.

Glowing purple cracks are primarily a texture/material feature unless they physically change the silhouette.

## 9. AI architecture

Split AI into structured stages.

### 9.1 Vision / ModelIntent

Input:

- reference image(s)
- base type
- Minecraft cuboid constraints

Output a strict JSON `ModelIntent`, for example:

```json
{
  "silhouette": "hunched humanoid",
  "proportions": {
    "headScale": 1.08,
    "armLength": 1.18,
    "handScale": 1.30,
    "shoulderWidth": 1.10
  },
  "asymmetry": 0.72,
  "features": [
    "oversized hands",
    "ragged shoulder cloth",
    "damaged lower garment"
  ],
  "materials": [
    "desaturated grey-green skin",
    "dark brown torn cloth",
    "violet emissive cracks"
  ]
}
```

### 9.2 Geometry planner

The AI does not return final `.bbmodel`. It returns operations from a whitelist:

```text
createBone
createCuboid
resizeCuboid
moveCuboid
rotateCuboid
setBonePivot
setBoneRotation
parentBone
removeCuboid
```

Each operation is schema-validated before execution.

Maintain undo/redo by storing operations or project snapshots.

### 9.3 AI edit flow

When user types:

> “Haz las manos más grandes y los hombros más irregulares”

AI should return a change plan such as:

```json
{
  "summary": "Increase both hands and add asymmetry to shoulders",
  "operations": [
    {"op": "resizeCuboid", "target": "hand_right", "scale": [1.2, 1.15, 1.2]},
    {"op": "resizeCuboid", "target": "hand_left", "scale": [1.15, 1.1, 1.15]},
    {"op": "moveCuboid", "target": "shoulder_right_detail", "delta": [-0.5, 0.25, 0]}
  ]
}
```

UI must show Before / After and list changed cuboids before applying.

## 10. Model editor workspace

Follow mockup 05.

Layout:

- left: hierarchy tree
- center: large Three.js viewport
- right: selected element properties
- bottom or floating: contextual AI prompt

Hierarchy should contain bones and child cuboids.

Manual tools:

- Select
- Move
- Scale
- Rotate
- Pivot
- Add cuboid
- Add bone
- Delete
- Duplicate
- Undo / Redo
- Grid/snap controls

Viewport:

- rotate, pan, zoom
- orthographic/perspective toggle optional
- grid floor
- selected cuboid outline
- bone/pivot gizmos
- reset camera
- reference-image overlay optional

## 11. Texture / UV editor

Follow mockup 07 plus useful concepts from `references/legacy_ui/texture_editor_legacy.png`.

Required:

- pixel editor for the current texture atlas
- UV region guides
- labelled body-part/face regions when possible
- selecting a cuboid face focuses/highlights its UV area
- selecting UV highlights that face in 3D
- 3D preview updates live
- rotate / zoom preview
- per-mob resolution support
- grid toggle
- color palette / picker
- brush
- eraser
- fill bucket
- eyedropper
- selection
- copy
- paste
- crop/fit pasted image to selected region
- undo/redo

Toolbar order should begin with color access, then drawing tools in a clear sequence. Preserve the previous product idea that color controls are quickly accessible and the editor can copy/paste a complete PNG or a selected part.

Do not implement a Photoshop-style layer stack in MVP.

## 12. AI texture generation

Do not ask an LLM to “paint the final PNG” as raw data.

Pipeline:

```text
Reference image
  -> material/palette analysis
  -> semantic face mapping
  -> deterministic UV layout
  -> image-generation/inpainting per semantic region or face
  -> atlas compositor
  -> pixel cleanup / palette adjustment
  -> preview / diff
```

Allow:

- faithful to reference
- Minecraft-like
- pixel-art
- detail level
- regenerate only selected face/body part

For Carcomido:

- grey-green skin
- dark ragged cloth
- bright violet eyes
- irregular violet emissive cracks
- high contrast but still readable in Minecraft

## 13. Animation system

Animation comes only after stable geometry, hierarchy, and pivots.

Maintain a tool-owned format called `AnimationSpec`; export it to `.bbmodel` later.

Support channels:

- rotation
- position
- scale (optional but schema-ready)

Animation properties:

- name
- duration
- loop
- tracks by bone
- keyframes
- interpolation
- semantic events

Use the sample in `samples/animation_spec_example.json`.

### Simple animation mode

For non-animators:

- animation type: Idle / Walk / Run / Attack / Hurt / Death / Special
- duration
- loop
- style sliders: speed, weight, aggression, asymmetry
- natural-language prompt
- Generate / Regenerate

### Advanced mode

Follow mockup 09:

- 3D viewport
- animation list left
- properties right
- timeline bottom
- bone rows
- keyframe diamonds
- play/pause
- loop
- playback speed
- scrubber
- add/delete keyframes

### AI animation generation

Do not generate movement from scratch when a reusable base exists.

Use template + AI modification:

```text
HumanoidWalkPreset
 + actual rig
 + reference-derived movement personality
 + user prompt
 -> AnimationSpec
```

Examples for Carcomido:

- heavy undead walk
- hunched body lean
- asymmetrical arm swing
- drag right leg
- unstable head motion

AI edits should be track-scoped when possible. “Move the left arm less” should not regenerate the entire animation.

### Animation library

Follow mockup 10.

Categories:

- Humanoid
- Hostile
- Neutral
- Special
- Custom

Cards preview the actual current mob using the preset.

## 14. Animation events

Internal `AnimationSpec` may carry semantic events even if not every export uses them immediately:

```json
{
  "time": 0.43,
  "type": "attack",
  "payload": {"hand": "right"}
}
```

Other future events:

- footstep
- sound
- particle
- custom server hook

Keep events separated from raw transform tracks.

## 15. FreeMinecraftModels / Blockbench export

Target current FMM behavior while isolating format knowledge in adapters.

Important baseline:

- FMM reads `.bbmodel` directly.
- FMM detects Blockbench major version from `meta.format_version`.
- Blockbench 5 separated `groups` from `outliner`.
- FMM currently handles v4/v5 hierarchy differences.
- FMM can use sibling `.png` and other companion files.
- special bones include `hitbox`, `tag_name`, and `mount_...`.

Implement:

```text
BBModelExporterV4
BBModelExporterV5
FmmCompatibilityValidator
```

Prefer modern v5 export, but keep a v4 compatibility adapter and golden fixtures.

Export validation must check:

- valid JSON
- UUID uniqueness
- valid groups/outliner references
- no negative/zero invalid cuboid dimensions
- all faces have valid UV coordinates
- texture indexes exist
- texture resolution matches UV coordinate space
- all animation bone references exist
- keyframe times are within duration
- animation duration > 0
- special FMM bone names are valid when used

Export options:

1. `mob.bbmodel`
2. optional `mob.png`
3. ZIP package containing model + texture(s)

The export screen should explicitly display FMM compatibility state and actionable errors.

## 16. FMM-specific authoring helpers

Add optional model authoring actions:

- Add hitbox anchor/bone
- Add nametag anchor/bone
- Add mount point

Map to conventions:

```text
hitbox
tag_name
mount_<name>
```

Hide these under an “FMM” or “Server” advanced section so normal creators are not overwhelmed.

## 17. Frontend stack

Use:

- Vue 3
- TypeScript
- Vite
- Pinia
- Vue Router
- Three.js directly or TresJS where it does not restrict editor-level control
- Canvas 2D / OffscreenCanvas for UV/pixel editing
- Web Workers for heavy pixel/UV operations
- IndexedDB for local autosave/cache
- Vitest
- Playwright for core flows

Avoid embedding the entire Blockbench application.

Build a purpose-specific editor.

## 18. Backend stack

Preferred implementation:

- Spring Boot 3.x
- Java 21
- PostgreSQL
- object storage (S3-compatible) for images/textures/export artifacts
- SSE or WebSocket for AI generation progress
- REST for project/editor state

Modules/services:

```text
project-service
asset-service
ai-orchestrator
model-validation-service
export-service
```

This can begin as one modular monolith; do not split into microservices prematurely.

## 19. Suggested API

```text
POST   /api/projects
GET    /api/projects
GET    /api/projects/{id}
PATCH  /api/projects/{id}
DELETE /api/projects/{id}

POST   /api/projects/{id}/mobs
GET    /api/mobs/{mobId}
PATCH  /api/mobs/{mobId}

POST   /api/mobs/{mobId}/references
POST   /api/mobs/{mobId}/ai/analyse
POST   /api/mobs/{mobId}/ai/generate-geometry
POST   /api/mobs/{mobId}/ai/edit-geometry
POST   /api/mobs/{mobId}/ai/generate-texture
POST   /api/mobs/{mobId}/ai/edit-texture
POST   /api/mobs/{mobId}/ai/generate-animation
POST   /api/mobs/{mobId}/ai/edit-animation

POST   /api/mobs/{mobId}/validate
POST   /api/mobs/{mobId}/export/bbmodel
GET    /api/jobs/{jobId}/events
```

AI endpoints return structured proposals/jobs. Never let provider-specific output leak directly into the editor state.

## 20. AI provider abstraction

Create interfaces such as:

```java
interface VisionModelProvider {}
interface StructuredReasoningProvider {}
interface ImageGenerationProvider {}
```

The app should not be tightly coupled to one AI vendor.

Store:

- provider
- model
- prompt version
- schema version
- reference IDs
- operation proposal

for reproducibility and debugging.

## 21. Revision / history system

Every accepted change should create a revision.

Support:

- undo
- redo
- named checkpoints
- AI proposal diff
- restore previous revision

Do not store giant duplicate PNGs for every brush stroke if avoidable; use efficient command history plus periodic snapshots.

## 22. 12 required screens

Implement these screens using the mockups as visual targets:

1. **Inicio / Mis proyectos** — dashboard, recent projects, main “Create with AI” entry.
2. **Nuevo proyecto / mob setup** — reference + model information.
3. **Generación IA** — staged progress + live wireframe/model preview.
4. **Resultado IA** — result metrics, 3D model, edit/export actions.
5. **Editor de modelo** — hierarchy, viewport, properties.
6. **Edición mediante IA** — AI assistant with Before/After + operation diff.
7. **Editor de textura** — UV canvas + 3D preview + pixel tools.
8. **Generador IA de textura** — reference/result, style and detail settings, part targeting.
9. **Animación** — viewport + animation list + timeline.
10. **Biblioteca de animaciones** — browsable presets with live preview.
11. **Exportación** — FMM/Blockbench target, validation, `.bbmodel` export.
12. **Detalle de proyecto** — mob grid, statuses, add mob.

Also preserve the useful **Agregar mob** modal/pattern from `references/legacy_ui/add_mob_modal_legacy.png` where appropriate.

## 23. Responsive strategy

Primary editing experience is desktop-first.

- dashboard/project management should work on tablet/mobile
- full model/texture/animation workspaces may show a “desktop recommended” layout on very narrow screens
- do not destroy editor density just to force every control into a phone view
- no horizontal page scroll in management screens

## 24. Accessibility

- full keyboard focus states
- tooltips do not replace visible labels for critical actions
- color is not the only indicator of selected/error state
- buttons at least ~40px hit targets
- timeline keyframes keyboard selectable
- 3D editor actions have menu/keyboard alternatives where feasible

## 25. MVP implementation order

### Phase 1 — deterministic editor core

- project/mob persistence
- 3D cuboid renderer/editor
- bones/pivots
- internal `MobProjectModel`
- undo/redo
- manual `.bbmodel` exporter
- validator

### Phase 2 — AI geometry

- reference upload
- Vision -> ModelIntent
- structured geometry operations
- AI diff / Apply / Reject
- generation progress screen

### Phase 3 — texture

- UV packer
- texture canvas
- live 3D texture preview
- AI texture pipeline

### Phase 4 — animation

- `AnimationSpec`
- timeline
- preset library
- AI animation generator/editor

### Phase 5 — FMM polish

- FMM-specific anchors
- compatibility report
- ZIP exports
- golden fixtures / integration test project

## 26. Acceptance criteria

A release candidate is acceptable when a user can:

1. Create project `Galgoth`.
2. Add a mob called `Carcomido` using `references/carcomido_reference.png`.
3. Generate an editable cuboid-only humanoid approximation.
4. Manually select and resize a hand.
5. Ask AI to make the other hand larger and review a diff before applying.
6. Generate/paint a texture and see it update in 3D.
7. Focus a body part in the UV editor.
8. Generate a heavy undead Walk animation.
9. Open advanced timeline and adjust one bone keyframe.
10. Validate the model.
11. Export a `.bbmodel` plus texture package.
12. Open the `.bbmodel` in Blockbench without repair dialogs.
13. Place the model/companions in FMM’s models folder and reload successfully in the target supported server setup.

## 27. Engineering constraints

- TypeScript strict mode.
- Backend validation for every write.
- JSON schema or equivalent validation for AI structured outputs.
- No raw model-provided UUIDs.
- No eval or generated executable code from AI responses.
- All AI requests cancellable where possible.
- Jobs survive temporary frontend disconnects.
- Autosave.
- Export is unit-tested against fixtures.
- AI failures must leave the current model unchanged.

## 28. Implementation style

Do not begin by creating placeholder pages for every feature.

Build a vertical slice first:

```text
Project -> Add Carcomido -> show cuboid model -> edit cuboid -> export bbmodel
```

Then add AI generation to that same flow.

For each feature:

1. define the domain contract
2. implement deterministic engine behavior
3. implement UI
4. add AI as a structured assistant
5. add tests

## 29. First development task

Start by producing:

1. repository/module structure
2. domain TypeScript types
3. backend DTOs/entities
4. route map
5. design tokens
6. reusable application shell matching mockups
7. Three.js cuboid/bone scene graph
8. load the included sample `samples/carcomido_minecraft_cuboids.bbmodel` as a temporary compatibility fixture
9. implement `MobProjectModel -> BBModelExporterV5` skeleton
10. create automated tests for UUID uniqueness and outliner/group integrity

After that, build the 12 screens incrementally around the real domain model rather than static mock data.

---

When there is ambiguity, prioritize in this order:

1. valid Minecraft/FMM-compatible output
2. non-destructive deterministic editing
3. fidelity to the 12-view mockup
4. AI convenience
5. extra features

Do not silently invent requirements that contradict the constraints above.
