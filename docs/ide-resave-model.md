# IDE resave-model route contract

The IDE plugin adds a route to MPS's existing localhost HTTP server, which normally listens on port `63320`. The route resaves one loaded MPS model in the currently open project.

## HTTP endpoint

```http
POST /mops/resave-model
Content-Type: application/json
```

Request:

```json
{
  "modelPath": "/absolute/path/to/model.mps"
}
```

Success response:

```http
200 OK
Content-Type: application/json
```

```json
{
  "modelPath": "/absolute/path/to/model.mps",
  "saved": true
}
```

`saved: true` means the IDE completed the save operation. It does not mean the persisted bytes changed.

Failure responses are non-2xx HTTP responses with a stable error code and a human-readable message:

```json
{
  "code": "MODEL_NOT_FOUND",
  "message": "No loaded MPS model matches the target path"
}
```

Recommended error codes:

- `INVALID_REQUEST`: the JSON request is malformed or `modelPath` is missing, empty, or not absolute.
- `INVALID_MODEL_TARGET`: `modelPath` points at a target shape that cannot represent a whole model, such as `.model` or `*.mpsr`.
- `MODEL_NOT_FOUND`: no loaded model in the currently open project matches `modelPath`.
- `MODEL_COULD_NOT_BE_LOADED`: the IDE can identify the intended model target but cannot load or access it as an MPS model.
- `SAVE_FAILED`: MPS attempted to save the model and the save operation failed.
- `INTERNAL_ERROR`: an unexpected route implementation failure.

## IDE behavior

- Resolve `modelPath` against the currently open project state.
- Match standalone models by their `*.mps` persistence file.
- Match file-per-root models by the containing folder, not by the `.model` metadata file or individual `*.mpsr` root files.
- Resave exactly the matched model, not the containing module or whole project.
- Do not create, import, or reload a model solely because a path was requested.
- Let MPS perform its normal save behavior, including persistence normalization and resolve-info updates.
- Report failures through the structured error response rather than a success response with `saved: false`.
- Do not provide dry-run behavior for this route. Any future non-mutating probe or status check should be a separate endpoint.

## Test plan

- Valid standalone model: with a project open and a loaded standalone `*.mps` model, `POST /mops/resave-model` returns `200` with the same absolute `modelPath` and `saved: true`.
- Valid file-per-root folder: with a loaded file-per-root model, posting the model folder path returns `200` and resaves that model.
- No byte-change case: posting an already-normal model still returns `saved: true`.
- Direct `.model` target: posting the metadata file returns a non-2xx response with `INVALID_MODEL_TARGET`.
- Direct `*.mpsr` target: posting a root file returns a non-2xx response with `INVALID_MODEL_TARGET`.
- Unknown path: posting an absolute path that is not a loaded model returns `MODEL_NOT_FOUND`.
- Missing language or inaccessible model: if MPS can identify the target but cannot access/load it as a model, return `MODEL_COULD_NOT_BE_LOADED`.
- Save exception: if the MPS save operation throws or reports failure, return `SAVE_FAILED`.
- Bad request body: malformed JSON, missing `modelPath`, empty `modelPath`, or relative `modelPath` returns `INVALID_REQUEST`.
- Non-canonical path: an absolute path containing aliases, symlinks, or redundant path segments still resolves to the intended loaded model when it points at the same persisted model location.
- Scope guard: a resave request for one model must not save every model in the module or project.
- No dry-run: the route always performs the resave operation when the request is valid and the model is found.
