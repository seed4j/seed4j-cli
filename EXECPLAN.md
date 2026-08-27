# Deliver executable `apply-set` with immutable preflight

## Purpose and success

Implement issue #298 so `seed4j apply-set` validates one immutable, read-only preflight snapshot and, unless `--plan` is
present, applies exactly the requested visible modules through the Seed4J individual-module API. Success means that the
CLI preserves deterministic ordering and parameter-source explanations, supports default-on `--[no-]commit`, reports
typed sequential progress, stops after the first thrown module application, preserves earlier successes, and never
mutates on an invalid preflight.

The normative contract is `.agent/specifications/apply-set-execution.md`. The implementation must not add implicit
dependencies or providers, materialize informational defaults as effective input, use Seed4J's multi-module API, perform
parallel work, or attempt rollback.

## Context and limits

Production code lives below `src/main/java/com/seed4j/cli/command`. Domain types and capability ports belong in
`domain/moduleset`; application services orchestrate those ports; Picocli rendering stays in `infrastructure/primary`;
NIO, JGit, project-history, and Seed4J-core integrations stay in `infrastructure/secondary`.

The existing read-only flow is `ApplyModuleSetCommand` -> `ModuleSetPlanningApplicationService` -> `ModuleSetPlanner`,
with `Seed4JModuleSetCatalog` and `ProjectsModuleSetPlanningHistoryReader` as secondary adapters. Existing observable
planning tests live in `ModuleSetPlanningApplicationServiceTest` and the `apply-set` nested suite of
`Seed4JCommandsFactoryTest`. Those suites remain the primary behavior homes; new test classes are added only for stable
capability or application contracts that do not belong there.

Use Java 25 and Node.js 22+. Follow strict behavior-first TDD: introduce one failing observable test, run the complete
relevant suite, implement the minimum passing behavior, and keep a public CLI checkpoint at least every two cycles. Do
not run `./mvnw clean verify`; repository guidance reserves that complete gate for the user unless explicitly requested.

## Definitions

- **Preflight snapshot:** the immutable `ModuleSetPlan` produced from one invocation's catalog, project path, history,
  explicit inputs, commit mode, path state, and applicable Git state.
- **Effective parameters:** only explicit values and compatible latest history values; displayed metadata defaults are
  excluded.
- **Reapplied:** an execution-order item whose slug existed in the history snapshot and is still invoked.
- **Partial failure:** one module call threw; preceding modules remain `SUCCEEDED`, that module is `FAILED`, and every
  later module is `SKIPPED` without rollback.

## Milestones

### 1. Make planning produce an execution-safe immutable snapshot

Add behavior tests to `ModuleSetPlanningApplicationServiceTest` for missing, extra, and duplicate calculated order;
reapplication; exclusion of informational defaults from the effective map; path validation; commit mode; and conditional
dirty-Git inspection. Evolve `ModuleSetPlanningRequest`, `ModuleSetPlan`, and `ModuleSetPlanner`, adding dedicated domain
types, structured problems/warnings, and capability ports under `domain/moduleset`. Wire those ports through
`ModuleSetPlanningApplicationService` without exposing filesystem or Git representations to application/domain.

Validation: run `./mvnw -Dtest=ModuleSetPlanningApplicationServiceTest test`. Expected: every new test first fails for
the intended missing behavior and the suite finishes green after each minimal implementation. Acceptance: the plan is an
immutable snapshot, rejects any requested/order mismatch, retains explicitly requested historical modules as reapplied,
contains the effective map without defaults, and consults Git only after every other preflight validation passes and only
when commits are enabled.

### 2. Execute an approved plan sequentially with typed progress

Add application-level behavior tests in `ModuleSetExecutionApplicationServiceTest` covering full success, one shared
effective map for all calls, typed started/completed events, first-failure stop, complete result slots, and reapplied
annotations. Add the stable execution result/event/status domain contracts and the capability-oriented
`ModuleSetModuleApplier`; implement `ModuleSetExecutionApplicationService` so it accepts only an approved
`ModuleSetPlan` and never replans.

Validation: run `./mvnw -Dtest=ModuleSetExecutionApplicationServiceTest test`, followed by
`./mvnw -Dtest=ModuleSetPlanningApplicationServiceTest,ModuleSetExecutionApplicationServiceTest test`. Expected: the
execution suite demonstrates red then green behavior and both application suites finish green. Acceptance: one ordered
pass calls the applier sequentially, the first throw yields `FAILED` plus later `SKIPPED`, and the final result always
contains every planned module without rollback.

### 3. Implement read-only NIO/JGit checks and the individual-core adapter

