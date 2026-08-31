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
and MUST NOT infer or attempt a restoration of files, project history, Git state, dispatched events, or downstream event
effects.

This issue creates only this specification. It does not change Java APIs, enable execution, or change the Seed4J core.

The future command interface is:

```text
seed4j apply-set <module-slug>... [module property options] [--project-path <path>] [--[no-]commit] [--plan]
```

- Omitting `--plan` requests execution after a successful preflight.
- `--commit` is enabled by default. `--no-commit` disables both Git initialization and commit creation.
- `--plan` remains read-only and reports the preflight snapshot without authorizing a later execution.
- Existing `seed4j apply <module>` behavior remains unchanged.

Preflight is a validated plan, not a dry run. It MUST NOT simulate individual module application, module hooks, Git
operations, event dispatch or listeners, or file diffs. Consequently, `--plan` reports validated inputs and execution
order, not a preview of the effects or diff that execution would produce.

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
9. read the latest applicable project-history values and, when the history API returns normally, reject relevant
   unsupported values or values incompatible with the catalog type;
10. reject every missing mandatory property; and
11. aggregate predictable dependency, property, and normally returned history-value problems in the preflight result.

Preflight MUST additionally validate the following execution invariants.

`ModuleSetPlan` MUST distinguish detailed planning that was `EVALUATED` from detailed planning that was
`NOT_EVALUATED`. A path problem, duplicate request, unknown module, or inexact landscape order stops planning before
history, dependencies, and parameters and therefore produces `NOT_EVALUATED`. A plan that reaches the history read is
`EVALUATED`, even when its dependency or resolved-parameter collection is genuinely empty. The CLI MUST render the two
unevaluated sections as `Dependency validation: (not evaluated)` and `Resolved parameters: (not evaluated)`.
`✓ No dependencies.` and `(none)` are reserved for evaluated empty results.

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

Path and requested-module selection are the only stages evaluated before this precedence decision. If the path has a
predictable problem, preflight MUST preserve that path problem together with any duplicate, unknown-module, or exact-set
problem already calculated, but MUST NOT read project history, inspect Git, validate dependencies, or resolve parameters.
The rejected snapshot keeps the requested modules, any calculated execution order, and commit mode, while marking
detailed planning `NOT_EVALUATED`. This avoids replacing the actionable path diagnostic with a project-dependent read
failure or presenting unevaluated empty collections as successful validation.

Reading project history, catalog metadata, filesystem metadata, and Git status is allowed. When the core history-read API
returns normally:

- absent project history MUST be treated as an empty history snapshot; and
- a relevant returned value that is unsupported or incompatible with its catalog type MUST be aggregated into the invalid
  preflight report and return exit code `2`.

Any exception thrown by the core history-read API MUST be treated as an unexpected failure and return exit code `1`,
still before any mutation. This includes exceptions caused by malformed persisted content or I/O failures. Such an
exception is opaque to the CLI: the CLI MUST NOT inspect its type, cause, or message, or read the core history storage
directly, to reclassify it as an invalid preflight.

### Exact requested-set invariant

The calculated execution order MUST contain every requested module exactly once and no module that was not explicitly
requested. Concretely, after duplicate rejection, its length and set of slugs MUST equal those of the requested module
list. Any mismatch invalidates preflight.

Dependencies can be satisfied from history or from an earlier requested module, but the planner MUST NOT add a missing
module or feature provider implicitly. It also MUST NOT omit an explicitly requested module because history already lists
it.

### Dirty Git warning

When commits are enabled and the destination belongs to an existing dirty Git worktree, preflight MUST emit a `WARNING`
on `stderr`. Dirty means that Git reports tracked, staged, or untracked changes relevant to that worktree.

For an execution invocation, the warning MUST explain that per-module commits can include or be affected by pre-existing
changes and explicitly confirm that execution will continue automatically:

```text
WARNING: Git worktree <path> is dirty; module commits can include or be affected by pre-existing changes. Execution will continue automatically.
```

For `--plan`, the warning MUST instead describe only a possible later execution and confirm that the current invocation is
read-only:

