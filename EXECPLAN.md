# Close JaCoCo gaps and Sonar maintainability issues without synthetic coverage

## Purpose and success

Eliminate the current 16 missed lines and 18 missed branches while preserving the repository's 100% line and branch
coverage gate per class. Add tests only through public Picocli journeys, application-service contracts, or stable NIO and
JGit adapter contracts. Remove states and branches proved unreachable instead of manufacturing coverage. Preserve the
complete public output and behavior of `apply-set`; do not lower thresholds, exclude classes, test private helpers or
wiring, or add mocks and seams for JGit internals.

After the JaCoCo gaps were closed, eliminate the 14 new Sonar code smells reported for HEAD `a280a2c`: seven `MAJOR`
and seven `MINOR` issues across six files. Preserve the same public APIs, `apply-set` output, event order, JaCoCo
thresholds, and Sonar profile. Add no tests, suppressions, exclusions, or “Won't Fix” resolutions for this maintenance
milestone.

Success is observable when the requested focused suites and `./mvnw test` pass, the JaCoCo CSV query prints no class,
formatting and `git diff --check` pass, and every `habit-hooks` finding has been analyzed without changing the snooze
file. The user remains responsible for the final aggregated `./mvnw clean verify` gate and will report its exit code.

The current test-organization milestone preserves all 576 behaviors while making public CLI journeys independently
discoverable. Success additionally requires the 20 module-set planning tests to be grouped by contract inside their
existing class; the 35 `apply-set`, 28 `apply`, seven completion, and four listing tests to live in dedicated suites; and
the root suite to retain root help, debug, versioning, extension-tree registration, and no unrelated command journeys.

The current contract-correction milestone aligns `apply-set` with `.agent/specifications/apply-set-execution.md` in four
observable areas: invalid project paths stop project-dependent planning before history reads; a property key has one
global catalog type and conflicting or explicitly mistyped values produce structured invalid-preflight diagnostics;
partial-failure guidance mentions Git only when module commits are enabled; and dirty-worktree warnings distinguish a
read-only plan from an execution that continues automatically. Success requires public-boundary tests, reconciled
documentation, `./mvnw test` passing, and no missed JaCoCo line or branch. The agent must not run `./mvnw clean verify`.

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

The user-provided Sonar analysis for HEAD `a280a2c` identifies these 14 occurrences:

| Rule         | Count | Affected code                                                                  |
| ------------ | ----: | ------------------------------------------------------------------------------ |
| `java:S6878` |     4 | Planning and execution renderer record-pattern dispatch                        |
| `java:S3400` |     1 | Constant `start()` result in `ApplyModuleSetExecutionRenderer`                 |
| `java:S3457` |     2 | LF characters embedded in renderer format strings                              |
| `java:S7467` |     2 | Ignored runtime exceptions in invocation and execution service                 |
| `java:S5838` |     3 | `StringWriter` string-representation assertions in `Seed4JCommandsFactoryTest` |
| `java:S108`  |     1 | Empty bare-repository resource block in `JGitModuleSetGitStateReaderTest`      |
| `java:S1481` |     1 | Ignored bare-repository resource variable in `JGitModuleSetGitStateReaderTest` |

The four `java:S6878` recommendations conflict with the previously observed Java 25/JaCoCo behavior: record
deconstruction reintroduces compiler-generated `MatchException` handlers that cannot be exercised through real behavior.
The compatible source shape is an exhaustive type-pattern switch that passes the complete record to a named helper; the
helper reads components outside pattern matching. This satisfies the analyzer without restoring synthetic uncovered
bytecode.

For the current work, selection and path facts are calculated together, but a predictably invalid path prevents history,
dependency, parameter, Git, and application work. Selection problems already calculated remain aggregated. Property
types are global catalog facts: the official Seed4J adapter rejects inconsistent external metadata before exposing it,
while a planner supplied by another adapter returns an invalid preflight with a deterministic type conflict. Independent
properties continue to be reconciled and validated. Existing CLI syntax, exit codes, module order, rollback, core
behavior, and per-module commit behavior remain outside the change.

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

