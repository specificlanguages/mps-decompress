# mops

`mops` is a small helper CLI for helping LLMs work with JetBrains MPS models.

## Usage

```sh
mops expand < input.mps > output.mps
mops expand input.mps > output.mps
mops generate-ids model.mps 10
mops generate-ids --long model-folder 10
mops list-models
mops validate [--json] <model.mps|model-folder|root.mpsr> [other-targets...]
mops --version
```

Diagnostics are written to stderr. Malformed XML, unsupported persistence versions, invalid persistence containment shape, missing registries, and duplicate registry/import indices fail with a non-zero exit code.

## Commands

```sh
mops expand [input.mps]
```

Expands short indices in the MPS model XML to human-readable identifiers or long IDs, so the model is easier to inspect
with humans or LLMs.

It rewrites selected node-graph attributes using the model's own `<registry>` and `<imports>` sections:

- `node@concept` becomes the full concept name.
- `node@role`, `property@role`, and `ref@role` become unqualified role names.
- regular MPS node IDs in `node@id`, `ref@node`, and `ref@to` become signed decimal Java `long` values.
- import aliases in `ref@to` become the full imported model reference.

The `<registry>` subtree is left semantically unchanged.

Reads from stdin when `input.mps` is omitted. Writes transformed XML to stdout.

```sh
mops generate-ids [--long] <model.mps|model-folder> <count>
```

Generates regular node IDs that are not already used by `node@id` attributes in the model. For standalone persistence, pass a `.mps` file. For file-per-root persistence, pass the model folder; direct `*.mpsr` files in that folder are scanned.

IDs are printed one per line. By default they use MPS Java-friendly base64 format. With `--long`, they are printed as decimal `int64` values. Command flags must appear before positional arguments.

```sh
mops list-models [root]
```

Scans `root`, or the current directory when omitted, for standalone `.mps` model files and file-per-root `.model` metadata files. Prints a pretty JSON object mapping model IDs to absolute paths. When multiple locations have the same model ID, the value is an array of paths instead of a string.

File-per-root models are reported as the parent folder of the `.model` file with a trailing `/`. Paths are normalized to `/` separators.

When possible, `list-models` uses `git ls-files -co --exclude-standard` so Git-ignored files are skipped. If Git is unavailable or the root is not in a Git worktree, it falls back to a filesystem walk and only skips `.git`.

```sh
mops validate [--json] <model.mps|model-folder|root.mpsr> [other-targets...]
```

Validates explicit MPS model XML targets. Standalone `.mps` files and file-per-root model folders are complete validation targets. For file-per-root persistence, pass the model folder containing `.model`; direct `*.mpsr` files are checked as one model-wide target and nested `*.mpsr` files are ignored.

Passing a single `*.mpsr` runs incomplete validation, which reports a non-blocking informational finding and tells the caller to validate the file-per-root model folder containing `.model` for complete structural validation. Incomplete validation does not fail solely because sibling roots are unavailable. Well-formed persistence version `9` targets pass. Malformed XML and unsupported persistence versions are reported as validation findings, and every requested target is checked before the final exit status is decided.

Human output is terse. With `--json`, `validate` prints a stable validation report with status and findings for LLM consumption.

## Build And Test

```sh
./gradlew check

cd cli
go test ./...
go build ./cmd/mops
```

The repository is a Gradle-rooted monorepo. `cli/` contains the Go module named `mops`; direct Go workflows run from
that directory. `ide/` contains the MPS project home for the future Live IDE bridge artifact. Root Gradle tasks
orchestrate subproject checks without replacing Go dependency management or build semantics.

## Attribution

The regular node ID decoding follows the behavior of JetBrains MPS persistence v9 `IdEncoder` and `JavaFriendlyBase64`.

- https://github.com/JetBrains/MPS/blob/9b2eefc0208fecedb3e08c7d9f7f53aa22b5e72e/core/persistence/source/jetbrains/mps/smodel/persistence/def/v9/IdEncoder.java
- https://github.com/JetBrains/MPS/blob/9b2eefc0208fecedb3e08c7d9f7f53aa22b5e72e/core/smodel/source/jetbrains/mps/smodel/JavaFriendlyBase64.java
