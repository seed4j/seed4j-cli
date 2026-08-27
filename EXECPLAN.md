# Close JaCoCo gaps with real behavior and dead-code removal

## Purpose and success

Eliminate the current 16 missed lines and 18 missed branches while preserving the repository's 100% line and branch
coverage gate per class. Add tests only through public Picocli journeys, application-service contracts, or stable NIO and
JGit adapter contracts. Remove states and branches proved unreachable instead of manufacturing coverage. Preserve the
complete public output and behavior of `apply-set`; do not lower thresholds, exclude classes, test private helpers or
wiring, or add mocks and seams for JGit internals.

Success is observable when the requested focused suites and `./mvnw test` pass, the JaCoCo CSV query prints no class,
formatting and `git diff --check` pass, and every `habit-hooks` finding has been analyzed without changing the snooze
file. The user remains responsible for the final aggregated `./mvnw clean verify` gate and will report its exit code.

## Context and limits

The coverage baseline already present in `target/site/jacoco/jacoco.csv` reports exactly 16 missed lines and 18 missed
branches across six classes:

| Class                                   | Missed lines | Missed branches | Classification                                                             |
| --------------------------------------- | -----------: | --------------: | -------------------------------------------------------------------------- |
| `ModuleSetExecutionApplicationService`  |            1 |               1 | Stable application-service rejection contract                              |
| `JGitModuleSetGitStateReader`           |            3 |               3 | Real bare/corrupt JGit adapter behavior plus one external-failure boundary |
| `NioModuleSetProjectPathValidator`      |            2 |               6 | Real POSIX permission behavior plus unreachable root traversal branches    |
| `ModuleSetRequestSelector`              |            0 |               1 | Real same-cardinality/wrong-set catalog behavior through Picocli           |
| `ApplyModuleSetPlanningProblemRenderer` |           10 |               6 | Three real invalid-path diagnostics plus impossible `VALID` representation |
| `ApplyModuleSetExecutionRenderer`       |            0 |               1 | Compiler-generated exhaustive-switch artifact                              |

Production and tests remain inside the existing hexagonal boundaries. Raw filesystem and Git mechanisms stay in
`infrastructure.secondary`; planning facts stay in `domain`; application execution rejects an invalid plan before any
side effect; presentation text stays in `infrastructure.primary`. Tests must not call or name private helpers and must
observe only CLI streams/exit status, application ports/events, or stable adapter return values and failures.

POSIX permission cases run only when the file store supports the POSIX view. Tests set permissions explicitly, assert
the behavior on Linux, and restore owner `rwx` in `finally` so temporary-directory cleanup remains possible. The NIO
validator continues to use `NOFOLLOW_LINKS` for physical component discovery and follows usable links for directory and
access checks.

Characterization tests may start green where behavior already exists. A red/green cycle is required only for missing or
incorrect behavior; production code will not be temporarily broken to manufacture a red test. The repository guidance
forbids the agent from running `./mvnw clean verify` unless explicitly asked.

## Milestones

### 1. Represent invalid paths without an impossible valid state

Convert `InvalidModuleSetProjectPath` from a record carrying all `ModuleSetProjectPathStatus` values into an enum with
only `NOT_DIRECTORY`, `NOT_ACCESSIBLE`, and `NOT_APPARENTLY_CREATABLE`. Remove
`ModuleSetProjectPathStatus.valid()`. In `ModuleSetPreflightEnvironmentInspector`, map all four validator statuses
explicitly and exhaustively, including `VALID` to no problem. Update `ApplyModuleSetPlanningProblemRenderer` to translate
only the three invalid problems. Reconcile existing domain expectations and add a parameterized public journey to
`Seed4JCommandsFactoryTest` using the stable `ModuleSetProjectPathValidator` port. For each problem, assert exit `2`,
empty stdout, the exact stderr diagnostic, `Status: INVALID`, final `No changes were applied.`, and no module application.

Validation: `./mvnw -Dtest=Seed4JCommandsFactoryTest,ModuleSetPlanningApplicationServiceTest test` must exit `0`.
Acceptance: no invalid-path value can represent `VALID`, all three exact diagnostics are protected through Picocli, and
the command remains read-only for invalid preflight.

### 2. Cover real permissions and remove impossible root traversal