### 7. Resolve the 14 Sonar issues without changing behavior or coverage

In `ApplyModuleSetPlanningProblemRenderer`, keep the exhaustive type-pattern switch and delegate complete duplicate and
unknown-module problems to named helpers. In `ApplyModuleSetExecutionRenderer`, delegate complete start and completion
events to named helpers, replace `start()` with a package-visible constant consumed directly by
`ApplyModuleSetInvocation`, and move the two explicit LF characters outside `.formatted(...)`. Use unnamed catch
variables in `ApplyModuleSetInvocation` and `ModuleSetExecutionApplicationService`.

In `Seed4JCommandsFactoryTest`, replace only the three exact `stderr.toString()` equality assertions with AssertJ
`hasToString`. In `JGitModuleSetGitStateReaderTest`, initialize and close the bare repository directly with
`.call().close()`. Add no tests: the existing 96 focused tests remain the observable regression boundary for CLI output,
execution events, and real JGit behavior.

Validation, in order:

1. `./mvnw -Dtest=Seed4JCommandsFactoryTest,ModuleSetExecutionApplicationServiceTest,JGitModuleSetGitStateReaderTest test`
2. `npm run prettier:format`
3. `npm run prettier:check`
4. `git diff --check`
5. `./mvnw test`
6. `awk -F, 'NR > 1 && ($6 > 0 || $8 > 0)' target/site/jacoco/jacoco.csv`
7. `habit-hooks`

Expected: the focused command exits `0` with the same 96 tests as the baseline; formatting, diff, and all 576 repository
tests pass; the JaCoCo query emits no rows; every Habit finding is analyzed and enforced findings are resolved without
running `habit-snooze` or changing `.habit-hooks/snooze.json`. Acceptance requires all 14 source occurrences to be
removed with no public output, event-order, API, threshold, profile, documentation, or test additions.

The final Sonar result requires the complete gate and an asynchronous server-side analysis. Per repository policy, the
user must run or explicitly authorize
`./mvnw clean verify sonar:sonar -Dsonar.token=<token>`. After the Sonar task completes, its API must report zero open new
issues, Quality Gate `OK`, and no issue introduced by the helper organization.

### 8. Reorganize tests around public command journeys

In `ModuleSetPlanningApplicationServiceTest`, place the existing 20 methods into `@Nested` classes named
`PreflightEnvironment`, `RequestSelection`, `DependencyPlanning`, and `ParameterPlanning`. Move the existing 35
`apply-set` methods to `ApplyModuleSetCommandTest`, grouped as `CommandContract`, `PlanValidation`,
`ParameterResolution`, and `Execution`. Move the existing 28 `apply` methods to
`ApplyModuleCommandTest`, grouped as `CommandContract`, `Planning`, and `Execution`; retain its four version methods in
`Seed4JCommandsFactoryTest`. Move the seven completion methods to `CompletionCommandTest` and the four listing methods
to `ListModulesCommandCliTest`, avoiding the existing unit-level `ListModulesCommandTest` contract.

Each extracted integration suite keeps `@IntegrationTest`, `OutputCaptureExtension`, the real command tree, injected
application services, and `CliFixture`. Keep helpers inside the narrowest suite or nested journey that uses them; do not
introduce inheritance, shared fixture bases, distinct Spring properties, context mutation, production changes, renamed
tests, changed assertions, or altered observable effects.

Validation, from narrow to broad:

