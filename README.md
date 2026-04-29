# mps-decompress

`mps-decompress` expands compressed JetBrains MPS persistence v9 model XML so the model is easier to inspect with humans or LLMs.

It rewrites selected node-graph attributes using the model's own `<registry>` and `<imports>` sections:

- `node@concept` becomes the full concept name.
- `node@role`, `property@role`, and `ref@role` become unqualified role names.
- regular MPS node IDs in `node@id`, `ref@node`, and `ref@to` become signed decimal Java `long` values.
- import aliases in `ref@to` become the full imported model reference.

The `<registry>` subtree is left semantically unchanged.

## Non-goals

The output is for inspection only. It is well-formed XML, but it is not intended to be loaded back into MPS or round-tripped as normal MPS persistence.

There is no in-place mode. Output always goes to stdout.

## Usage

```sh
mps-decompress < input.mps > output.mps
mps-decompress input.mps > output.mps
mps-decompress --version
```

Diagnostics are written to stderr. Unsupported persistence versions, missing registries, malformed XML, and duplicate registry/import indices fail with a non-zero exit code.

## Build And Test

```sh
go test ./...
go build ./cmd/mps-decompress
```

## Attribution

The regular node ID decoding follows the behavior of JetBrains MPS persistence v9 `IdEncoder` and `JavaFriendlyBase64`.

- https://github.com/JetBrains/MPS/blob/9b2eefc0208fecedb3e08c7d9f7f53aa22b5e72e/core/persistence/source/jetbrains/mps/smodel/persistence/def/v9/IdEncoder.java
- https://github.com/JetBrains/MPS/blob/9b2eefc0208fecedb3e08c7d9f7f53aa22b5e72e/core/smodel/source/jetbrains/mps/smodel/JavaFriendlyBase64.java
