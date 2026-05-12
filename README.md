# mops

`mops` is a small helper CLI for helping LLMs work with JetBrains MPS models.
This checkout is a Gradle-rooted Kotlin prototype with two application subprojects: `cli/` and `daemon/`.

## Usage

```sh
mops --help
mops --mps-home /path/to/mps daemon ping
mops --mps-home /path/to/mps model resave path/to/model
mops daemon status
mops daemon stop
```

This checkout is the Kotlin daemon prototype. The old Go/offline command surface and the previous Live IDE bridge
prototype were intentionally removed. MPS-backed work now goes through a per-project daemon process that the CLI starts
or reuses for daemon-backed commands. `mops model resave` is the planned first real model operation and is still a
skeleton command in this stage.

## Commands

```sh
mops --mps-home <path> daemon ping
```

Starts or reuses the persistent daemon for the current MPS project, exchanges one ping request over a loopback socket,
and prints the structured response. `MOPS_MPS_HOME` can be used instead of `--mps-home`.
The command walks upward from the current directory until it finds a `.mps` directory.

```sh
mops --mps-home <path> model resave <model-target>
```

Planned daemon-backed resave command. It will infer the MPS project, start or reuse a per-project daemon, and ask that
daemon to resave one model target through MPS APIs.

```sh
mops daemon status [--all]
mops daemon stop [--all]
```

Inspect or stop known per-project daemon processes. Without `--all`, the command infers the current project from the
working directory. With `--all`, it reads every known daemon record.

## Daemon State

Daemon records, logs, working files, and isolated IDEA config and system directories live outside the MPS project. By
default the CLI stores them under `~/.mops/daemon`; set `MOPS_DAEMON_HOME` to use another directory. Each project gets a
stable hashed subdirectory under `projects/`, including:

- `daemon.json` - atomic daemon record with port, token, PID, protocol version, daemon version, project path, MPS home,
  log path, and startup time
- `logs/daemon-ping.log` - daemon startup and runtime log for the current prototype
- `daemon/idea-config` and `daemon/idea-system` - isolated IDEA directories passed to the daemon JVM

Daemon commands use loopback socket IPC with a per-daemon token. Requests are serialized by the daemon. Stale daemon
records are removed when the recorded process or socket is no longer reachable.

## Build And Test

```sh
./gradlew check
./gradlew :cli:run --args="--mps-home /path/to/mps daemon ping"
./gradlew :cli:run --args=--help
./gradlew :daemon:run --args=--help
```

The repository is a Gradle-rooted Kotlin prototype with two application subprojects: `cli/` and `daemon/`.