1. `npm run prettier:format`
2. `./mvnw -Dtest=ModuleSetPlanningApplicationServiceTest test` — exactly 20 tests
3. `./mvnw -Dtest=Seed4JCommandsFactoryTest,ApplyModuleSetCommandTest test`
4. `./mvnw -Dtest=Seed4JCommandsFactoryTest,ApplyModuleSetCommandTest,ApplyModuleCommandTest test`
5. `./mvnw -Dtest=Seed4JCommandsFactoryTest,ApplyModuleSetCommandTest,ApplyModuleCommandTest,CompletionCommandTest,ListModulesCommandCliTest test` — exactly 86 CLI tests
6. Combined planning and CLI command — exactly 106 tests
7. `npm run prettier:check`
8. `git diff --check`
9. `./mvnw test` — exactly 576 tests
10. `awk -F, 'NR > 1 && ($6 > 0 || $8 > 0)' target/site/jacoco/jacoco.csv` — no output
11. `habit-hooks`, with every finding analyzed and `.habit-hooks/snooze.json` unchanged

Acceptance: all method names, annotations that determine executions, Given/When/Then bodies, arguments, assertions,
messages, exit codes, filesystem/history/Git effects, and the total test count remain unchanged. Only test ownership,
nested grouping, imports, and this durable plan may differ.

### 9. Preserve predictable invalid-path precedence

Update `ModuleSetPlanner` so it still computes path and selection results, but calls its selected-module planning flow
only when the path has no problem and selection is approved. Preserve requested modules, computed order, commit mode,
and accumulated selection/path problems in a rejected plan while leaving dependencies and effective parameters empty.
Strengthen `ModuleSetPlanningApplicationServiceTest` and `ApplyModuleSetCommandTest` so history access fails if reached
and public output asserts exit `2`, empty stdout, the exact path diagnostic, `Status: INVALID`, and
`No changes were applied.`, with no project-dependent read, Git inspection, or application.

Validation:
`./mvnw -Dtest=ModuleSetPlanningApplicationServiceTest,ApplyModuleSetCommandTest test` must exit `0`.
Acceptance: a predictable invalid path wins before every project-dependent step while already-calculated selection
problems remain observable.

### 10. Make property types globally invariant and safely validated

Extend the sealed `ModuleSetPropertyConflict` hierarchy with `ModuleSetPropertyTypeConflict`, reconcile the distinct
types for each key deterministically, emit no reconciled definition for a conflicting key, and keep that key known for
unused-option validation. Add a structured planning problem for an explicit value whose runtime type differs from the
reconciled definition; render the exact required mismatch and type-conflict diagnostics, omit incompatible values from
effective parameters, and continue aggregating independent property problems. Update `Seed4JModuleSetCatalog` to reject
repeated mapped definitions whose types differ with deterministic `IllegalArgumentException` text before CLI exposure.
Protect module-order invariance, absence of derived diagnostics, independent validation, explicit mismatch behavior,
official-adapter rejection, and preserved compatible default/description conflict behavior in
`ModuleSetPlanningApplicationServiceTest`, `Seed4JModuleSetCatalogTest`, and `ApplyModuleSetCommandTest`.

Validation:
`./mvnw -Dtest=ModuleSetPlanningApplicationServiceTest,Seed4JModuleSetCatalogTest,ApplyModuleSetCommandTest test` must
exit `0`.
Acceptance: every property key has one type across a valid catalog, inconsistent alternate adapters produce invalid
preflight without execution, and no conflicting or explicitly mistyped value reaches effective parameters.

### 11. Make partial-failure guidance follow commit mode

Update `ApplyModuleSetExecutionRenderer` to derive failure effects and next-action text from `ModuleSetCommitMode`.
Preserve the current commit-enabled wording byte for byte; omit both `Git` and `Git log` from the `--no-commit` wording.
Add a public partial-failure journey in `ApplyModuleSetCommandTest` that observes exit `1`, ordered
`SUCCEEDED`/`FAILED`/`SKIPPED` statuses, `PARTIAL_FAILURE`, hidden exception internals, and commit-disabled guidance.
Retain commit-enabled regression coverage and application-service behavior in
`ModuleSetExecutionApplicationServiceTest`.