```text
WARNING: Git worktree <path> is dirty; module commits in a later execution can include or be affected by pre-existing changes. This plan is read-only; no modules will be applied.
```

Both warnings are strictly informational. Neither may recommend cleaning, stashing, another intervention, or
`--no-commit`; invalidate the plan; prevent the requested planning or execution flow; or change an otherwise successful
exit code `0`. Only the execution warning may claim automatic continuation.

The dirty-worktree check is not required when `--no-commit` is selected because this command will perform no Git
initialization or commit in that mode.

## Parameter and history semantics

A property key is one global catalog concept and MUST have exactly one `STRING`, `INTEGER`, or `BOOLEAN` type across the
complete catalog. The official Seed4J catalog adapter MUST validate that invariant after mapping external resources and
fail deterministically as an internal inconsistency before exposing Picocli options. If another catalog implementation
supplies selected definitions with distinct types, preflight MUST report them in deterministic type order, for example
`shared: conflicting types (INTEGER, STRING)`, and remain invalid regardless of selected-module order.

The catalog MUST remain stable for the lifetime of the planner. Resources, landscape, and extensions are fixed before the
Picocli tree and its global property options are assembled, and one CLI process executes one command invocation. The
planner does not snapshot a changing catalog and does not support catalog hot reload during `apply-set`.

No reconciled definition may be produced for a type-conflicting key. That key remains known for unused-option validation,
but MUST NOT resolve against explicit input, project history, or defaults and MUST NOT produce diagnostics derived from an
arbitrarily chosen type. Other property keys continue reconciliation and validation so their independent problems remain
aggregated.

Programmatic callers MUST provide explicit domain values whose types match the single reconciled types. A mismatch is an
internal invariant violation, not caller-correctable preflight input: planning MUST throw a dedicated domain exception
before parameter resolution and MUST NOT add a planning problem or CLI diagnostic for it. Picocli prevents this mismatch
for the stable official catalog by parsing each option directly into its declared domain type.

Parameter resolution keeps this precedence:

1. explicit CLI input;
2. the latest compatible project-history value;
3. a module metadata default, for display only.

The approved plan MUST contain one effective global parameter map. That map contains every explicit value and every latest
compatible project-history value selected for the requested set. Every individual Seed4J core call MUST receive that same
complete map, with the same keys and resolved values; the CLI MUST NOT filter it to the current module's declared
properties.

Informational defaults MUST NOT be entries in the effective global parameter map. A value equal to a metadata default is
an entry only when its source is explicit input or compatible project history. The execution adapter MUST NOT materialize
a displayed metadata default as core input. A default for a mandatory property does not make the preflight valid.

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
result, or history interpretation between approval and execution. Module identity and commit mode vary per individual
core request, but each request MUST carry the same immutable effective global parameter map from that plan.

This prevents internal planning drift; it cannot eliminate external time-of-check/time-of-use changes. An external change
after preflight can still make an individual application fail.

## Sequential execution algorithm

After a valid execution preflight, the CLI MUST:

1. create a result slot for every slug in the plan's execution order;
2. obtain the effective global parameter map from the approved plan once;
3. visit that order once, sequentially, without parallel work;
4. call the existing **individual-module** Seed4J application API for that module, passing the selected commit mode and
   the same complete effective global parameter map used for every other individual call;
5. mark the module `SUCCEEDED` only when its `events.dispatch(...)` call returns normally, which is the normal return
   boundary of the individual call;
6. on the first thrown exception, mark that module `FAILED`, mark every uninvoked later module `SKIPPED`, and stop; and
7. render a final summary containing every requested module.

The CLI MUST NOT use the Seed4J multi-module application API. That API performs its own multi-module transformation and
does not preserve the CLI's approved plan as the sole ordering and reporting boundary. The CLI MUST NOT reproduce module
application behavior; the individual API remains responsible for applying each module.

No change to the `seed4j/seed4j` repository is required for this first execution contract.

## Module result semantics

The three module statuses have these exact meanings:

