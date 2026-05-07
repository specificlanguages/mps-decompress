# mops

`mops` is a helper CLI context for LLM-assisted work on JetBrains MPS models. It offers tools that make model files
easier to inspect and edit safely.

## Language

**MPS model**:

A container for a forest of **MPS nodes**. Top-level **MPS nodes** are called **Root nodes**. An **MPS model** may import
other **MPS models**.

**MPS node**:

An instance of an MPS concept inside an **MPS model**. It is the unit of model structure: it has an **MPS node ID**, a
concept, zero or more properties, zero or more **Node references**, and zero or more child **MPS nodes** in containment
roles. An **MPS node** is either a **Root node** or is contained by exactly one parent **MPS node**.

_Avoid_: XML node when referring to the model element.

**Root node**:

An **MPS node** directly contained by an **MPS model**, not by another **MPS node**. In file-per-root persistence, each
direct `*.mpsr` file persists one **Root node** and its descendants.

**Persisted model**:
The representation of a model on disk. May be a file (`*.mps` - default XML persistence, `*.mpb` - binary
persistence) or a folder containing a file named `.model` and zero or more `*.mpsr` files (file-per-root persistence).

_Avoid_: model file when the persistence format matters, model XML file (models may also be persisted in a binary format).

**MPS model XML**:
An **MPS model** persisted as XML. This may be a standalone `*.mps` file or file-per-root persistence split across
`.model` metadata and `*.mpsr` root files.

_Avoid_: MPS model when specifically referring to XML persistence, persisted model when binary persistence is not meant.

**MPS node ID**:
The identifier of an **MPS node** within an **MPS model**. IDs can be either regular or foreign. Each **MPS node** has
exactly one **MPS node ID**, and **MPS node IDs** must be unique within the model.
_Avoid_: node reference

**Regular node ID**:
An **MPS node ID** backed by a Java `long` value (signed 64-bit integer). In **MPS model XML**, it is written with
canonical MPS Java-friendly Base64 rules, disallowing leading zeroes but allowing a literal `0`.

**Foreign node ID**:
An opaque **MPS node ID** with the `~` prefix, including bare `~`.

**Node reference**:
A reference from one **MPS node** to another **MPS node**, in the same or in a different MPS model.

_Avoid_: node pointer, node ID.

**Local node reference**:
A reference from one **MPS node** to another **MPS node** in the same MPS model.

**External node reference**:
A reference from one **MPS node** to a target **MPS node** in an imported MPS model.

**Import index**:
The compact persistence alias used to identify an imported model in the XML persistence formats.
_Avoid_: model ID when referring to the alias itself

**Registry index**:
The compact persistence alias used by **MPS model XML** for concepts, child roles, property roles, and reference roles
declared in the model registry.
_Avoid_: language concept when referring only to the persistence alias

**Persistence grammar**:
The schema-like structural rules for **MPS model XML**: which XML elements may appear at each level, which attributes
are required for **Root nodes**, child **MPS nodes**, properties, and **Node references**, and where containment roles are
required. It does not check ID uniqueness, reference resolution, registry resolution, or language-level concept and child
cardinality rules.
_Avoid_: XML schema

**Model validation**:
Checking whether edited **MPS model XML** still represents a structurally and/or semantically usable **MPS model**.

**Validation run**:
A validation invocation over one or more **Validation targets**. The old `mops validate` command belonged to the removed
Go/offline prototype.

**Structural validation**:
Local checks over **MPS model XML** persistence structure, IDs, registries, imports, and file-internal consistency.

**Validation target**:
A selected **MPS model XML** file or file-per-root model folder that a validation run is asked to check.

**File-per-root model folder**:
An MPS model persistence folder containing `.model` metadata and direct `.mpsr` root files that together represent one
MPS model.

**Validation finding**:
A specific issue, warning, or informational result produced while checking a **Validation target**.
_Avoid_: error as a generic synonym

**Finding code**:
A stable machine-readable identifier for the kind of **Validation finding**.
_Avoid_: message text as an API