Add stable adapter behavior suites under `src/test/java/com/seed4j/cli/command/infrastructure/secondary` for existing
directories, non-directory targets, apparently creatable destinations, invalid intermediate components, absent/clean/
dirty worktrees (tracked, staged, and untracked), and exact conversion to `Seed4JModuleToApply`. Add the explicit JGit
version and dependency to `pom.xml`; implement secondary adapters with NIO, isolated JGit, and
`Seed4JModulesApplicationService.apply(Seed4JModuleToApply)`. Keep path layouts out of domain records and map property
value types exhaustively.

Validation: run focused Maven tests naming the new secondary test classes, then the application suites. Expected: no
path test creates a probe or destination; all Git states map correctly; every core call carries slug, project path,
commit mode, and the complete effective map exactly. Acceptance: no multi-module core API is referenced.

### 4. Expose execution through Picocli and preserve the individual `apply` command

Extend the existing `apply-set` behavior suite in `Seed4JCommandsFactoryTest` one journey at a time: help/completion;
`--plan`; real full success with an in-set dependency; immutable invalid-preflight behavior; per-module commits;
`--no-commit`; reapplication; dirty-worktree warning; second-module failure; opaque history-read failure; and unchanged
`seed4j apply`. Update `CliFixture` wiring and primary renderers so invalid preflight is only on stderr with exit `2`,
unexpected pre-execution failure is generic with exit `1`, and partial results/progress use the normative literal
statuses and inspection guidance.

Validation: run `./mvnw -Dtest=Seed4JCommandsFactoryTest test` after every behavior cycle and execute the command's public
path through the test fixture at least every two cycles. Expected: stdout/stderr and exit codes match the normative
contract, planning stays read-only, and execution uses one preflight plan. Acceptance: omitting `--plan` executes,
`--[no-]commit` defaults enabled, completion exposes both forms, dirty Git only warns, and `seed4j apply` remains
unchanged.

### 5. Reconcile documentation and complete repository validation

Update `documentation/Commands.md`, `documentation/hexagonal-architecture.md`, and README navigation with execution
semantics, exact flags/statuses/exit codes, safety boundaries, and the new port/adapter flow. Run the formatter, review the
green implementation for behavior-preserving structural improvements, run `habit-hooks`, analyze and resolve every
finding, then run the repository's allowed agent-side gates.

Validation: run `npm run prettier:format`, focused tests affected by formatting/refactoring,
`npm run prettier:check`, `./mvnw test`, and `habit-hooks`. Expected: every command exits `0` and no enforced Habit
finding remains. Acceptance: docs and implementation agree with the exact normative identifiers, tests remain centered
on observable contracts, and the user is asked to run `./mvnw clean verify` as the final complete gate.

### 6. Remediate execution rendering and broken-symlink preflight regressions

Extend the public `apply-set` journeys in `Seed4JCommandsFactoryTest` so a valid `--plan` displays informational defaults,
while a real execution displays a dedicated compact preflight containing only execution order, effective parameters with
sources and CLI options, and commit mode. Preserve the complete detailed renderer for valid plans and invalid preflight,
and keep informational defaults out of `effectiveResolvedParameters()` and every Seed4J core call. Add stable NIO adapter
tests for a broken destination symlink, a broken intermediate symlink, and a valid directory symlink, including proof
that validation creates no destination or target. Implement physical component discovery with
`Files.exists(path, LinkOption.NOFOLLOW_LINKS)` while retaining followed-link directory and permission checks. Reconcile
`documentation/Commands.md` with both output forms and the broken-symlink behavior.

Validation: first run `./mvnw -Dtest=Seed4JCommandsFactoryTest test`, then
`./mvnw -Dtest=NioModuleSetProjectPathValidatorTest,ModuleSetPlanningApplicationServiceTest test`. At completion run, in
order, `npm run prettier:format`, `npm run prettier:check`, `git diff --check`, the requested combined focused Maven test
command, `./mvnw test`, and `habit-hooks`. Expected: every command exits `0`; `--plan` alone shows defaults as
`default (informational)`, execution never does, broken symlinks invalidate preflight without filesystem mutation, valid
directory symlinks remain accepted, and no enforced Habit finding remains.

### 7. Remove mutable caller-owned state from shared preflight formatting

Refactor `ApplyModuleSetPreflightSectionsRenderer` so execution-order, parameter, and commit-mode sections each build and
return one complete `String`. Update the detailed and compact renderers to append those returned values without passing
their `StringBuilder` into the shared formatter. Keep parameter-value and source conversion as private pure helpers; do
not add internal-method tests or change public APIs, domain types, options, streams, exit codes, or documentation.