Validation: `./mvnw -Dtest=ApplyModuleSetCommandTest,ModuleSetExecutionApplicationServiceTest test` must exit `0`.
Acceptance: failure recovery guidance mentions Git only when the invocation was allowed to create module commits.

### 12. Contextualize dirty-worktree warnings for planning and execution

Update `ApplyModuleSetInvocation` to determine intent before warning rendering. Give
`ApplyModuleSetWarningRenderer` named planning and execution paths instead of a behavioral boolean. Preserve the exact
execution warning and add the exact read-only planning warning. Extend `ApplyModuleSetCommandTest` with a real dirty Git
repository under `--plan`, observing exit `0`, planning-only warning, `No changes were applied.`, unchanged history and
commits, and no application. Preserve automatic execution continuation and ensure `--plan --no-commit` still performs
no Git inspection. Retain the real adapter contract in `JGitModuleSetGitStateReaderTest`.

Validation: `./mvnw -Dtest=ApplyModuleSetCommandTest,JGitModuleSetGitStateReaderTest test` must exit `0`.
Acceptance: only execution claims automatic continuation, while a plan explicitly states that it is read-only.

### 13. Reconcile documentation and complete local validation

Update `.agent/specifications/apply-set-execution.md`, `documentation/Commands.md`, and
`documentation/hexagonal-architecture.md` with path precedence, globally invariant types, commit-mode-specific recovery
guidance, distinct dirty warnings, and official-adapter consistency responsibility. Audit every sealed switch and new
branch through public or stable application/adapter behavior, without JaCoCo exclusions or artificial helper tests.
Format, run the exact focused and repository-wide checks, analyze every Habit finding, confirm the snooze file is
unchanged, re-read all requirements, and record actual outcomes here immediately before handoff.

Validation, in order:

1. `npm run prettier:format`
2. `npm run prettier:check`
3. `git diff --check`
4. `./mvnw -Dtest=ModuleSetPlanningApplicationServiceTest,ModuleSetExecutionApplicationServiceTest,ApplyModuleSetCommandTest,Seed4JModuleSetCatalogTest,NioModuleSetProjectPathValidatorTest,JGitModuleSetGitStateReaderTest test`
5. `./mvnw test`
6. `awk -F, 'NR > 1 && ($6 > 0 || $8 > 0)' target/site/jacoco/jacoco.csv`
7. `habit-hooks`

Expected: formatting, diff, and tests exit `0`; the JaCoCo query prints nothing; no introduced or unresolved enforced
Habit finding remains; `.habit-hooks/snooze.json` is unchanged and `habit-snooze` is never run. The user then runs
`./mvnw clean verify` and reports its exit code plus a concise relevant failure summary, if any.

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
- [x] Read the four-contract request and add milestones 9–13 while preserving all history and the pending Sonar gate.
- [x] Preserve invalid-path precedence before project-dependent planning and validate it at public boundaries.
- [x] Enforce global property types, explicit input type safety, and deterministic catalog rejection.
- [x] Make partial-failure recovery guidance conditional on commit mode.
- [x] Render distinct dirty-worktree warnings for read-only planning and automatic execution.
- [x] Reconcile the specification and public architecture/command documentation.
- [x] Run the requested local validation sequence, analyze Habit, audit scope, and update this plan before handoff.
- [x] Re-audit the green change with the explicitly requested quiet behavior-TDD skill without manufacturing a retroactive red.
- [x] Confirm HEAD `a280a2c`, a clean worktree, and record the 14-issue Sonar milestone before implementation.
- [x] Resolve all 14 Sonar source occurrences without changing tests or observable behavior.
- [x] Run the seven requested local validation commands in order and analyze every Habit finding.
- [x] Re-read the Sonar request and update this ExecPlan immediately before handoff.
- [ ] Obtain the authorized complete Sonar gate and confirm the asynchronous result through the Sonar API.
- [x] Read the test-journey request and inventory the existing planning and CLI methods.
- [x] Group the 20 planning tests into four behavioral nested classes.
- [x] Extract and group the 35 `apply-set` CLI tests.
- [x] Extract and group the 28 `apply` CLI tests while returning four version tests to the root suite.
- [x] Extract the seven completion and four listing CLI tests.
- [x] Run focused and repository-wide validation, analyze Habit findings, and audit the final structure and counts.
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
  For the four `java:S6878` occurrences, exhaustive type patterns delegate the complete records to named helpers, and
  those helpers use explicit record accessors outside pattern matching. This satisfies Sonar while avoiding the
  unreachable lines produced by record deconstruction.