| Status      | Meaning                                                                                                                                                                                                                         |
| ----------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `SUCCEEDED` | The individual API reached `events.dispatch(...)`, that dispatch call returned normally, and the individual call returned normally. This status does not guarantee completion of event listeners or their asynchronous effects. |
| `FAILED`    | The individual call threw, including a throw from `events.dispatch(...)`. Its effects are indeterminate and can include files, project history, a commit, dispatched events, or downstream event effects.                       |
| `SKIPPED`   | The module was not invoked because an earlier module was `FAILED`. It has no effects from this invocation.                                                                                                                      |

`FAILED` does not mean “no changes.” Even if the exception appears to originate late in the call, the CLI MUST NOT infer
which effects occurred from exception type, current files, history, Git, or observed events. The failure report MUST call
the effects indeterminate.

Every `SUCCEEDED` module before a failure remains successful. Its files, history, commit when enabled, and event dispatch
remain in place. The CLI MUST NOT reset, revert, amend, delete, compensate, or dispatch compensating events. This says
nothing about whether listeners or asynchronous downstream effects have completed.

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

- `SUCCEEDED` guarantees that the module's history update completed and that `events.dispatch(...)` returned normally;
  it does not guarantee completion of event listeners or asynchronous effects;
- `FAILED` provides no guarantee about whether its history update, event dispatch, listeners, or asynchronous effects
  occurred or completed;
- `SKIPPED` guarantees that no history update or event dispatch came from that module in this invocation; and
- earlier successful history updates and event dispatches are never rolled back.

The Seed4J individual application API, not the CLI, owns file generation, history persistence, Git initialization and
commit creation, and event dispatch. The CLI owns orchestration and reports the normal return of `events.dispatch(...)`
as the observable success boundary, without extending that boundary to listeners or asynchronous effects.

## Output and exit codes

Human-readable text is the only format in this contract.

| Stream   | Content                                                                                                                                                            |
| -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `stdout` | A valid plan; execution progress; the per-module final summary; and overall `SUCCEEDED` or `PARTIAL_FAILURE`. It MUST be empty for an invalid preflight.           |
| `stderr` | Warnings; the complete invalid preflight report; a concise unexpected-failure cause; and a safe next action when applicable. No stack trace is printed by default. |

For an invalid preflight, the complete human-readable report, including the `Preflight: INVALID` heading, every
aggregated diagnostic, any safe next action, and `No changes were applied.`, MUST be written to `stderr`; `stdout` MUST
remain empty.

The progress line and final summary MUST use the literal module statuses `SUCCEEDED`, `FAILED`, and `SKIPPED`. A failed
execution MUST use the literal overall status `PARTIAL_FAILURE`. Warnings and `reapplied` annotations do not replace a
module status.

The exit-code contract is:

| Exit code | Meaning                                                                                                                                                                                                                                                                                                     |
| --------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `0`       | `--plan` produced a valid plan, or execution completed with every module `SUCCEEDED`. Warnings do not change this code.                                                                                                                                                                                     |
| `2`       | Command usage or preflight is invalid, including a relevant unsupported history value or one incompatible with its catalog type after a normal history read. No individual module was invoked, and no project or Git mutation was made.                                                                     |
| `1`       | An unexpected failure occurred, including every exception from the core history-read API, whether caused by malformed persisted content, I/O, or another condition. If execution started, there can be partial progress and indeterminate effects from the `FAILED` module; otherwise no mutation occurred. |

On exit code `1` after execution starts, `stderr` MUST tell the caller to inspect the working tree, project history,
relevant external event effects, and—only when commits are enabled—Git log before deciding whether to retry. With commits
enabled, it preserves the exact two guidance lines shown in the partial-progress example. With `--no-commit`, it MUST use:

```text
The failed module may have changed files, history, dispatched events, or downstream event effects. Earlier successes were preserved.
Next action: inspect the working tree, project history, and relevant event effects before deciding whether to retry.
```

It MUST NOT recommend an automatic blind retry or claim rollback.

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
      Events: dispatched
      Commit: created
