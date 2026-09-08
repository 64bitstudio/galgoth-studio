# Galgoth Studio — Build Pack

This package turns the product discussion into a build-ready specification for an AI-assisted Minecraft Java Edition mob editor.

## Start here

1. Read `Galgoth_Studio_Product_AI_Technical_Spec.pdf` for the complete product/UX/technical document.
2. If you are using a coding agent, attach the whole folder and paste `MASTER_PROMPT.md`.
3. Use `mockups/00_all_views.png` as the primary visual reference.
4. Use `references/carcomido_reference.png` as the canonical image-to-model test case.
5. Use `samples/carcomido_minecraft_cuboids.bbmodel` as an early exporter/importer fixture, not as the final format architecture.
6. Read `TECHNICAL_REFERENCES.md` before implementing Blockbench/FMM export.

## Important product decisions

- Java Edition only for MVP.
- Projects contain multiple mobs.
- Model geometry is cuboid-based for the primary workflow.
- AI proposes structured operations; application code validates/applies them.
- Internal project state is NOT `.bbmodel`.
- Texture editor has no general layer system in the current product direction.
- Animation uses an internal `AnimationSpec`, then exports.
- Main UI accent is mint/emerald on graphite/black.

## Folder map

```text
mockups/       12 final UI mockups + master storyboard
references/    Carcomido reference + earlier UI explorations
diagrams/      Architecture/workflow diagrams
samples/       Example model and internal JSON specs
MASTER_PROMPT.md
TECHNICAL_REFERENCES.md
```