Add parameterized POSIX behavior cases in `NioModuleSetProjectPathValidatorTest` for an existing directory with owner
`rw-------` and `r-x------`, both yielding `NOT_ACCESSIBLE`, and for an absent destination below an ancestor with those
same permission sets, both yielding `NOT_APPARENTLY_CREATABLE`. Restore owner `rwx------` in `finally`. In
`NioModuleSetProjectPathValidator`, remove the unreachable null checks around absolute-path ancestor traversal while
retaining first-physical-component discovery and symlink semantics.

Validation: `./mvnw -Dtest=NioModuleSetProjectPathValidatorTest test` must exit `0`.
Acceptance: Linux permission failures are covered through the stable NIO adapter and absolute traversal has no synthetic
`ancestor == null` branch.

### 3. Protect selection and execution invariants

Add a Picocli journey in `Seed4JCommandsFactoryTest` where the catalog returns an execution order with the requested
cardinality but replaces one requested module. Assert exit `2`, empty stdout, the complete
`ModuleSetExecutionOrderMismatch` diagnostic, and no history read or module application. Add an application-service test
that passes an invalid `ModuleSetPlan` through `ModuleSetExecutionApplicationService.execute` and asserts rejection before
the applier or event listener receives an effect.

Validation:
`./mvnw -Dtest=Seed4JCommandsFactoryTest,ModuleSetExecutionApplicationServiceTest,ModuleSetPlanningApplicationServiceTest test`
must exit `0`.
Acceptance: equal cardinality cannot conceal a changed module set, and invalid plans cannot cross the execution
side-effect boundary.

### 4. Cover real JGit failure modes without internal mocks

In `JGitModuleSetGitStateReaderTest`, create a real bare repository and assert `NO_WORKTREE`; create a normal repository,
corrupt its index, and assert `IllegalStateException` with exact message `Unable to read Git worktree state` and the
original JGit failure preserved as cause. In `JGitModuleSetGitStateReader`, retain no-worktree and clean/dirty detection
but wrap JGit operations in one boundary catching `Exception`, never `Error`, so checked and runtime external failures
share one stable adapter failure contract. Do not introduce test-only abstractions.

Close the non-null `Repository` explicitly in `finally` rather than retaining the compiler's impossible nullable-resource
branch from try-with-resources. The adapter must still close repositories for bare, clean/dirty, and failing status reads,
and the outer failure boundary remains the only normalization point.

Validation: `./mvnw -Dtest=JGitModuleSetGitStateReaderTest test` must exit `0`.
Acceptance: real bare and corrupt repositories exercise the adapter and technical failures preserve their cause.

### 5. Remove the execution renderer's synthetic switch branch

Rewrite `ApplyModuleSetExecutionRenderer.completed` so an exhaustive switch expression produces the status-detail text
for `SUCCEEDED`, `FAILED`, and `SKIPPED`, then append it to the existing output. Add no switch-specific test; existing
public journeys already observe all three statuses and protect byte-for-byte output.

If the final Java 25/JaCoCo report attributes synthetic `MatchException` handlers to exhaustive record-pattern dispatch
switches, retain exhaustive switch expressions but replace record deconstruction patterns with type patterns plus stable
record accessors. This removes compiler-only handler lines without inventing impossible tests or changing dispatch.

Validation: `./mvnw -Dtest=Seed4JCommandsFactoryTest,ModuleSetExecutionApplicationServiceTest test` must exit `0`.
Acceptance: public progress output is unchanged and the compiler no longer reports an uncovered synthetic default branch.

### 6. Consolidate design and complete repository validation

Format the touched files, run the requested checks, review only the changed behavior and adjacent contracts for
behavior-preserving structural risks, and analyze every Habit finding. Re-read every requirement and update this plan at
handoff with actual command outcomes and any remaining limitation. Do not change `.habit-hooks/snooze.json` or run
`habit-snooze` without explicit authorization.

Validation, in order:

1. `npm run prettier:format`
2. `npm run prettier:check`
3. `git diff --check`
4. `./mvnw -Dtest=HexagonalArchTest,Seed4JCommandsFactoryTest,ModuleSetPlanningApplicationServiceTest,ModuleSetExecutionApplicationServiceTest,NioModuleSetProjectPathValidatorTest,JGitModuleSetGitStateReaderTest test`
5. `./mvnw test`
6. `awk -F, 'NR > 1 && ($6 > 0 || $8 > 0)' target/site/jacoco/jacoco.csv`
7. `habit-hooks`

