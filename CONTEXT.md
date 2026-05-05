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
An invocation of `mops validate` over one or more **Validation targets**.

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

## Relationships

- **Model validation** is exposed through `mops validate` and may combine **Structural validation** and **Semantic model
  checking**.
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

## Example Dialogue

> **Dev:** "After an LLM edits **MPS model XML**, should `mops` validate it?"
> **Domain expert:** "Yes, but say which layer: **Structural validation** can run locally, while **Semantic model
checking** needs MPS to load and check the model. The **Validation report** should be structured enough for an LLM to
> consume."

## Flagged Ambiguities

- "validate" was used for both local XML/persistence checks and MPS-backed semantic checks; resolved as **Model
  validation** with two layers: **Structural validation** and **Semantic model checking**.
