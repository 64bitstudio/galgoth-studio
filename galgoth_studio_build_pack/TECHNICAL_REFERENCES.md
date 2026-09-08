# Technical references (verified 2026-09-08)

- Blockbench `.bbmodel` format: https://blockbench.net/wiki/docs/bbmodel/
  - `.bbmodel` is JSON-based and primarily an internal Blockbench project format.
  - The format may introduce breaking changes. Blockbench 5.0 separated `groups` from `outliner` and changed animation keyframe direction behavior.
- Blockbench format capability matrix: https://blockbench.net/wiki/blockbench/formats/
- FreeMinecraftModels: https://github.com/MagmaGuy/FreeMinecraftModels
  - Imports `.bbmodel` and `.fmmodel`.
  - Current README targets Java 21 and Spigot/Paper 1.21.4+.
  - Models are placed under `plugins/FreeMinecraftModels/models/`, followed by `/fmm reload`.
  - Special conventions include `hitbox`, `tag_name`, and `mount_`-prefixed bones.
  - Sibling `.png`, `.yml`, `.json` files may be placed beside a model.
- FMM parser source: https://github.com/MagmaGuy/FreeMinecraftModels/blob/master/src/main/java/com/magmaguy/freeminecraftmodels/dataconverter/FileModelConverter.java
  - Detects Blockbench major version from `meta.format_version`.
  - Handles v4 versus v5 outliner/group differences.
  - Reads textures, cube elements, locator/null-object elements, and animations.

Treat these references as implementation baselines, not frozen contracts. Keep exporters versioned and covered by fixtures/tests.
