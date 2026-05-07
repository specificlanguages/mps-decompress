# Issue tracker: Local Markdown

Issues and PRDs for this repo live as local Markdown files under `.scratch/`.

## Conventions

- **One feature per directory**: `.scratch/<feature-slug>/`
- **The PRD**: `.scratch/<feature-slug>/PRD.md`
- **Implementation issues**: `.scratch/<feature-slug>/issues/<NN>-<slug>.md`, numbered from `01`
- **Triage state**: record it as a `Status:` line near the top of each issue file; see `docs/agents/triage-labels.md` for the role strings
- **Comments and conversation history**: append to the bottom of the file under a `## Comments` heading

## When a skill says "publish to the issue tracker"

Create a new file under `.scratch/<feature-slug>/` and place the content in the appropriate PRD or issue file.

## When a skill says "fetch the relevant ticket"

Read the file at the referenced path. The user will normally pass the path or the issue number directly.