Expected: formatting and all tests exit `0`; the `awk` command prints no row; zero missed branches and lines remain in
every class; no threshold or class exclusion changes; Habit has no unresolved enforced finding.

## Progress

- [x] Read the request, repository instructions, current ExecPlan, and applicable execution/TDD skills.
- [x] Record the observed JaCoCo baseline and classify all six affected classes before implementation.
- [x] Remove the valid state from the invalid-path representation.
- [x] Cover the three public invalid-path diagnostics.
- [x] Cover inaccessible existing directories and non-creatable ancestors.
- [x] Remove impossible absolute-root traversal branches.
- [x] Cover equal-cardinality execution order with the wrong module set.
- [x] Cover application-service rejection of an invalid plan without effects.
- [x] Cover a real bare repository and real corrupt Git index.
- [x] Normalize external JGit failures at one adapter boundary.
- [x] Convert execution completion details to an exhaustive switch expression.
- [x] Confirm zero missed JaCoCo lines and branches in every class.
- [x] Run and analyze `habit-hooks` without altering the snooze baseline.
- [x] Re-read the request and update this ExecPlan immediately before handoff.

## Decisions

- Invalid project paths use a closed enum containing only the three invalid business problems. The alternative—retaining
  `ModuleSetProjectPathStatus` inside the problem—permits a contradictory `VALID` problem and forces unreachable renderer
  behavior. The validator still returns all four environmental results, and the inspector owns their explicit mapping.
- External JGit failures are normalized once at the secondary adapter boundary as
  `IllegalStateException("Unable to read Git worktree state", cause)`. Test-only seams and mocks were rejected because
  real repositories can cover caller-observable adapter behavior and JGit 7.5 has no concrete bytecode path for every
  checked exception declared by its API.
- `FileRepositoryBuilder.build()` either returns the repository or throws; the adapter does not model a nullable resource.
  Explicit `finally` closure preserves resource ownership while removing the compiler-only null branch emitted by
  try-with-resources.
- Compiler-generated switch defaults and mathematically unreachable absolute-root branches are removed through exhaustive
  source shapes, not tests written to trigger impossible states. This keeps coverage tied to executable behavior.
- Java 25 record-pattern dispatch may emit synthetic accessor-failure handlers attributed to a source closing brace.
  Type patterns with explicit record accessors preserve exhaustive typed dispatch while avoiding those unreachable lines.

## Risks

- POSIX access checks depend on the executing user. The supported Linux CI runs as a non-root user; tests first verify the
  POSIX view and restore owner access in `finally`. A root-only local environment may not observe denied access and must
  be reported as an environment limitation rather than weakened assertions.
- Corrupting a repository index must be confined to a temporary directory. The fixture owns the repository and no user
  checkout is modified.
- Renderer refactoring must preserve exact whitespace and line ordering. Existing Picocli success, partial-failure, and
  skipped-module journeys are the regression boundary; no internal renderer test will be added.

## Validation

Baseline evidence from the pre-existing report:

- `awk -F, 'NR > 1 && ($6 > 0 || $8 > 0) { print; missed_branches += $6; missed_lines += $8 } END { ... }' target/site/jacoco/jacoco.csv`
  printed the six classified classes and totals `TOTAL_MISSED_BRANCHES=18 TOTAL_MISSED_LINES=16`.
- The worktree was clean on branch `codex/issue-298-apply-set-execution` before this ExecPlan update.
- The new parameterized Picocli characterization for `NOT_DIRECTORY`, `NOT_ACCESSIBLE`, and
  `NOT_APPARENTLY_CREATABLE` started green against the existing output. After replacing the contradictory record with a
  closed enum and exhaustively mapping validator results, the milestone command
  `./mvnw -Dtest=Seed4JCommandsFactoryTest,ModuleSetPlanningApplicationServiceTest test` exited `0` with 105 tests, no
  failures, errors, or skips. Each case asserts exact stderr, empty stdout, exit `2`, and no module application.
