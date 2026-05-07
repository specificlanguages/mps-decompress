# mops

`mops` is a small helper CLI for helping LLMs work with JetBrains MPS models.

## Usage

```sh
mops --help
mops --mps-home /path/to/mps model resave path/to/model
mops daemon status
mops daemon stop
```

This checkout is the Kotlin daemon prototype. The old Go/offline command surface and the previous Live IDE bridge
prototype were intentionally removed. `mops model resave` is the planned first daemon-backed operation; this slice only
contains the runnable Kotlin CLI and daemon skeletons.

## Commands

```sh
mops --mps-home <path> model resave <model-target>
```

Planned daemon-backed resave command. It will infer the MPS project, start or reuse a per-project daemon, and ask that
daemon to resave one model target through MPS APIs.

```sh
mops daemon status [--all]
mops daemon stop [--all]
```

Planned daemon lifecycle commands for inspecting and stopping per-project daemon processes.

## Build And Test

```sh
./gradlew check
./gradlew :cli:run --args=--help
./gradlew :daemon:run --args=--help
```

The repository is a Gradle-rooted Kotlin prototype with two application subprojects: `cli/` and `daemon/`.