- Predictably invalid paths short-circuit only project-dependent planning; requested modules, computed order, commit mode,
  and path/selection problems remain in the rejected plan. Continuing history or parameter work was rejected because it
  can mask the actionable path diagnostic and touch a project already known to be unusable.
- Property type is a global key invariant. The official catalog rejects inconsistent external metadata as an internal
  adapter error, while the domain planner still diagnoses inconsistent input from alternate catalog implementations.
  Conflicting keys are excluded from value resolution but remain known, preventing arbitrary-type and unused-option
  follow-on diagnostics.
- Warning rendering uses named planning and execution operations. A boolean renderer argument was rejected because it
  obscures the caller's intent and makes the two human/agent-facing contracts easier to accidentally interchange.
- The quiet behavior-TDD request arrived after production and behavior tests were already green. Reverting working code
  merely to manufacture a red state was rejected. The completed audit instead verifies that every added test lives in an
  existing behavioral suite, uses Given/When/Then, observes a CLI journey, application contract, or official adapter
  contract, and remains insensitive to private helper or production-file topology.

## Risks

- POSIX access checks depend on the executing user. The supported Linux CI runs as a non-root user; tests first verify the
  POSIX view and restore owner access in `finally`. A root-only local environment may not observe denied access and must
  be reported as an environment limitation rather than weakened assertions.
- Corrupting a repository index must be confined to a temporary directory. The fixture owns the repository and no user
  checkout is modified.
- Renderer refactoring must preserve exact whitespace and line ordering. Existing Picocli success, partial-failure, and
  skipped-module journeys are the regression boundary; no internal renderer test will be added.
- The new sealed conflict and mismatch variants add exhaustive branches in reconciliation and rendering. Each must be
  reached through a selected-module planning or public CLI journey so the zero-miss JaCoCo gate remains meaningful.
- Short-circuiting an invalid path deliberately reduces dependency/history/parameter aggregation; selection diagnostics
  are the only already-computed problems preserved alongside it.
- External catalog inconsistency is not actionable CLI input and must never be reformatted as an ordinary invalid option.

## Validation

Current contract-correction evidence:

- Milestone 9 focused validation
  `./mvnw -Dtest=ModuleSetPlanningApplicationServiceTest,ApplyModuleSetCommandTest test` exited `0` with 59 tests, no
  failures, errors, or skips. The application contract now throws if history is read after `NOT_DIRECTORY`; the
  parameterized CLI path cases throw on any history read, assert exit `2`, empty stdout, exact path diagnostics and final
  invalid/no-change output, and observe no module application. Git readers also remain fail-fast in the invalid-path
  application case. The rejected plan preserves its selected item and has empty dependencies and parameters.
  A second application journey combines an invalid path with duplicate and unknown requested modules, proving that the
  already-computed selection diagnostics are preserved while history and Git reads remain unreachable.