[2/3] maven-java
      Status: SUCCEEDED
      History: updated
      Events: dispatched
      Commit: created
[3/3] prettier
      Status: SUCCEEDED
      History: updated
      Events: dispatched
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

`stdout`: empty

`stderr`:

```text
Preflight: INVALID
ERROR: module:init is missing; required by: maven-java.
Next action: add init explicitly before maven-java, or choose a set whose dependencies are satisfied.
No changes were applied.
```

Exit code: `2`. No individual module, Git initialization, file write, history update, commit, or event dispatch was
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
      Events: dispatched
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
The failed module may have changed files, history, Git, dispatched events, or downstream event effects. Earlier successes were preserved.
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
      Events: dispatched
      Commit: created

Summary:
  prettier  SUCCEEDED
Module set status: SUCCEEDED
```

`stderr`:

```text
WARNING: Git worktree /work/sample is dirty; module commits can include or be affected by pre-existing changes. Execution will continue automatically.
```

Exit code: `0`. The warning is informational and execution continues automatically.

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
      Events: dispatched
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
      Events: dispatched
      Commit: disabled
[2/2] maven-java
      Status: SUCCEEDED
      History: updated
      Events: dispatched
      Commit: disabled

Summary:
  init        SUCCEEDED
  maven-java  SUCCEEDED
Module set status: SUCCEEDED
```

`stderr`: empty

Exit code: `0`. Files, history, and event dispatch follow the normal individual application flow; this invocation neither
initializes Git nor creates commits. `Events: dispatched` does not assert listener or asynchronous-effect completion.

## Rejected alternatives

### Treating preflight as a dry run

Rejected. Preflight validates a plan without executing the mechanisms whose effects a dry run would need to predict. It
does not apply modules or hooks, perform Git operations, dispatch events, run listeners, or calculate execution diffs.

### Real or Git-based rollback

Rejected for the first implementation. Files, persisted history, commits, dispatched events, and downstream event effects
do not share one transaction boundary. Git cannot retract event dispatch or listener effects and is not a safe restoration
mechanism for an initially dirty or non-Git project. Advertising rollback without a capability spanning all effects would
create a false guarantee.

### One commit for the complete set

Rejected. Delaying a single commit would not make history or event dispatch transactional, and a later failure would
leave prior successful file changes without the per-module audit boundary supplied by the individual API. One completed
commit per successful module accurately records the sequential operation.

### Blocking execution on a dirty Git worktree

Rejected. Dirtiness does not make the module plan invalid and users may intentionally retain local changes. A strictly
informational, non-blocking warning explains the commit risk and confirms automatic continuation without recommending an
intervention.

### Skipping modules already in history

Rejected. An explicit slug is execution intent, and a module may be deliberately reapplied to reconcile or regenerate its
output. History can satisfy dependency and parameter planning but MUST NOT silently remove an explicit request.

### Seed4J core multi-module API

Rejected for this flow. The CLI needs the approved `ModuleSetPlan` to remain the sole exact order, parameter, progress, and
failure boundary. Invoking the core multi-module API would delegate a second multi-module transformation and obscure which
individual `events.dispatch(...)` call returned or threw. The individual API provides the required observable boundary
without a core change.

### Per-module parameter maps or materialized metadata defaults

Rejected. Defaults are catalog information used to explain possibilities, not explicit user intent or persisted project
state. Sending them would change current parameter semantics and could make a mandatory value appear supplied. Filtering
values per module would also make individual calls observe different inputs from the approved set. The CLI therefore
sends the same complete map of explicit and compatible historical values to every individual call and sends no
informational default.

### Conflating absent history, normally returned values, and read exceptions

Rejected. Absence returned normally is a valid empty history snapshot. A relevant value returned normally that is
unsupported or incompatible with its catalog type is caller-correctable preflight invalidity. Every exception thrown by
the core history-read API is an unexpected failure, including one caused by malformed persisted content or I/O.
Inspecting the exception type, cause, or message, or reading the core history storage directly, would violate the public
API boundary and make exit codes `2` and `1` unreliable.