Validation: first run the unchanged public checkpoint `./mvnw -Dtest=Seed4JCommandsFactoryTest test`. After the edit run,
in order, `npm run prettier:format`, `npm run prettier:check`, `git diff --check`,
`./mvnw -Dtest=HexagonalArchTest,Seed4JCommandsFactoryTest,NioModuleSetProjectPathValidatorTest test`, `./mvnw test`, and
`habit-hooks`. Expected: public output remains byte-for-byte protected by the Picocli journeys, all commands except an
unauthorized pre-existing Habit baseline situation exit `0`, and no caller passes mutable rendering state to the shared
formatter. Acceptance: `--plan` retains informational defaults, invalid preflight stays entirely on `stderr` and ends
with `No changes were applied.`, execution retains only its compact sections and progress, and reapplication, warnings,
and summaries remain unchanged.

## Progress

- [x] Read the implementation request, normative specification, repository instructions, and existing planning/CLI flow.
- [x] Create this self-contained ExecPlan before production implementation.
- [x] Complete immutable planning/preflight snapshot behavior.
- [x] Complete sequential execution and typed progress behavior.
- [x] Complete NIO, JGit, and individual Seed4J adapters.
- [x] Complete Picocli execution journeys and compatibility checks.
- [ ] Complete the Habit baseline decision (implementation, documentation, formatting, focused tests, and full tests are
      complete; refreshing the reviewed snooze baseline still requires explicit authorization).
- [x] Add this remediation milestone before changing regression tests or production code.
- [x] Write failing public rendering tests and separate detailed planning from compact execution preflight output.
- [x] Write failing broken-symlink tests and harden read-only NIO component discovery.
- [x] Reconcile command documentation with the two renderer contracts and symlink preflight rules.
- [ ] Run the complete remediation validation sequence and resolve every enforceable Habit finding (`habit-hooks` remains
      pending only on the reviewed snooze baseline).
- [x] Re-read the remediation request and update this plan with final evidence before handoff.
- [x] Add the mutable out-parameter remediation milestone before production edits.
- [x] Run the unchanged public Picocli checkpoint before the out-parameter refactor.
- [x] Return independently built section strings and update both renderer callers.
- [x] Run the requested out-parameter remediation validation sequence and analyze every Habit finding.
- [ ] Obtain explicit authorization to refresh the reviewed Habit snooze baseline; no snooze file was changed.
- [x] Re-read the request and update this plan with final out-parameter remediation evidence before handoff.

## Decisions

- Keep `ModuleSetPlan` as the sole approved execution input rather than introducing a second executable request. This
  preserves the normative snapshot boundary and prevents catalog/history drift between preflight and application.
- Represent operational checks as capability ports and structured domain facts, while concrete `Path`, NIO traversal,
  JGit discovery, and Seed4J request conversion remain secondary-adapter concerns. This enforces the repository's
  hexagonal boundary and keeps diagnostics renderable by Picocli rather than embedded in domain code.
- Extend the existing planning and CLI behavior suites instead of mirroring each new production type with a test class.
  A separate execution service suite and adapter suites are justified because those are stable caller-facing contracts.
- Keep effective-parameter selection authoritative in `ModuleSetPlan`; the primary renderer now asks the approved plan for
  resolved effective values instead of independently repeating the default-exclusion policy.
- Treat the Habit findings reopened in previously snoozed, intentionally explicit integration-test files as reviewed
  design decisions. Repository policy forbids refreshing that baseline without explicit user authorization, so the Habit
  gate remains pending rather than silently changing `.habit-hooks/snooze.json`.
- Keep the detailed renderer authoritative for `--plan` and invalid preflight, but introduce a dedicated compact execution
  renderer. This prevents presentation policy from changing the effective parameter map or the Seed4J core contract.
- Detect physical path components without following links, then follow valid links for directory and access checks. This
  distinguishes a broken symlink from an absent creatable path without rejecting a usable directory symlink.
- Make each shared preflight-section method own its local `StringBuilder` and return the completed section. This removes
  caller-owned mutable state from the formatter boundary while leaving detailed-versus-compact selection in the two
  policy renderers and preserving all observable text.

## Risks

- The Seed4J 2.2.0 individual request and history APIs are external contracts. Inspect their compiled signatures and test
  exact conversion before committing to adapter shapes.
- JGit version convergence can conflict with Seed4J transitive dependencies. Declare a compatible explicit version and
  inspect the dependency tree if focused compilation reports convergence or linkage problems.
