# Gradle-rooted cli/ide monorepo

`mops` is a Gradle-rooted monorepo with sibling `cli/` and `ide/` subprojects. The Go command-line tool lives under `cli/`, where `cli/build.gradle.kts` delegates build and test work to Go, while IDE-facing MPS code lives under `ide/`. This keeps the live-IDE integration visible as a peer artifact and gives the repository one top-level Gradle entry point for cross-language builds without replacing Go's own module and toolchain semantics.