### Treating event dispatch as downstream completion

Rejected. A normal return from `events.dispatch(...)` establishes the individual call's synchronous success boundary but
cannot guarantee completion of listeners or asynchronous effects outside that boundary.

## Responsibility boundary

| Responsibility                                                                                                                              | Owner                                      |
| ------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------ |
| CLI syntax, `--[no-]commit`, `--plan`, usage validation, streams, and exit codes                                                            | `seed4j-cli` primary adapter               |
| Read-only preflight orchestration, immutable plan, exact-set invariant, effective global parameter map, and reapply marker                  | `seed4j-cli` application/domain            |
| Catalog, landscape ordering, public core history-read API integration, path checks, Git-status reads, and individual-core integration       | `seed4j-cli` secondary adapters            |
| Sequential control, first-failure stop, result classification, progress, summary, strictly informational warnings, and failure next actions | `seed4j-cli`                               |
| Module business behavior, file changes, history persistence, Git initialization/commit, and event dispatch                                  | existing individual API in `seed4j/seed4j` |

The CLI MUST not duplicate Seed4J module application logic. The core MUST not be changed merely to add this CLI
orchestration contract.

## Acceptance audit for #297

| #   | Acceptance criterion                                    | Normative coverage                                                                                                                                                                                                                                                                                            |
| --- | ------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | Atomicity guarantees are explicit                       | The operation is sequential, non-atomic, non-transactional, and has no automatic rollback of files, history, Git, dispatch, or downstream effects.                                                                                                                                                            |
| 2   | Preflight boundaries are defined                        | It is a validated plan, not a dry run; normal history returns are separated from opaque read exceptions; practical path checks, exact-set confirmation, and the informational dirty-Git warning are specified.                                                                                                |
| 3   | Commit granularity is defined                           | Default mode creates exactly one commit per `SUCCEEDED` module; `--no-commit` creates none and does not initialize Git.                                                                                                                                                                                       |
| 4   | Partial-failure behavior is defined                     | Normal dispatch return is `SUCCEEDED`; the first thrown call is `FAILED`, later calls are `SKIPPED`, earlier successes remain, and execution stops.                                                                                                                                                           |
| 5   | Rollback support or its absence is explicit             | No file, history, Git, event-dispatch, listener-effect, or asynchronous-effect restoration or compensation is attempted.                                                                                                                                                                                      |
| 6   | Already-applied behavior is defined                     | Explicitly requested historical modules are invoked and annotated `reapplied`.                                                                                                                                                                                                                                |
| 7   | Project-history behavior is defined                     | A normal read maps absence to empty and relevant unsupported or catalog-incompatible values to invalid preflight; every read-API exception, including malformed persisted content or I/O, is unexpected; a successful call's update remains, a failed call is indeterminate, and a skipped call adds nothing. |
| 8   | Event-dispatch behavior is defined                      | `SUCCEEDED` means `events.dispatch(...)` returned normally, without guaranteeing listener or asynchronous-effect completion; failed dispatch effects are indeterminate and successful dispatches are not retracted.                                                                                           |
| 9   | Exit codes are defined                                  | `0` is valid/full success; `2` covers non-mutating usage or predictable validation of values returned normally; `1` covers unexpected failures, including every core history-read API exception without reclassification by the CLI.                                                                          |
| 10  | User-visible progress and failure reporting are defined | Streams, literal statuses, `PARTIAL_FAILURE`, concise causes, applicable next actions, complete invalid-preflight reporting on `stderr`, informational Git warning wording, and six complete examples are specified.                                                                                          |
| 11  | CLI and Seed4J core responsibilities are identified     | The table assigns planning, public history-API integration, the shared effective global map, orchestration, and reporting to the CLI, without allowing direct core history-storage inspection, and assigns individual application effects plus dispatch to the core.                                          |
| 12  | The execution issue has no unresolved product decision  | Interface, snapshots, order, global values, history outcomes, reapplication, commits, dispatch boundary, preflight meaning, streams, warnings, and failure handling are fixed here.                                                                                                                           |

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