- Capturing `System.out`/`System.err` can obscure stream assertions. CLI tests must assert each captured stream directly,
  especially the empty-stdout invalid-preflight guarantee.
- Real module integration can leave partial files by design. Tests must use isolated temporary projects and assert
  preservation/skip semantics without implementing cleanup or rollback in production.

## Documentation

`documentation/Commands.md` is the public command guide and must explain execution as the default, `--plan` as a fresh
read-only snapshot, default-enabled `--[no-]commit`, reapplication, dirty-Git warnings, literal statuses, partial effects,
and exit codes. `documentation/hexagonal-architecture.md` must replace the read-only flow with the planning/execution
boundary and name the capability ports and concrete secondary adapters. README navigation must continue to point readers
to the canonical command and architecture guides without duplicating their normative detail.

## Validation

Validation proceeds from focused suites to public CLI journeys and then repository-wide allowed gates. Record actual
exit status at milestone boundaries. The final expected command sequence is:

1. `npm run prettier:format`
2. focused Maven tests for planning, execution, secondary adapters, and CLI integration
3. `npm run prettier:check`
4. `./mvnw test`
5. `habit-hooks`

The user, not the agent, runs `./mvnw clean verify` and reports its exit code plus any relevant failure summary.

Observed milestone evidence:

- `./mvnw -Dtest=ModuleSetPlanningApplicationServiceTest test` exited `0` with 18 tests after red/green cycles for exact
  order, reapplication, effective parameters, project-path failure, commit mode, and conditional Git inspection.
- `./mvnw -Dtest=Seed4JCommandsFactoryTest test` exited `0` with 75 tests at the public-path checkpoint before the
  planning-port expansion; the next CLI checkpoint will include concrete NIO/JGit adapters and updated rendering.
- `./mvnw -Dtest=ModuleSetPlanningApplicationServiceTest,ModuleSetExecutionApplicationServiceTest test` exited `0` with
  20 tests. Execution visits the approved items once, passes the same effective-parameter object to each application,
  emits typed start/completion events, stops at the first runtime failure, and returns `FAILED`/`SKIPPED` slots for every
  remaining planned item.
- The focused planning, execution, NIO, JGit, and Seed4J-adapter command exited `0` with 30 tests. JGit
  `7.5.0.202512021534-r` matches the Seed4J 2.2.0 dependency; NIO left nonexistent targets absent; JGit covered
  `NO_WORKTREE`, `CLEAN`, and dirty tracked/staged/untracked states; the core adapter called only
  `apply(Seed4JModuleToApply)` with exact typed values.
- `./mvnw -Dtest=Seed4JCommandsFactoryTest test` exited `0` with 81 tests after the executable Picocli flow was wired.
  The suite covers a real in-set dependency with one commit per module, disabled commits without Git initialization,
  reapplication, dirty-worktree continuation, first-failure stop with a complete partial summary, opaque history-read
  failure before mutation, invalid-preflight stream isolation, and completion candidates for `--commit`, `--no-commit`,
  and `--plan`.
- `./mvnw -Dtest=ModuleSetPlanningApplicationServiceTest test` exited `0` with 20 tests after completing the exact-order
  invariant for missing, extra, and duplicated landscape results.
- After formatting and design review, the combined focused command exited `0` with 114 tests, and the planning plus public
  CLI checkpoint after the final behavior-preserving refactor exited `0` with 102 tests.
- `npm run prettier:format` and `npm run prettier:check` both exited `0`; all matched files use the repository format.
- `./mvnw test` exited `0` with 565 tests, no failures, errors, or skips.
- `habit-hooks` exited `1`. Its active findings are complexity/duplication heuristics for changed files, including large
  integration tests already present in `.habit-hooks/snooze.json`; changing those files intentionally reopened their
  baseline. The findings were reviewed: production orchestration methods are cohesive, import-block matches are not a
  missing abstraction, and the explicit Given/When/Then journeys should not be replaced by mechanical helpers. One real
  duplicate validation between the application service and planner was removed. Refreshing the reviewed baseline requires
  explicit authorization because repository instructions prohibit `habit-snooze` or snooze-file edits otherwise.
- The remediation public checkpoint `./mvnw -Dtest=Seed4JCommandsFactoryTest test` first failed on the missing
  informational default and then on the missing compact execution contract. After separating the renderers, the same
  82-test suite exited `0`: detailed plans include display defaults and always end with `No changes were applied.`, while
  execution includes only order, effective parameters with provenance/CLI spelling, and commit mode before progress.
