---
name: seed4j-cli
description: Use Seed4J CLI to initialize or modify projects by discovering the active runtime and safely planning and applying modules. Use when Seed4J is the chosen project generator, including implementing a new-project specification, adding Seed4J capabilities, or working with the seed4j command.
---

# Seed4J CLI

Use the installed `seed4j` command as the authority for the active runtime, visible modules, parameters, and dependencies.
This skill governs the Seed4J portion of the task; after Seed4J finishes, verify the result and continue the surrounding
implementation normally.

## When to use this skill

Use this skill when Seed4J is the chosen project generator: implementing a new-project specification, discovering or
applying visible Seed4J modules, adding a Seed4J capability to a project, or working directly with the `seed4j` command.
Local project context that already establishes Seed4J is sufficient.

Do not use it for ordinary application bugs that do not involve Seed4J modules, authoring a new Seed4J module or runtime
extension, or work that explicitly chooses another project generator.

## Workflow

1. Decide whether the user requested inspection only or authorized project changes. Inspection, explanation, and planning
   do not authorize mutation. An implementation or change request does authorize execution after a valid plan.
2. Discover the active CLI and runtime with `seed4j --version`, `seed4j list`, and `seed4j --help`.
3. Infer candidate modules only from the user's requirements and the visible active catalog. Do not invent a static
   catalog or select a missing dependency or feature provider implicitly.
4. Before constructing an individual invocation, inspect `seed4j apply <module> --help`.
5. Ask the user only when a requirement, parameter, dependency, or provider choice remains materially ambiguous.
6. For one module, read [Applying an individual module](references/applying-modules.md). For a multi-module outcome, read
   [Planning and applying a module set](references/module-set-planning.md).
7. Plan before mutation. Evaluate the rendered dependency and parameter states, not only the plan's exit code. Execute
   only within the user's existing authorization and the host's effective permissions; a plan is not an authorization
   token or a reserved execution.
8. Verify generated files and relevant Seed4J and Git state, then return to the surrounding task.

## Mutation preflight

Before any mutating Seed4J command, establish that the invoked process can write the target project. Because commits are
enabled by default, also establish that it can write Git metadata. A successful read-only plan proves neither capability.

If Git-metadata access is absent or cannot be established, stop before execution, explain the missing host capability,
and request Full Access or an equivalent permission. Do not execute merely to observe the expected failure, reinterpret
existing changes as permission, add `--no-commit` as a workaround, or modify the agent host's configuration.

Use `--no-commit` only when the user explicitly requested that Seed4J neither initialize Git nor create Seed4J commits.
Project write access remains required, but Git-metadata write access does not.