**Finding severity**:
The confidence impact of a **Validation finding**, distinguishing blocking errors from non-blocking warnings or
informational facts.

**Validation report**:
The collection of **Validation findings** and overall status produced by a model validation run.

**Incomplete validation**:
A validation run that checks only a standalone `.mpsr` root file or another target without enough context for model-wide
checks.
_Avoid_: failed validation when incompleteness is the only finding

**Semantic model checking**:
MPS-backed checking that loads an MPS project and its models and reports semantic issues such as broken references, scope
violations, and user-defined checker findings.
_Avoid_: structural validation, XML validation

**MPS daemon**:
A per-project Kotlin/JVM process started and reused by `mops` to run MPS-backed operations through MPS APIs.
_Avoid_: Live IDE bridge, visible IDE bridge

**Model resave**:
A daemon-backed operation that saves one loaded **MPS model** back to its **Persisted model** representation.
_Avoid_: format, force-save-all when only one model is targeted

**Model resave target**:
A filesystem path to a standalone `*.mps` file or **File-per-root model folder** selected for a **Model resave**.
_Avoid_: raw model reference as the primary user input

## Relationships

- **Model validation** may combine **Structural validation** and **Semantic model checking**. The old offline
  `mops validate` command is not part of the active daemon prototype.
- A **Validation run** checks all requested **Validation targets** and reports all findings before deciding the overall
  status.
- **MPS node IDs** must be unique across the entire MPS model, including all root files in file-per-root persistence.
- **Regular node IDs** must use canonical MPS Java-friendly base64 encoding; non-canonical regular-looking IDs are
  invalid.
- **Foreign node IDs** are valid opaque identifiers and participate in duplicate-ID and local-reference checks by exact
  string match.
- Custom or extended **MPS node ID** families beyond **Regular node IDs** and **Foreign node IDs** are outside the
  supported structural validation scope.
- **Local node references** in file-per-root persistence resolve against the whole model, not only the root file
  containing the reference.
- **External node references** must use an existing **Import index** and a syntactically valid target **MPS node ID**,
  but structural validation does not prove that the external target node exists.
- **Registry indices** used by node concepts and roles must resolve in the model's own registry, but structural
  validation does not prove language-level concept or role correctness.
- **Persistence grammar** checks schema-like XML structure and required attributes, not ID uniqueness, reference
  resolution, registry resolution, or concept-specific child cardinality.
- **Structural validation** is the always-available baseline for edited **MPS model XML**.
- **Structural validation** starts from explicit **Validation targets** rather than implicit whole-project or Git-change
  discovery.
- A **File-per-root model folder** is the complete target for model-wide structural checks; a standalone `.mpsr` root
  file can only support incomplete checks.
- **Incomplete validation** should still report useful local findings and should not fail solely because model-wide
  context is missing.
- A **Validation report** should be stable enough for LLM consumption, while human CLI text can stay terse.
- A **Validation finding** has stable fields for severity, code, message, target, file location when available,
  validation layer, and finding source.
- A **Finding code** is the stable API for agents; message text is for humans.
- **Finding severity** determines whether a **Validation finding** blocks confidence in the edited model.
- **Semantic model checking** is a later validation layer because it depends on MPS and project state.
- The **MPS daemon** replaces the old Live IDE bridge direction as the active MPS-backed prototype path.
- MPS-backed operations are grouped under domain commands such as `mops model resave`, not under an `ide` command group.
- The **MPS daemon** is a separate per-project JVM process, not code embedded in the CLI process.
- The CLI starts or reuses the **MPS daemon** for daemon-backed operations.
- The **MPS daemon** communicates through local socket IPC and serializes requests in the first prototype.
- A **Model resave** targets exactly one **MPS model**; broader module-wide or project-wide resaves are separate future
  operations.
- The first **Model resave** command accepts exactly one **Model resave target** per invocation.
- A **Model resave target** is a file path, not a raw MPS model reference string.
- A `.model` file or direct `*.mpsr` root file is not a valid **Model resave target** because neither represents a
  resavable model target by itself.
