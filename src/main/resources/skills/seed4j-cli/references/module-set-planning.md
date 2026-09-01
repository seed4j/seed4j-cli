# Planning and applying a module set

Use the active catalog and request the complete desired outcome in one plan:

```text
seed4j apply-set <modules...> --plan
```

Requested order is preserved for reporting while the Seed4J landscape calculates execution order. Dependencies and
feature providers are never selected implicitly. Infer an explicit provider only from an unambiguous user requirement;
ask when visible candidates remain materially ambiguous.

## Interpret the plan

A plan is read-only. It neither authorizes, reserves, nor caches execution. Execution performs a fresh preflight and may
be invalidated by intervening changes. Evaluate dependency, provider, parameter, path, and Git states rather than only the
exit code.

Explicitly requested modules remain in execution order and are reapplied even when project history records them. Explicit
CLI parameters take precedence over compatible history. Metadata defaults shown by `apply-set --plan` are informational:
they are not executed or persisted as effective values unless supplied explicitly or obtained from compatible history.

| Exit code | Meaning                                                                                  |
| --------- | ---------------------------------------------------------------------------------------- |
| `0`       | The plan is valid, or every selected module succeeded.                                   |
| `2`       | Usage or predictable preflight validation failed before module or Git mutation.          |
| `1`       | An unexpected pre-execution failure occurred, or execution ended with a partial failure. |

Treat nonzero results as possible command contracts, not automatically as a broken tool.

## Permissions, commits, and dirty worktrees

Apply the project-write, Git-metadata, and default-commit preflight from
[Applying an individual module](applying-modules.md#authorization-and-execution), including its shared
[Codex permissions guidance](applying-modules.md#codex-permissions). Existing changes do not authorize `--no-commit`, do
not prove a module commit safe, and do not override the host-permission preflight.

## Sequential execution and partial failure

With commits enabled, each successful module creates one commit. Execution is sequential and non-atomic: successes before
the first failure are preserved, the failed module's effects are indeterminate, later modules are skipped, and no
automatic rollback occurs.

After partial failure, inspect the working tree and Seed4J project history. Inspect Git history too when commits were
enabled. For a user-requested `--no-commit` execution, do not give Git-specific recovery guidance.