- Milestone 10 focused validation
  `./mvnw -Dtest=ModuleSetPlanningApplicationServiceTest,Seed4JModuleSetCatalogTest,ApplyModuleSetCommandTest test`
  exited `0` with 68 tests, no failures, errors, or skips. Selected modules with `INTEGER`/`STRING` definitions yield the
  same sorted type conflict in either execution order; their key is neither resolved nor reported unused, independent
  input still resolves, and an unrelated unused option still aggregates. A programmatic explicit `STRING` for an
  `INTEGER` definition yields the structured mismatch and an empty effective map. Public CLI journeys render both exact
  diagnostics and invoke no module. The official adapter accepts compatible resources and rejects divergent mapped
  resources with `Conflicting module set property types for shared: INTEGER, STRING`.
- Milestone 11 focused validation
  `./mvnw -Dtest=ApplyModuleSetCommandTest,ModuleSetExecutionApplicationServiceTest test` exited `0` with 45 tests, no
  failures, errors, or skips. The existing commit-enabled partial failure retains the exact Git/Git-log wording. The new
  `--no-commit` journey exits `1`, invokes only the first two of three modules, reports
  `SUCCEEDED`/`FAILED`/`SKIPPED` and `PARTIAL_FAILURE`, omits exception internals, and uses the exact two recovery lines
  without any `Git` reference on stderr.
- Milestone 12 focused validation
  `./mvnw -Dtest=ApplyModuleSetCommandTest,JGitModuleSetGitStateReaderTest test` exited `0` with 50 tests, no failures,
  errors, or skips. The existing real dirty execution keeps the automatic-continuation text. A new real dirty `--plan`
  exits `0`, emits only the exact later-execution/read-only warning, ends the plan with `No changes were applied.`, invokes
  no module, and leaves project history and Git commits byte-for-byte unchanged. The seven real JGit adapter cases remain
  green; commit-disabled planning still skips Git inspection through the application contract.
- Milestone 13 documentation and final validation are complete. `.agent/specifications/apply-set-execution.md` now fixes
  invalid-path precedence, global type invariance, explicit mismatch behavior, commit-mode-specific failure guidance, and
  both exact dirty-warning contracts. `documentation/Commands.md` removes the first-definition type rule and documents
  conditional Git-log guidance; `documentation/hexagonal-architecture.md` assigns whole-catalog type validation to the
  official secondary adapter before Picocli exposure.
- `npm run prettier:format`, `npm run prettier:check`, and `git diff --check` exited `0` in the requested order. Formatting
  changed only the already touched Java files; the check reported that every matched file uses repository style and the
  diff check emitted no output.
- The exact combined command
  `./mvnw -Dtest=ModuleSetPlanningApplicationServiceTest,ModuleSetExecutionApplicationServiceTest,ApplyModuleSetCommandTest,Seed4JModuleSetCatalogTest,NioModuleSetProjectPathValidatorTest,JGitModuleSetGitStateReaderTest test`
  exited `0` with 92 tests, no failures, errors, or skips. Checkstyle reported zero violations.
- `./mvnw test` exited `0` with 585 tests, no failures, errors, or skips. The subsequent exact JaCoCo query
  `awk -F, 'NR > 1 && ($6 > 0 || $8 > 0)' target/site/jacoco/jacoco.csv` exited `0` without output, confirming zero missed
  lines and branches in all 273 reported classes.
- The post-green design review classified the new structures as behavior-preserving and cohesive. The type reconciler
  owns one invariant per key, the parameter problem collector receives distinct ordered facts without a missing domain
  abstraction, adapter validation remains at the external trust boundary, renderer policy stays primary, and metadata
  rereads are defended by the explicit mismatch problem. No supported refactor reduced risk without creating a bag,
  scattering one policy, or hiding public Given/When/Then evidence, so no refactor was applied.
- The subsequent `tdd-behavior-autonomous-quiet` audit found no implementation-detail test, new one-class-per-production-
  class suite, private helper assertion, annotation assertion, or missing public-path checkpoint. The added tests remain
  grouped by behavior in the existing application, CLI journey, and official-adapter suites. Because the skill was named
  only after the implementation was green, no false red/green history is claimed and no code was deliberately broken to
  reconstruct one.
