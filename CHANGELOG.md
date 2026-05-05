# Changelog

## 0.3.0 (Unreleased)

- Renamed `decompress` command to `expand`.

## 0.2.0 - 2026-04-29

- Added `mops generate-ids`, which generates unused regular node IDs for standalone `.mps` files or file-per-root model folders, defaulting to short Java-friendly base64 output with a `--long` decimal mode.
- Added `mops list-models`, which discovers `.mps` files and file-per-root `.model` metadata files and emits a model-ID-to-location JSON map.
- Renamed the CLI entry point from `mps-decompress` to `mops decompress`.
- Changed XML output to use `<empty />` syntax for empty elements to improve readability and reduce token usage.
- Deferred `mops help <command>` support as a useful future CLI usability improvement.
