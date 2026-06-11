# LLM Design Decisions (2026-06-11)

## Purpose

Capture decisions for designing Seed4J CLI as a tool used by LLM agents as well as humans, so future CLI and automation work reduces interpretation variance instead of relying on each model to infer the same intent.

## Decision

Seed4J CLI must remain explicit, prescriptive, and machine-friendly. Clear CLI help is necessary, but not enough by itself for reliable LLM behavior across models.

Future MCP support may be useful as a structured intent layer over the CLI, but it should complement a strong CLI rather than replace it. Avoid designing an MCP server as a generic shell-command wrapper; expose intent-shaped tools that map to Seed4J capabilities.

## Rationale

LLMs can read the same CLI help and still apply different safety heuristics. For example, an agent may see a non-Git directory, decide that automatic commit is a risky side effect, and pass `--no-commit` even when the user asked for a normal project initialization.

Text help is advisory. MCP tool schemas can make intent harder to misread by using explicit fields, enums, defaults, validation, and structured errors. That said, if a model can still call the CLI directly, the CLI itself must continue to be safe and clear for machine callers.

## CLI and MCP Boundary

Seed4J CLI is the organic, zero-setup entry point for humans, scripts, CI, Docker, documentation examples, and LLMs that have terminal access. It should stay simple, local, scriptable, and useful without requiring an MCP-compatible host.

Seed4J MCP is the structured integration path for agents running in MCP-compatible clients. It is the better home for rich planning workflows, prompts, resources, schema-driven tool calls, previews, and guided validation.

Do not duplicate MCP workflows in the CLI just to make a textual version of the same agent experience. Add CLI features when they improve the terminal-native experience or provide clear zero-setup value. When both surfaces need the same concept or rule, keep the contract aligned and move shared behavior into a common core when practical.

## CLI Guidance

- Prefer commands and options that represent user intent rather than implementation details.
- Prefer explicit defaults in help text, examples, and errors.
- Prefer structured output modes such as JSON when a caller needs to inspect results programmatically.
- Prefer dry-run or plan modes for operations where agents should preview changes before mutating files.
- Avoid ambiguous negative options for important behavior. If a negative option exists, document when to use it and when not to use it.
- Validation errors should say what happened, why it is unsafe or invalid, and what command or option the caller should use next.

## Project Initialization and Git

The normal Seed4J project initialization behavior is to initialize Git if needed and create the Seed4J commit.

`--no-commit` means Seed4J must skip both Git initialization and commit creation. It is only appropriate when the caller explicitly does not want Seed4J to create a Git repository or commit.

For LLM-facing examples, prefer wording such as:

```text
For a normal new Git project, omit --no-commit.
Use --no-commit only when the user explicitly asks for no Git repository or no Seed4J commit.
```

## Future MCP Guidance

If an MCP server is added, expose intent-shaped tools such as:

- `create_project`
- `add_module`
- `list_modules`
- `inspect_project`
- `explain_module`
- `plan_project_stack`

Avoid a generic `run_seed4j_command(command: string)` tool as the primary API because it preserves the same ambiguity as shell command construction.

For project creation, prefer an explicit enum over a negated boolean, for example:

```json
{
  "gitBehavior": "init_and_commit"
}
```

The MCP server should reject contradictory or unsafe combinations, such as choosing `skip_git` when the user asked for a normal Git project initialization.