- `habit-hooks` exited `1` with 162 reviewed heuristic locations/signals: 2 high-parameter signatures, 29 oversized
  methods, 2 cohesive public-journey files, and 129 duplicate locations across 69 groups. The changed production findings
  are the cohesive shared-property reconciliation, ordered problem aggregation, short-lived invocation routing, and
  import-block matches; the test findings are explicit public/application/adapter journeys, repeated domain setup, and
  exact output/effect assertions. The remaining constructor and most journey signals are baseline. Extracting them would
  create method-shaped bags, scatter one invariant, or hide the observable contracts required by this milestone. No
  enforced current-task defect remained, `habit-snooze` was not run, and
  `git diff --exit-code -- .habit-hooks/snooze.json` exited `0`.
- Final scope audit found only the ExecPlan, the three requested documentation files, module-set domain/planner types, the
  three primary renderers plus invocation, the official catalog adapter, and the three requested test suites changed. Two
  new domain records represent the authorized diagnostics. CLI syntax, three exit codes, Seed4J core, module order,
  rollback, and per-module commit behavior are unchanged. The pending authorized server-side Sonar milestone remains
  unchecked and was preserved.

Current test-organization evidence:

- `npm run prettier:format` exited `0`; only `ModuleSetPlanningApplicationServiceTest` required formatting.
- `./mvnw -Dtest=ModuleSetPlanningApplicationServiceTest test` exited `0` with exactly 20 tests: three preflight, four
  request-selection, seven dependency-planning, and six parameter-planning scenarios, with no failures, errors, or skips.
- After extracting the 35 `apply-set` methods, `npm run prettier:format` exited `0` and
  `./mvnw -Dtest=Seed4JCommandsFactoryTest,ApplyModuleSetCommandTest test` exited `0` with 47 executions: 8 root and 39
  `apply-set` parameter-expanded scenarios, with no failures, errors, or skips. Checkstyle also reported zero violations.
- After extracting the 28 single-module methods and moving the four version methods to the root suite,
  `./mvnw -Dtest=Seed4JCommandsFactoryTest,ApplyModuleSetCommandTest,ApplyModuleCommandTest test` exited `0` with exactly
  75 executions, no failures, errors, or skips.
- The five reorganized CLI suites together exited `0` with exactly 86 executions: 8 root, 39 parameter-expanded
  `apply-set`, 28 `apply`, 7 completion, and 4 listing scenarios, with no failures, errors, or skips.
- The combined planning and CLI command exited `0` with exactly 106 executions, no failures, errors, or skips.
- `npm run prettier:check` and `git diff --check` exited `0`. A path audit confirmed no `src/main` change, and the four
  extracted suites use the same `@IntegrationTest` plus `OutputCaptureExtension` configuration as the root suite, with
  no distinct properties, context mutation, inheritance, or shared fixture base.
- `./mvnw test` exited `0` with the unchanged 576 tests, no failures, errors, or skips. The subsequent JaCoCo CSV query
  exited `0` without output, confirming zero missed lines and branches in every reported class.
- A sorted method-name comparison against the original `Seed4JCommandsFactoryTest` produced no diff. The final suites
  contain exactly 8 root, 35 `apply-set`, 28 `apply`, 7 completion, and 4 listing methods; parameter expansion accounts
  for the unchanged 86 CLI executions.
- `habit-hooks` exited `1` with 203 reviewed signals: 4 pre-existing excessive-parameter locations, 32 oversized
  production/test methods, 3 cohesive journey files over the generic size threshold, and 164 duplicate-code locations
  across 91 groups. The moved test bodies and existing production/fixture code account for the method findings; the
  three large files each represent one requested public or application-service journey; and the duplicate findings are
  preserved Given/When/Then setup, exact expectations, or the repeated package/import/annotation structure necessarily
  introduced by the four requested standalone suites. Extracting those fragments would change assertions or introduce
  the artificial inheritance/fixture coupling prohibited by this milestone. No enforced current-task defect remained,
  `habit-snooze` was not run, and `.habit-hooks/snooze.json` is unchanged.