- `./mvnw -Dtest=NioModuleSetProjectPathValidatorTest,ModuleSetPlanningApplicationServiceTest test` failed first for
  each broken-link regression and then exited `0` with 27 tests. A broken destination link maps to `NOT_DIRECTORY`, a
  broken intermediate link maps to `NOT_APPARENTLY_CREATABLE`, a usable directory link remains valid, and assertions
  confirm that no missing target or nested destination is created.
- The public CLI checkpoint exited `0` with 82 tests after the NIO cycles. One immediately preceding run observed the two
  history actions in reverse order; the affected test passed alone and the unchanged complete public suite passed on
  repetition, so no unrelated production or test semantics were changed.
- `documentation/Commands.md` now shows an informational default in the detailed `--plan` output, the compact execution
  preflight before progress, the exclusion of display defaults from core calls, and broken-link path rejection without
  mutation. The normative specification required no change because it already permits these stable explanatory fields.
- The design review classified duplicated section formatting as a design risk and consolidated it behind one
  primary-adapter formatter while preserving two policy-owning renderers. The combined public, NIO, and planning
  checkpoint exited `0` with 109 tests after this behavior-preserving refactor.
- `npm run prettier:format`, `npm run prettier:check`, and `git diff --check` each exited `0` in the requested order.
- The requested combined focused Maven command exited `0` with 135 tests, including architecture, planning, execution,
  NIO, JGit, Seed4J-core conversion, and public CLI journeys.
- `./mvnw test` exited `0` with 565 tests and no failures, errors, or skips.
- `habit-hooks` exited `1`. Every finding was reviewed: the only material duplication in this remediation was the shared
  preflight-section formatting and is resolved; the new NIO validator/tests, compact renderer, and shared formatter have
  no file-scoped finding. Remaining findings are the previously reviewed baseline for cohesive planning/execution/CLI
  orchestration signatures, explicit Given/When/Then integration journeys, intentionally broad public behavior suites,
  and coincidental imports or independently owned bounded-context representations. Mechanical extraction would violate
  repository test/design guidance without improving responsibility boundaries. `.habit-hooks/snooze.json` is unchanged;
  repository policy requires explicit authorization before refreshing that baseline.
- Before the mutable out-parameter refactor, `./mvnw -Dtest=Seed4JCommandsFactoryTest test` exited `0` with 82 tests and
  no failures, errors, or skips. This is the public rendering baseline for the behavior-preserving change.
- After the refactor, `npm run prettier:format`, `npm run prettier:check`, and `git diff --check` each exited `0` in the
  requested order. Prettier reported the changed files unchanged.
- `./mvnw -Dtest=HexagonalArchTest,Seed4JCommandsFactoryTest,NioModuleSetProjectPathValidatorTest test` exited `0` with
  107 tests and no failures, errors, or skips. The existing Picocli journeys preserve informational defaults in
  `--plan`, invalid-preflight stream isolation and final sentence, compact execution content, reapplication, warnings,
  progress, and summaries.
- `./mvnw test` exited `0` with 565 tests and no failures, errors, or skips.
- `habit-hooks` exited `1` only because the reviewed snooze baseline remains unauthorized. File-scoped runs exited `0`
  for `ApplyModuleSetPreflightSectionsRenderer` and `ApplyModuleSetExecutionPreflightRenderer`. The detailed
  `ApplyModuleSetPlanRenderer` still reports its pre-existing `oversized-function` finding for `render`: the method has
  one responsibility, ordering the complete detailed public output, and this change only replaced three mutating calls
  with three `append(String)` calls without adding lines or concerns. Extracting another helper would be mechanical and
  obscure that order. No new finding is related to the mutable-state remediation, and `.habit-hooks/snooze.json` remains
  unchanged.

Final remediation audit:

- Detailed valid `--plan` and invalid preflight both use `resolvedParameters()` and always end with
  `No changes were applied.`; invalid output remains isolated to `stderr` with exit `2`.
- Execution explicitly selects the compact renderer, which receives `effectiveResolvedParameters()` and prints only the
  approved preflight sections before typed progress; informational defaults remain absent from the effective map and core
  adapter calls.
- Physical path existence uses `LinkOption.NOFOLLOW_LINKS`; directory and permission validation still follows usable
  directory links. Broken destination and intermediate links are rejected without creating targets or destinations.
- Public documentation names the exact headings, sources, CLI options, default-exclusion rule, and symlink semantics.
- Shared execution-order, parameter, and commit-mode formatting now returns independently built `String` sections; both
  policy renderers concatenate those values and never pass caller-owned mutable state into the shared formatter.
- No tests or public documentation changed. Existing Picocli behavior tests protect all requested output and stream
  contracts; all allowed gates are green except the authorization-dependent Habit baseline.
