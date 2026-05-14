# Changelog

## 0.3.0 (Unreleased)

- Pivoted the prototype to Kotlin application subprojects for `cli` and `daemon`.
- Added a persistent per-project daemon lifecycle behind `mops --mps-home <path> daemon ping`.
- Added `mops daemon status` and `mops daemon stop` for inspecting and stopping known project daemons.
- Made daemon startup prepare isolated MPS/IDEA runtime directories and report environment readiness plus daemon log path.
- Removed the old Go/offline command surface.
- Removed the old Live IDE bridge subproject and decision records.

## 0.2.0 - 2026-04-29

- Added `mops generate-ids`, which generates unused regular node IDs for standalone `.mps` files or file-per-root model folders, defaulting to short Java-friendly base64 output with a `--long` decimal mode.
- Added `mops list-models`, which discovers `.mps` files and file-per-root `.model` metadata files and emits a model-ID-to-location JSON map.
- Renamed the CLI entry point from `mps-decompress` to `mops decompress`.
- Changed XML output to use `<empty />` syntax for empty elements to improve readability and reduce token usage.
- Deferred `mops help <command>` support as a useful future CLI usability improvement.