- Final scope audit found changes only in this ExecPlan and the six requested test paths: the existing planning and root
  suites plus four new journey suites. No production API, code, assertion, test name, message, CLI argument, exit code,
  filesystem/history/Git effect, Spring property, or public documentation changed.
- Follow-up naming review replaced the three `*IntegrationTest` class names with repository-style `*Test` names, removed
  top-level `@DisplayName` metadata from all four extracted suites, and removed the unused `commandsFactory` field from
  `ApplyModuleSetCommandTest`. The listing journey uses `ListModulesCommandCliTest` because `ListModulesCommandTest`
  already owns a distinct unit-level contract. The focused 86 CLI executions and all 576 repository tests remained green.

Baseline evidence from the pre-existing report:

- The user-provided Sonar analysis corresponds to the confirmed HEAD `a280a2c` and reports 14 open new code smells,
  split evenly between seven `MAJOR` and seven `MINOR` occurrences across the six milestone files. The worktree was clean
  when milestone 7 was added.

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
- Milestone 7 changed only the six requested code/test files plus this ExecPlan. Source audit confirms complete records
  cross all four type-pattern switch arms into named helpers; no record accessor remains in those arms. The constant
  start text, two unnamed catches, two LF concatenations, three `hasToString` assertions, and direct JGit close are all
  present. No test, public documentation, public API, coverage threshold, Sonar profile, suppression, or snooze entry was
  added or changed.
- The exact focused milestone gate
  `./mvnw -Dtest=Seed4JCommandsFactoryTest,ModuleSetExecutionApplicationServiceTest,JGitModuleSetGitStateReaderTest test`
  exited `0` with the baseline 96 tests, no failures, errors, or skips.
- `npm run prettier:format`, `npm run prettier:check`, and `git diff --check` ran next in the requested order and each
  exited `0`. Prettier changed only formatting in already touched files.
- `./mvnw test` exited `0` with 576 tests, no failures, errors, or skips. The subsequent
  `awk -F, 'NR > 1 && ($6 > 0 || $8 > 0)' target/site/jacoco/jacoco.csv` exited `0` with no output, so every reported
  class retains zero missed lines and branches after the helper reorganization.
- `habit-hooks` exited `1` with 195 reviewed signals: 4 excessive-parameter locations, 32 oversized functions, 2
  oversized files, and 157 locations across 80 duplicate-code groups. They are pre-existing imports, composition/fixture
  shapes, public test journeys, exact-output assertions, and adapter cleanup. No finding targets a newly introduced
  helper body, constant, catch, LF concatenation, assertion expression, or direct JGit close. Extracting the affected
  test setup/output would obscure the explicit Given/When/Then behavior, and the remaining production findings are
  outside this behavior-preserving Sonar milestone. No enforced current-task defect remained; `habit-snooze` was not run
  and `git diff --quiet -- .habit-hooks/snooze.json` exited `0`.
- Immediately before commit, the same focused command passed again with 96 tests, `./mvnw test` passed again with 576,
  Prettier and `git diff --check` passed, and the JaCoCo query remained empty. `habit-hooks` again returned only the
  reviewed 195-signal baseline; the snooze file remains unchanged.
- Server-side validation remains pending authorization and a Sonar token. The user must run or authorize
  `./mvnw clean verify sonar:sonar -Dsonar.token=<token>`; only its completed analysis can establish zero open new issues,
  Quality Gate `OK`, and no helper-organization regression through the Sonar API.

Focused validation ran at every milestone boundary. Picocli public-path checkpoints ran after milestones 1, 3, and 5,
satisfying the at-least-every-two-behaviors cadence. The final agent-side gate was `./mvnw test`; after handoff the user
runs the complete Sonar gate and reports the exit code plus any relevant failure summary and asynchronous task reference.