- A **Model resave target** is resolved to an absolute path before it is sent to the **MPS daemon**.
- The **MPS daemon** resolves the **Model resave target** against its loaded project and reports when no such model can
  be found or loaded.
- A **Model resave** may update resolve information or normalize persistence, but it is not a pure formatter.
- A successful **Model resave** means the **MPS daemon** completed the save operation, even when the persisted bytes did
  not change.
- A failed **Model resave** is reported by the **MPS daemon** with a stable machine-readable code and human message.
- A **Model resave** does not automatically run **Model validation**; callers compose the two operations explicitly when
  they need both.

## Example Dialogue

> **Dev:** "After an LLM edits **MPS model XML**, should `mops` validate it again in a future command?"
> **Domain expert:** "Yes, but say which layer: **Structural validation** can run locally, while **Semantic model
checking** needs MPS to load and check the model. The **Validation report** should be structured enough for an LLM to
> consume."

> **Dev:** "Should we call the first daemon-backed MPS operation a formatter?"
> **Domain expert:** "No. It is a **Model resave**: MPS saves one loaded **MPS model** back to its **Persisted model**,
> which can update resolve info and normalize persistence as a consequence."

> **Dev:** "Why is the command `mops model resave` instead of `mops ide resave-model`?"
> **Domain expert:** "The active prototype path is the **MPS daemon**, not a visible IDE bridge. `model resave` names the
> domain operation while the CLI handles daemon startup and reuse."

> **Dev:** "Do I pass the MPS model reference to `mops model resave`?"
> **Domain expert:** "No. The **Model resave target** is the path to the **Persisted model** that the agent edited."

## Flagged Ambiguities

- "validate" was used for both local XML/persistence checks and MPS-backed semantic checks; resolved as **Model
  validation** with two layers: **Structural validation** and **Semantic model checking**.
- "force save all" initially suggested a module-wide or project-wide action; resolved: the first operation is a
  **Model resave** for a single **MPS model**.
- "MPS-backed" can mean loading MPS in-process, talking to a visible IDE, or using a daemon; resolved: the active
  prototype path is the per-project **MPS daemon**.
- "Daemon protocol version" could be exhaustive or minimal in the first slice; resolved: v1 compatibility checking is
  limited to CLI/daemon protocol version compatibility.
- "Open project verification" could be a separate client concern or daemon state; resolved: the **MPS daemon** owns one
  loaded project and resolves operation targets against that project.
- "`<model>`" in `mops model resave <model>` could mean an MPS model reference or a path; resolved: it is a
  **Model resave target** path.
- "Resave-model inputs" could allow one or many model targets; resolved: the first command accepts exactly one
  **Model resave target**.
- "Socket IPC" could mean HTTP or a local socket protocol; resolved: the daemon prototype uses local socket IPC, with
  loopback TCP acceptable for v1.
- "Implementation planning" could preserve both Live IDE bridge and daemon paths; resolved: remove the old bridge path
  and make the daemon the only active prototype route.
- "Moving the Go CLI under `cli/`" could preserve the old Go command surface; resolved: replace it with a Kotlin CLI.
- "Post-resave validation" could be automatic or explicit; resolved: **Model resave** does not automatically run
  **Model validation**.
- "Path target" could mean the path as typed by the caller or a normalized path; resolved: `mops` sends an absolute
  **Model resave target** path to the **MPS daemon**.
- "Target validation" could mean local CLI preflight or daemon-side resolution; resolved: the **MPS daemon** is
  responsible for resolving the target to a loaded **MPS model** and reporting target failures.
- "`saved`" could mean either "the save operation completed" or "the file bytes changed"; resolved: it means the save
  operation completed.
- "Failed resave result" could be represented as `saved: false` or as an error response; resolved: failures use stable
  error codes.
- "Model resave route" could be owned by a daemon command group or a domain command group; resolved: use the domain
  command `mops model resave`.
- "Daemon endpoint security" could rely only on loopback or require a token; resolved: v1 uses loopback plus a
  per-daemon token.
