# Safe `apply-set` execution contract

Status: normative decision record for [seed4j-cli#297](https://github.com/seed4j/seed4j-cli/issues/297).

Implementation target: decision-complete input for
[seed4j-cli#298](https://github.com/seed4j/seed4j-cli/issues/298).

This specification defines the first mutating version of `seed4j apply-set`. The terms **MUST**, **MUST NOT**, **SHOULD**,
and **MAY** are normative.

## Scope and safety guarantee

`apply-set` applies an explicitly requested set of modules sequentially. The operation is **not atomic**, **not
transactional**, and has **no automatic rollback**. After execution starts, an error can leave partial effects.

The command MUST preserve every earlier successful module, stop at the first module whose individual application throws,
and MUST NOT infer or attempt a restoration of files, project history, Git state, or published events.

This issue creates only this specification. It does not change Java APIs, enable execution, or change the Seed4J core.

The future command interface is:

```text
seed4j apply-set <module-slug>... [module property options] [--project-path <path>] [--[no-]commit] [--plan]
```

- Omitting `--plan` requests execution after a successful preflight.
- `--commit` is enabled by default. `--no-commit` disables both Git initialization and commit creation.
- `--plan` remains read-only and reports the preflight snapshot without authorizing a later execution.
- Existing `seed4j apply <module>` behavior remains unchanged.

## Read-only preflight

The CLI MUST finish a read-only preflight before invoking the first module. A usage or predictable validation failure MUST
return exit code `2` and guarantee that the CLI invoked no module and made no project or Git mutation.

Preflight MUST retain all validations performed by the current read-only `apply-set` planner:

1. require at least one visible module slug;
2. reject unknown or hidden slugs and duplicate requested slugs;
3. ask the Seed4J landscape to calculate a deterministic order and reject modules it cannot order;
4. validate module dependencies, including dependencies satisfied by project history or by an explicitly requested module
   ordered before its dependent;
5. validate feature dependencies, without selecting a provider implicitly;
6. reconcile property definitions and reject incompatible definitions;
7. reject explicit property options unused by the requested set;
8. parse explicit values using their declared types;
9. read the latest applicable project-history values and reject relevant unsupported values or type mismatches;
10. reject every missing mandatory property; and
11. aggregate predictable dependency, property, and history problems in the preflight result.

Preflight MUST additionally validate the following execution invariants.

### Practical project-path validation

Path validation is practical rather than a promise that later writes will succeed:

- If the destination exists, it MUST be a directory and appear traversable and writable to the current process.
- If the destination does not exist, preflight MUST accept it when its nearest existing ancestor is a traversable, writable
  directory and no existing path component that must be a directory is a non-directory. Such a destination is
  _apparently creatable_.
- Preflight MUST NOT create the destination, create parent directories, initialize Git, acquire a mutating project lock,
  create a write probe, or otherwise test the path by mutation.
- A path accepted as apparently creatable can still fail during execution because permissions, concurrent changes,
  storage, or other external conditions can change. That failure follows the unexpected-failure contract.

Reading project history, catalog metadata, filesystem metadata, and Git status is allowed. Malformed or unreadable history
needed by the plan is a preflight failure when it is a predictable validation condition; an unexpected read failure returns
exit code `1`, still before any mutation.

### Exact requested-set invariant

The calculated execution order MUST contain every requested module exactly once and no module that was not explicitly
requested. Concretely, after duplicate rejection, its length and set of slugs MUST equal those of the requested module
list. Any mismatch invalidates preflight.

Dependencies can be satisfied from history or from an earlier requested module, but the planner MUST NOT add a missing
module or feature provider implicitly. It also MUST NOT omit an explicitly requested module because history already lists
it.

### Dirty Git warning

When commits are enabled and the destination belongs to an existing dirty Git worktree, preflight MUST emit a `WARNING`
on `stderr` and continue. Dirty means that Git reports tracked, staged, or untracked changes relevant to that worktree.

The warning MUST explain that per-module commits can include or be affected by pre-existing changes and recommend cleaning
or stashing the worktree, or explicitly choosing `--no-commit`. It is informational: it MUST NOT invalidate the plan,
prevent execution, or change an otherwise successful exit code `0`.

The dirty-worktree check is not required when `--no-commit` is selected because this command will perform no Git
initialization or commit in that mode.

## Parameter and history semantics

Parameter resolution keeps this precedence:

1. explicit CLI input;
2. the latest compatible project-history value;
3. a module metadata default, for display only.

Defaults remain informational. The execution adapter MUST send to the Seed4J core only applicable values whose source in
the approved plan is explicit input or project history. It MUST NOT materialize a displayed metadata default as core
input. A default for a mandatory property does not make the preflight valid.

An explicitly requested module already present in the preflight history snapshot MUST remain in the execution order and
MUST be invoked again. Planning, progress, and the final summary MUST mark that module as `reapplied`. `reapplied` is an
annotation on the module, not a fourth execution status. The module still ends as `SUCCEEDED`, `FAILED`, or `SKIPPED`.

The preflight history snapshot determines the `reapplied` annotation and parameter sources for the whole invocation.
History written by an earlier module in the same execution MUST NOT cause the CLI to re-plan or skip a later requested
module.

## Plan snapshot consistency

A `--plan` invocation and a later execution invocation are separate snapshots. A successful `--plan` does not reserve the
project, lock dependencies, authorize mutation, or provide a plan token. Execution MUST recalculate the complete read-only
preflight so that catalog, history, parameters, path state, and warnings reflect the new invocation.

Within one execution invocation, the approved `ModuleSetPlan` is the single execution snapshot. The application flow MUST
reuse the same immutable plan instance that passed preflight. It MUST NOT resolve a second order, property set, dependency
result, or history interpretation between approval and execution. Each individual core request is a projection of that
same plan instance.

This prevents internal planning drift; it cannot eliminate external time-of-check/time-of-use changes. An external change
after preflight can still make an individual application fail.

## Sequential execution algorithm

After a valid execution preflight, the CLI MUST:

1. create a result slot for every slug in the plan's execution order;
2. visit that order once, sequentially, without parallel work;
3. derive the current module's applicable explicit and historical parameters from the approved plan;
4. call the existing **individual-module** Seed4J application API for that module, passing the selected commit mode;
5. mark the module `SUCCEEDED` only if the individual call returns normally;
6. on the first thrown exception, mark that module `FAILED`, mark every uninvoked later module `SKIPPED`, and stop; and
7. render a final summary containing every requested module.

The CLI MUST NOT use the Seed4J multi-module application API. That API performs its own multi-module transformation and
does not preserve the CLI's approved plan as the sole ordering and reporting boundary. The CLI MUST NOT reproduce module
application behavior; the individual API remains responsible for applying each module.

No change to the `seed4j/seed4j` repository is required for this first execution contract.

## Module result semantics

The three module statuses have these exact meanings:

| Status      | Meaning                                                                                                                                                                   |
| ----------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `SUCCEEDED` | The individual call returned normally. File application, project-history update, an enabled per-module commit, and publication of that call's events therefore completed. |
| `FAILED`    | The individual call threw. Its effects are indeterminate and can include files, project history, a commit, or published events.                                           |
| `SKIPPED`   | The module was not invoked because an earlier module was `FAILED`. It has no effects from this invocation.                                                                |

`FAILED` does not mean “no changes.” Even if the exception appears to originate late in the call, the CLI MUST NOT infer
which effects occurred from exception type, current files, history, Git, or observed events. The failure report MUST call
the effects indeterminate.

Every `SUCCEEDED` module before a failure remains successful. Its files, history, commit when enabled, and published
events remain in place. The CLI MUST NOT reset, revert, amend, delete, compensate, or publish compensating events.

If the first individual call throws, there are no earlier `SUCCEEDED` modules, but the overall execution result is still
`PARTIAL_FAILURE` because the failed call itself can have partial effects.

## Commits, project history, and events

With the default `--commit` mode:

- Git is initialized when needed by the individual Seed4J application flow.
- Every `SUCCEEDED` module has exactly one completed module commit.
- Commits are created per module, in execution order; the CLI MUST NOT squash them into one set commit.
- Commits belonging to earlier successful modules are preserved after a later failure.
- A `FAILED` module's Git initialization or commit state is indeterminate.

With `--no-commit`:

- this invocation MUST NOT initialize Git;
- this invocation MUST NOT create, amend, or otherwise manage a commit; and
- files, project history, and events still follow the normal per-module application flow.

For project history and events in either commit mode:

- `SUCCEEDED` guarantees that the module's history update and event publication completed;
- `FAILED` provides no guarantee about whether its history update or events occurred;
- `SKIPPED` guarantees that no history update or event came from that module in this invocation; and
- earlier successful history and events are never rolled back.

The Seed4J individual application API, not the CLI, owns file generation, history persistence, Git initialization and
commit creation, and event publication. The CLI owns orchestration and reporting of the observable call boundary.

## Output and exit codes

Human-readable text is the only format in this contract.

| Stream   | Content                                                                                                                                                        |
| -------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `stdout` | A valid plan; execution progress; the per-module final summary; overall `SUCCEEDED` or `PARTIAL_FAILURE`; and `No changes were applied.` when preflight fails. |
| `stderr` | Warnings; validation diagnostics; a concise unexpected-failure cause; and a safe next action. No stack trace is printed by default.                            |

The progress line and final summary MUST use the literal module statuses `SUCCEEDED`, `FAILED`, and `SKIPPED`. A failed
execution MUST use the literal overall status `PARTIAL_FAILURE`. Warnings and `reapplied` annotations do not replace a
module status.

The exit-code contract is:

| Exit code | Meaning                                                                                                                                                                 |
| --------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `0`       | `--plan` produced a valid plan, or execution completed with every module `SUCCEEDED`. Warnings do not change this code.                                                 |
| `2`       | Command usage or preflight is invalid. No individual module was invoked, and no project or Git mutation was made.                                                       |
| `1`       | An unexpected failure occurred. If execution started, there can be partial progress and indeterminate effects from the `FAILED` module; otherwise no mutation occurred. |

On exit code `1` after execution starts, `stderr` MUST tell the caller to inspect the working tree, project history, Git
log when applicable, and relevant external event effects before deciding whether to retry. It MUST NOT recommend an
automatic blind retry or claim rollback.

## Complete output examples

The exact renderer may add stable explanatory fields, but it MUST preserve the streams, statuses, safety statements, and
exit semantics shown here.

### Full success

```bash
seed4j apply-set init maven-java prettier \
  --project-path /work/sample \
  --project-name "Sample application" \
  --base-name sampleApplication \
  --package-name com.acme.sample \
  --node-package-manager npm
```

`stdout`:

```text
Preflight: VALID
Commit mode: one commit per succeeded module

Applying module set:
[1/3] init
      Status: SUCCEEDED
      History: updated
      Events: published
      Commit: created
[2/3] maven-java
      Status: SUCCEEDED
      History: updated
      Events: published
      Commit: created
[3/3] prettier
      Status: SUCCEEDED
      History: updated
      Events: published
      Commit: created

Summary:
  init        SUCCEEDED
  maven-java  SUCCEEDED
  prettier    SUCCEEDED
Module set status: SUCCEEDED
```

`stderr`: empty

Exit code: `0`.

### Invalid preflight

```bash
seed4j apply-set maven-java --project-path /work/sample
```

`stdout`:

```text
Preflight: INVALID
No changes were applied.
```

`stderr`:

```text
ERROR: module:init is missing; required by: maven-java.
Next action: add init explicitly before maven-java, or choose a set whose dependencies are satisfied.
```

Exit code: `2`. No individual module, Git initialization, file write, history update, commit, or event publication was
attempted.

### Unexpected failure after partial progress

```bash
seed4j apply-set init maven-java prettier --project-path /work/sample
```

`stdout`:

```text
Preflight: VALID
Commit mode: one commit per succeeded module

Applying module set:
[1/3] init
      Status: SUCCEEDED
      History: updated
      Events: published
      Commit: created
[2/3] maven-java
      Status: FAILED
      Effects: indeterminate
[3/3] prettier
      Status: SKIPPED
      Reason: not invoked after the first failure

Summary:
  init        SUCCEEDED
  maven-java  FAILED
  prettier    SKIPPED
Module set status: PARTIAL_FAILURE
```

`stderr`:

```text
ERROR: maven-java failed: unable to complete module application.
The failed module may have changed files, history, Git, or published events. Earlier successes were preserved.
Next action: inspect the working tree, project history, Git log, and relevant event effects before deciding whether to retry.
```

Exit code: `1`. The `init` commit and its other completed effects remain. No restoration is attempted.

### Dirty Git worktree continues with a warning

```bash
seed4j apply-set prettier --project-path /work/sample
```

`stdout`:

```text
Preflight: VALID
Commit mode: one commit per succeeded module

Applying module set:
[1/1] prettier
      Status: SUCCEEDED
      History: updated
      Events: published
      Commit: created

Summary:
  prettier  SUCCEEDED
Module set status: SUCCEEDED
```

`stderr`:

```text
WARNING: Git worktree /work/sample is dirty; module commits can include or be affected by pre-existing changes.
Next action: clean or stash those changes before execution, or use --no-commit. Continuing because this warning is non-blocking.
```

Exit code: `0`. The warning does not block execution.

### Reapply a module already in project history

Given that project history already contains `prettier`:

```bash
seed4j apply-set prettier --project-path /work/sample
```

`stdout`:

```text
Preflight: VALID
Execution order:
  1. prettier (reapplied)
Commit mode: one commit per succeeded module

Applying module set:
[1/1] prettier (reapplied)
      Status: SUCCEEDED
      History: updated
      Events: published
      Commit: created

Summary:
  prettier  SUCCEEDED  reapplied
Module set status: SUCCEEDED
```

`stderr`: empty

Exit code: `0`. History satisfied planning facts but did not erase the explicit request.

### Execution with `--no-commit`

```bash
seed4j apply-set init maven-java \
  --project-path /work/sample \
  --project-name "Sample application" \
  --base-name sampleApplication \
  --package-name com.acme.sample \
  --no-commit
```

`stdout`:

```text
Preflight: VALID
Commit mode: disabled; Git will not be initialized and no commits will be created

Applying module set:
[1/2] init
      Status: SUCCEEDED
      History: updated
      Events: published
      Commit: disabled
[2/2] maven-java
      Status: SUCCEEDED
      History: updated
      Events: published
      Commit: disabled

Summary:
  init        SUCCEEDED
  maven-java  SUCCEEDED
Module set status: SUCCEEDED
```

`stderr`: empty

Exit code: `0`. Files, history, and events are applied normally; this invocation neither initializes Git nor creates
commits.

## Rejected alternatives

### Real or Git-based rollback

Rejected for the first implementation. Files, persisted history, commits, and already observed events do not share one
transaction boundary. Git cannot retract event delivery and is not a safe restoration mechanism for an initially dirty
or non-Git project. Advertising rollback without a capability spanning all effects would create a false guarantee.

### One commit for the complete set

Rejected. Delaying a single commit would not make history or events transactional, and a later failure would leave prior
successful file changes without the per-module audit boundary supplied by the individual API. One completed commit per
successful module accurately records the sequential operation.

### Blocking execution on a dirty Git worktree

Rejected. Dirtiness does not make the module plan invalid and users may intentionally retain local changes. An actionable,
non-blocking warning communicates the commit risk while leaving the choice with the caller.

### Skipping modules already in history

Rejected. An explicit slug is execution intent, and a module may be deliberately reapplied to reconcile or regenerate its
output. History can satisfy dependency and parameter planning but MUST NOT silently remove an explicit request.

### Seed4J core multi-module API

Rejected for this flow. The CLI needs the approved `ModuleSetPlan` to remain the sole exact order, parameter, progress, and
failure boundary. Invoking the core multi-module API would delegate a second multi-module transformation and obscure which
individual call returned or threw. The individual API provides the required observable boundary without a core change.

### Materializing metadata defaults

Rejected. Defaults are catalog information used to explain possibilities, not explicit user intent or persisted project
state. Sending them would change current parameter semantics and could make a mandatory value appear supplied. Only
explicit and compatible historical values cross into the core call.

## Responsibility boundary

| Responsibility                                                                                                         | Owner                                      |
| ---------------------------------------------------------------------------------------------------------------------- | ------------------------------------------ |
| CLI syntax, `--[no-]commit`, `--plan`, usage validation, and exit codes                                                | `seed4j-cli` primary adapter               |
| Read-only preflight orchestration, immutable plan, exact-set invariant, parameter-source selection, and reapply marker | `seed4j-cli` application/domain            |
| Catalog, landscape ordering, history reads, path checks, Git-status reads, and individual-core integration             | `seed4j-cli` secondary adapters            |
| Sequential control, first-failure stop, result classification, progress, summary, warnings, and next action            | `seed4j-cli`                               |
| Module business behavior, file changes, history persistence, Git initialization/commit, and event publication          | existing individual API in `seed4j/seed4j` |

The CLI MUST not duplicate Seed4J module application logic. The core MUST not be changed merely to add this CLI
orchestration contract.

## Acceptance audit for #297

| #   | Acceptance criterion                                    | Normative coverage                                                                                                      |
| --- | ------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| 1   | Atomicity guarantees are explicit                       | The operation is sequential, non-atomic, non-transactional, and has no automatic rollback.                              |
| 2   | Preflight boundaries are defined                        | Read-only validations, practical path checks, exact requested-set confirmation, and dirty-Git warning are specified.    |
| 3   | Commit granularity is defined                           | Default mode creates exactly one commit per `SUCCEEDED` module; `--no-commit` creates none and does not initialize Git. |
| 4   | Partial-failure behavior is defined                     | Earlier successes remain, the first thrown call is `FAILED`, later calls are `SKIPPED`, and execution stops.            |
| 5   | Rollback support or its absence is explicit             | No file, history, Git, or event restoration or compensation is attempted.                                               |
| 6   | Already-applied behavior is defined                     | Explicitly requested historical modules are invoked and annotated `reapplied`.                                          |
| 7   | Project-history behavior is defined                     | A returned call guarantees its update; a thrown call is indeterminate; skipped calls add nothing; successes remain.     |
| 8   | Event-dispatch behavior is defined                      | A returned call guarantees publication completed; a thrown call is indeterminate; published events are not retracted.   |
| 9   | Exit codes are defined                                  | `0` is valid/full success, `2` is non-mutating usage/preflight invalidity, and `1` is unexpected failure.               |
| 10  | User-visible progress and failure reporting are defined | Streams, literal statuses, `PARTIAL_FAILURE`, concise cause, next action, and six complete examples are specified.      |
| 11  | CLI and Seed4J core responsibilities are identified     | The responsibility table assigns planning/orchestration to the CLI and individual application effects to the core.      |
| 12  | The execution issue has no unresolved product decision  | Interface, snapshots, order, values, reapplication, commits, effects, reporting, and failure handling are fixed here.   |

The three examples required by #297 are **Full success**, **Invalid preflight**, and **Unexpected failure after partial
progress**. The additional examples fix the required dirty-Git, reapplication, and `--no-commit` behavior for #298.

## Explicit limits and documentation lifecycle

The following remain out of scope: implementation in #297, presets or named sets, parallel execution, JSON output, any
rollback mechanism, Seed4J core changes, automatic dependency/provider selection, and a general specification system.

No capability beyond this specification may enter #298 without an explicit review and revision of this contract. In
particular, convenience behavior MUST NOT weaken preflight, add implicit modules or defaults, change commit granularity,
or claim stronger failure recovery.

After #298, implementation and tests become the executable proof of behavior, `documentation/Commands.md` documents the
public command, and this specification remains the canonical record of decisions, guarantees, rejected alternatives, and
architectural limits.