- The POSIX adapter cycle initially failed only because existence cannot be observed through an untraversable ancestor;
  moving assertions after the required `finally` permission restoration made the no-write check reliable. Both
  `rw-------` and `r-x------` now cover existing-directory and absent-destination behavior. After removing the impossible
  null checks from absolute ancestor traversal, `./mvnw -Dtest=NioModuleSetProjectPathValidatorTest test` exited `0`
  with 11 tests, no failures, errors, or skips.
- The equal-cardinality/wrong-set Picocli characterization started green and its public suite exited `0` with 86 tests;
  exact invalid-preflight output, empty stdout, no history read, and no module application are asserted. The invalid-plan
  application-service characterization also started green. The milestone command covering Picocli, planning, and
  execution exited `0` with 109 tests, no failures, errors, or skips; applier calls and published events remain empty on
  rejection.
- The real bare-repository characterization started green and exercised `NO_WORKTREE`. Corrupting the index produced the
  intended red failure: JGit exposed `JGitInternalException: Not a DIRC file.` instead of the stable adapter contract.
  After consolidating all JGit work inside one `catch (Exception)` boundary, the original JGit exception is the direct
  cause of `IllegalStateException("Unable to read Git worktree state")`. The milestone command exited `0` with 7 tests,
  no failures, errors, or skips.
- `ApplyModuleSetExecutionRenderer.completed` now obtains status details from an exhaustive switch expression and appends
  that text to the existing prefix. No internal switch test was added. The public Picocli and execution-service command
  exited `0` with 89 tests, preserving success, failed, skipped, commit-enabled, and commit-disabled output.
- The first full-suite JaCoCo query after all planned behaviors reported zero missed branches but one missed line in each
  renderer. HTML and bytecode inspection traced both lines to compiler-generated `MatchException` handlers for exhaustive
  record deconstruction patterns, not to uncovered business behavior. The two dispatch switches now keep exhaustive
  type patterns while reading record components through their stable accessors.
- After replacing record deconstruction, both renderer gaps disappeared. The next full-suite report exposed one remaining
  missed branch at the JGit try-with-resources closing brace: bytecode showed the compiler's nullable-resource guard,
  although `FileRepositoryBuilder.build()` returns a repository or throws. The adapter now expresses the non-null ownership
  invariant with unconditional `finally` closure.
- Final formatting and static checks succeeded: `npm run prettier:format`, `npm run prettier:check`, and
  `git diff --check` all exited `0`.
- The exact focused gate exited `0` with 145 tests, no failures, errors, or skips:
  `./mvnw -Dtest=HexagonalArchTest,Seed4JCommandsFactoryTest,ModuleSetPlanningApplicationServiceTest,ModuleSetExecutionApplicationServiceTest,NioModuleSetProjectPathValidatorTest,JGitModuleSetGitStateReaderTest test`.
- The default agent-side gate `./mvnw test` exited `0` with 576 tests, no failures, errors, or skips. The final command
  `awk -F, 'NR > 1 && ($6 > 0 || $8 > 0)' target/site/jacoco/jacoco.csv` exited `0` without output, confirming zero missed
  lines and zero missed branches in every reported class.
- `habit-hooks` exited `1`. Every finding was reviewed: the reported parameter counts are in pre-existing composition and
  fixture code; the reported large methods/files and duplicate fragments are explicit public/application/adapter journeys,
  their safety cleanup, or imports. In the changed production adapter, the Git method remains one cohesive normalized
  external boundary. Extracting the new test setup or exact-output assertions would hide Given/When/Then behavior or the
  POSIX restoration guarantee. No enforced current-task design defect remained to fix, and `.habit-hooks/snooze.json` was
  not changed.
- The final behavior-preserving design review found no further refactor that would improve boundaries without weakening
  the stable tests. The only structural adjustments were the exhaustive type-pattern dispatch and explicit non-null JGit
  resource lifecycle documented above.
- Commit-readiness revalidation repeated the final sequence: Prettier and `git diff --check` passed; the focused gate
  passed with 145 tests; `./mvnw test` passed with 576 tests; and the JaCoCo CSV query remained empty. `habit-hooks`
  again reported only the reviewed baseline, with no snooze-file change.

Focused validation ran at every milestone boundary. Picocli public-path checkpoints ran after milestones 1, 3, and 5,
satisfying the at-least-every-two-behaviors cadence. The final agent-side gate was `./mvnw test`; after handoff the user
runs `./mvnw clean verify` and reports the exit code plus any relevant failure summary.
