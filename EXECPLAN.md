# Complete the experimental-channel trust and synchronization contracts

## Purpose and success

Deliver the remaining approved experimental-channel behavior across `seed4j-cli` and the separate personal snapshot publisher. Success means publisher workflows use resolvable immutable action pins, privileged deployment independently binds candidate contents to the trusted qualified identity and legal/provenance policy, all genuine failures produce safe actionable persistent reporting, one-way `main` to `experimental` synchronization is executable and stale-safe, and the CLI packages distribution metadata from the same build-owned authority as its Seed4J dependencies.

Safety boundary: This task is limited to authorized, defensive maintenance of these repositories. Do not execute upstream-produced artifacts in privileged paths or broaden credentials and workflow permissions.

## Context and limits

- Primary repository: `/home/renanfranca/projects/seed4j-cli`, immutable base `c3c05500acdb61853d39f77404f2254fbd3ab975`, authority/recovery-correction checkpoint `ce3da135ae4dfa5420357e37f00c558118eec2fb`.
- Publisher repository: `/home/renanfranca/projects/seed4j-main-snapshots`, immutable base `c9524cd9003f46284f0a9550b8cb2739d56ebf03`, accepted immutable checkpoint `ec4208dda65c69fd3e0fd4fc4d59e0d7777a3ed5`.
- Normative source: `.agent/specifications/experimental-seed4j-main-channel.md`; current defect evidence: `.agent/tmp/experimental-seed4j-main-channel.structural-review-qualification-final.md`.
- Preserve stable behavior, POM/main/tests-only publisher artifacts, sources/Javadocs prohibition, exact-SHA checks, protected-branch checks, `latest` isolation, credential boundaries, and current unrelated work.
- Do not create branches, stage, commit, push, publish, dispatch workflows, mutate issues or pull requests, install tooling, run `./mvnw clean verify` or Sonar, snooze Habit findings, or change external state.

## Milestones

### 1. Restore executable publisher workflows and local pin policy

Replace checkout, Node, and Java action references in `.github/workflows/tests.yml`, `.github/workflows/publish.yml`, and `test/workflow-policy.test.cjs` with the verified official immutable commits. Record primary-source verification in the correction report. Strengthen local policy assertions to require the approved action-to-commit mapping while keeping live network checks outside ordinary tests. Run the publisher workflow-policy tests and `npm test`; expected result is that every action reference is the approved 40-hex immutable commit and upload/download pins remain unchanged and syntactically pinned.

### 2. Re-establish the publisher trust boundary and actionable failure reporting

Add behavior-first tests in the existing publisher artifact, qualification, reporting, and workflow-policy suites. Update `scripts/deploy-candidate.cjs` and `.github/workflows/publish.yml` so the trusted qualification identity is passed directly to privileged verification, decoded there, and compared exactly with the manifest and qualified SHA before settings or deployment planning. Re-verify POM coordinate/version/nonofficial/legal/provenance fields and embedded main-JAR provenance/legal entries without executing upstream content. Update `scripts/qualify-candidate.cjs`, `scripts/report-result.cjs`, and workflow output wiring so pre-identity qualification failures and post-qualification build/deploy failures carry bounded sanitized single-line diagnostics; expected skips stay silent, pre-identity failures do not invent identity or retry state, and successful current publication closes the deterministic issue. Run focused adapter suites, full publisher `npm test`, and deterministic `npm run dry-run`.

### 3. Implement one-way current-SHA synchronization

Add a primary-repository synchronization policy script under `scripts/`, a workflow under `.github/workflows/`, and cohesive tests under `test/workflows/` or `test/release/`. The script must model dry-run-safe decisions for current successful `main`, disposable branch refresh from current `experimental`, exact source merge, conflict issue lifecycle, PR creation/refresh, explicit standard-tests dispatch for the current PR head, strict green/current SHA gating, post-merge experimental build dispatch, and branch cleanup only after merge. The workflow must use only narrowly scoped ephemeral `GITHUB_TOKEN` permissions and contain no reverse wholesale synchronization. Update `documentation/experimental-channel.md` and `documentation/workflows.md`. Run workflow and release tests plus deterministic dry-run scenarios; expected result is rejection or refresh for every stale, pending, red, or conflicting state and merge/cleanup only for current green state.

### 4. Establish one build-owned CLI distribution identity

Make each branch's top-level `pom.xml` values the explicit authority for Seed4J group, artifact, version, release channel, upstream SHA, repository, and unavailable modules. Filter `META-INF/seed4j-cli-distribution.properties` from those properties so runtime and tests dependencies and packaged immutable metadata cannot diverge. Keep this neutral/main-bound deliverable strictly stable: it must not declare the personal coordinate, Central snapshot repository, or an experimental Maven profile. Add Maven/build policy validation through existing Node workflow tests and Java metadata-reader tests, including a transformed experimental branch fixture and exact full-SHA rules. Update `renovate.json` so its stable manager matches the current top-level authority and its experimental manager becomes applicable only after the experimental branch replaces that same marker and values. Correct `documentation/hexagonal-architecture.md` so the secondary catalog reads the immutable domain port rather than an application cache. Preserve all stable defaults and defer the optional availability signature cleanup.

### 5. Reconcile documentation, evidence, and both repositories

Run publisher focused tests, full `npm test`, deterministic dry run, Prettier, and diff checks. Run primary workflow/release tests, affected Java tests, `./mvnw test`, Prettier, Habit Hooks, and diff checks. Audit the final diffs against every normative positive and prohibition. Write `.agent/tmp/experimental-seed4j-main-channel.structural-corrections.md` with per-finding design, exact commands/results, action-pin verification, residual external gates, and both repository statuses. Leave both repositories uncommitted and release only the Implementer lease.

### 6. Bind and durably finalize exact synchronization proposals

Add adapter-level behavior scenarios in `test/workflows/main-to-experimental-sync.test.js` before changing the implementation. Make `scripts/main-to-experimental-sync.cjs` the executable authority for review and post-merge decisions: compare the recorded PR-body head with the live PR head and exact completed build, and validate independently observed proposal parent topology against recorded source and target. Update `.github/workflows/synchronize-experimental.yml` so an open auto-merge timeout schedules a durable workflow re-entry and a later invocation observes the eventual merge, explicitly dispatches the current experimental build, and only then deletes the disposable branch. Run the focused synchronization suite, all workflow tests, and `npm run sync:dry-run`; stale, changed-head, invalid-topology, pending, red, and delayed-open states must never claim lifecycle completion.

### 7. Complete publisher infrastructure-failure reporting and inert diagnostics

Add reporting/workflow behavior tests before implementation. Pass the qualifier job result into the report adapter and map missing candidate outputs plus a failed/cancelled/timed-out qualifier job to a bounded nonretryable qualification failure with no invented identity; preserve silent expected skips. Rename the public committed/runbook key to `centralTokenExpiresAt` consistently while retaining any internal policy translation, and enforce documentation/schema agreement through an executable adapter test. Neutralize untrusted GitHub mentions and Markdown-link syntax in diagnostic text while preserving the one explicit trusted `@renanfranca` issue mention. Run focused reporting, operations, config, and workflow tests followed by full publisher `npm test` and `npm run dry-run`.

### 8. Final correction evidence and handoff

Reconcile lifecycle/configuration documentation and this plan, run primary workflow/release/dry-run tests, `./mvnw test`, Prettier, `habit-hooks --no-snooze`, and diff checks; run publisher full/focused tests, dry-run, Prettier, and diff checks. Write `.agent/tmp/experimental-seed4j-main-channel.structural-corrections-final.md` with red/green evidence and exact repository states. Leave both repositories uncommitted and unstaged, release only the Implementer lease, and keep ledger phase `implementing`.

### 9. Authenticate and exhaust durable synchronization recovery

Add executable workflow-adapter scenarios in `test/workflows/main-to-experimental-sync.test.js` before production edits. Move completion-record parsing and authorization plus bounded historical PR selection into `scripts/main-to-experimental-sync.cjs`. Completion evidence must be authored by the repository automation identity and strictly bind PR number, proposal head, merge commit, and resulting experimental SHA. OPEN recovery must compare independently fetched current `main` and `experimental` with the recorded source and target, while MERGED recovery must remain valid after either protected head advances when the exact merge stays reachable. Replace latest-only scheduled shell selection with one deterministic bounded drain of every outstanding trusted PR, prioritizing older merged work without deleting a reused branch that now backs a newer proposal. Update `.github/workflows/synchronize-experimental.yml`, synchronization documentation, and the final recovery report. Run focused red/green scenarios, full workflow/release tests, sync dry run, YAML parsing, Maven tests, Prettier, Habit Hooks, and diff checks; the publisher repository must remain clean at its accepted checkpoint.

### 10. Make recovery transport and side effects durable at runtime

Add behavior-first child-process scenarios for X1-X6 before changing production paths. Make trusted bot-issued exact-head
`workflow_dispatch` builds eligible only for experimental release while stable remains push-only. Replace bulk JSON
environment transport with bounded file/pipe input, and use the required `synchronization-pending` label as a server-side
pending index so completed history cannot crowd out older work and explicit recovery resolves its exact PR directly.
Move candidate orchestration into an executable Node adapter that isolates candidate failures, reuses an existing
queued/in-progress/successful exact-head standard build, coalesces refresh once, leaves historical conflict-issue closure
to current preparation, and records completion only after all required effects. Delete the disposable branch through an
atomic expected-SHA force-with-lease refspec and treat an advanced branch as a safe candidate-local keep. Update release
and synchronization workflows, scripts, tests, `README.md`, channel/workflow documentation, and the final runtime report.
Run focused red/green scenarios, all requested local gates, and exact repository audits without external mutation.

### 11. Qualify release provenance and repair proposal builds

Add behavior-first process, workflow, and real local-Git scenarios for R1-R4. Split release handling into a trusted,
permission-minimal qualification job sourced from default `main` and a privileged publish job that consumes only an
accepted immutable channel/SHA, checks out that SHA, and rechecks only the selected protected branch before running
target code. Keep stable release and recovery independent of an absent `experimental` branch. Make scheduled recovery's
global query a lightweight number/state index, fetch each candidate's full evidence through a genuinely bounded
candidate-local process, and continue later completion plus one coalesced refresh after hostile candidate output. Make
exact proposal-head build assurance the first repairable post-publication effect, and let OPEN scheduled recovery reuse
any queued, running, or completed exact-head run—including red—or dispatch exactly once when absent without merging.
After green behavior, apply refactor-design to remove obsolete recovery commands and implementation-detail tests when
the real workflow/process paths fully protect their contracts. Update release/synchronization workflows, adapters,
tests, README and workflow/channel documentation, then run the complete primary validation set and write the final
qualification correction report.

### 12. Remove implicit build authority and recover partially published proposals

Add public workflow-policy tests before changing the standard build so every unspecified `GITHUB_TOKEN` capability is
denied, the only required capability is `contents: read`, and checkout never persists its credential into later
PR-controlled steps. Recheck the automation-identity predicates that authorize release and synchronization finalization
after removing that authority path. Add a child-process recovery scenario that first models `gh pr create --label`
creating the canonical PR but returning nonzero when its label update fails. Keep the ordinary historical drain strictly
label-indexed, and add one scheduled-only, bounded exact lookup for an OPEN PR with the fixed source and target branches.
Treat that unlabeled PR as untrusted until full candidate evidence, live and fetched proposal heads, both current
protected heads, and exact two-parent topology agree. Only then add the pending label and reuse or dispatch exactly one
proposal build; never enable merge from this repair path. Cover absent, malformed, duplicate, and retry/reuse states,
then reconcile synchronization documentation and the final correction evidence.

## Progress

- [x] Read the approved specification and structural review; verify both repositories are clean at the assigned checkpoints.
- [x] Create this self-contained implementation plan.
- [x] Complete publisher action-pin policy (official tags resolved with `git ls-remote`; focused policy suite green).
- [x] Complete privileged identity/legal/provenance binding (adversarial self-consistent bundle tests reject before settings or Maven invocation).
- [x] Complete bounded build/deploy and pre-identity failure reporting (40-test publisher suite and deterministic dry run green).
- [x] Complete one-way synchronization behavior, workflow, tests, and documentation (including bounded auto-merge confirmation found by the design audit).
- [x] Correct the authoritative CLI distribution identity to the normative branch-owned model and reconcile its tests and documentation.
- [x] Repeat final validation, correction report, repository audit, and handoff after the branch-isolation correction.
- [x] Correct D6, D7, and R2 through executable synchronization adapter behavior (focused red/green scenarios cover preparation, live-head/topology review, and delayed finalization).
- [x] Correct D8, D9, and R3 through publisher workflow/reporting behavior (focused red/green scenarios cover missing qualifier output, public schema documentation, and inert diagnostics).
- [x] Complete final correction validation, report, and repository audit; release the Implementer lease at handoff without changing phase.
- [x] Correct N1-N3 and the remaining R2 recovery slice through executable adapter behavior (strict trusted completion,
      current-head OPEN recovery, and deterministic bounded multi-PR draining are green in the workflow suite).
- [x] Complete primary-only recovery validation, evidence report, publisher-clean audit, and lease release.
- [x] Correct X1-X6 and the reopened D5/D7/R2 runtime slice through executable adapter behavior (trusted release
      provenance, bounded recovery transport, isolated/idempotent orchestration, pending-state indexing, current-only
      issue closure, and atomic leased cleanup all have red/green public-boundary scenarios).
- [x] Complete runtime validation, documentation, evidence report, publisher-clean audit, and lease release handoff.
- [x] Correct R1-R4 through trusted release qualification, channel-local Git resolution, bounded per-candidate recovery,
      and repairable exact proposal-head builds.
- [x] Complete post-green design consolidation, validation, qualification report, publisher audit, and lease release.
- [x] Remove implicit standard-build write authority and persisted checkout credentials through a public red/green policy cycle.
- [x] Recover a valid unlabeled partial proposal through one bounded exact scheduled lookup and a public red/green process cycle.
- [x] Reconcile documentation/evidence, run the required primary gates, audit both repositories, and release the Implementer lease.

## Decisions

- Keep the optional `Seed4JModuleAvailability` typed-signature cleanup deferred. It is not required to establish the build-owned identity and would add domain churn without resolving a current defect.
- Treat the trusted qualification output as the authorization source for deployment. Artifact manifests remain integrity evidence but cannot self-authorize identity, legal metadata, or provenance after the untrusted build boundary.
- Keep synchronization decisions in a deterministic local script invoked by a thin workflow adapter. This permits exhaustive stale/conflict/dry-run tests without GitHub writes and keeps the workflow declarative.
- Keep distribution identity branch-owned rather than profile-owned. The current main-bound POM exposes only stable top-level values; the later experimental branch replaces those same values and adds its snapshot repository, so ordinary Maven commands always build the checked-out branch's own identity.
- Use an explicit durable workflow re-entry after an auto-merge polling timeout. A bounded runner wait remains useful, but lifecycle completion is authorized only by a later independently observed merged PR and current experimental topology.
- Treat issue diagnostics as untrusted display data. The reporting template owns the sole active `@renanfranca` mention; captured diagnostics neutralize every mention and Markdown link before issue writes.
- Represent synchronization completion as a strict automation-authored state record bound to the PR, proposal head, merge commit, and dispatched experimental head. Plain marker text or mismatched state remains non-authoritative.
- Make one scheduled recovery enumerate a bounded history and process every outstanding trusted PR deterministically. This avoids relying on GitHub's replaceable single pending workflow slot for eventual progress.
- Trust `workflow_dispatch` release provenance only for an exact current `experimental` build attributed by GitHub to
  `github-actions[bot]`; `main` remains push-only and all later exact-head/tag checks remain mandatory.
- Use the repository label `synchronization-pending` as the durable server-side finalization index. Establish it before
  auto-merge and remove it only after canonical trusted completion; operators must create the documented label before
  enabling synchronization.
- Keep historical finalization away from the global conflict issue. Only a current authoritative preparation result may
  close it, and duplicate matching issues fail closed.
- Keep GitHub recovery effects in one executable Node orchestrator. The workflow supplies only scalar configuration;
  the orchestrator owns bounded candidate-local streaming, candidate ordering and isolation, exact-build reuse, refresh coalescing,
  atomic cleanup, completion recording, and pending-label reconciliation.
- Keep bounded-file parsing for workflow-owned files and terminate direct GitHub response streams at 2 MiB inside each
  recovery candidate. The post-green refactor removed obsolete selection, finalization, and cleanup commands so the live
  recovery process is the only orchestration policy surface.
- Qualify workflow-run releases using trusted default-branch code before any privileged job or triggering revision
  checkout. The privileged job receives only the immutable accepted channel/SHA and independently rechecks that one
  protected head, preserving stable operation before `experimental` exists.
- Treat proposal-head build assurance as a recoverable synchronization state. PR publication establishes durable
  pending state first; scheduled OPEN recovery repairs only a missing exact-head run and preserves any existing
  queued, running, successful, or failed result for ordinary review policy.
- Give the standard build exactly `contents: read` and do not pass `GITHUB_TOKEN` to Sonar. Checkout requires contents
  read access, while the documented Maven analysis authenticates to SonarQube Cloud with `SONAR_TOKEN`; declaring one
  read permission makes every unspecified GitHub permission none.
- Preserve the label-filtered historical recovery index as the normal trust boundary. A scheduled run may additionally
  request at most one OPEN PR by the exact `main-to-experimental-sync` head and `experimental` base, but that candidate
  earns its pending label only after the same canonical identity, current-head, and topology proof as an indexed PR.

## Risks

- Publisher build artifacts are attacker-controlled relative to deployment credentials. Verification must finish before settings creation and must not load or execute candidate code.
- Failure diagnostics cross workflow-job boundaries and enter issue bodies. They must be length-bounded, single-line, control-character-free, and sourced from trusted adapter summaries rather than arbitrary logs.
- Synchronization evidence becomes stale whenever either protected branch or the PR head moves. Every merge-affecting transition must compare current source, target, and head SHAs and refresh instead of trusting earlier checks.
- GitHub auto-merge is asynchronous. A successful job that leaves an open auto-merge PR without durable re-entry can permanently skip the mandatory post-merge build and cleanup.
- Qualifier infrastructure can fail before repository code emits outputs. Reporting must use the independently available job result and must not interpret missing output as an expected skip.
- Public PR comments are attacker-controlled unless both authorship and exact state are validated; completion cannot short-circuit before that policy check.
- The fixed disposable branch can be reused by a newer PR while an older merge still needs finalization. Cleanup must delete it only when the remote head still equals the proposal being finalized.
- Recovery effects cross independent GitHub/Git boundaries. A partial failure must not block later candidates or lose a
  coalesced refresh, and retries must reuse an exact-head build request instead of repeatedly dispatching it.
- A check followed by unconditional remote deletion is racy. The expected proposal SHA must be carried in the Git push
  lease so a concurrent update is preserved atomically.
- Bulk PR/comment JSON can exceed the operating system's per-environment-string limit before Node starts. Transport it
  through a bounded file or pipe and validate size and shape inside the executable adapter.
- A privileged `workflow_run` job cannot trust a verifier loaded from the triggering revision. Admission and immutable
  channel/SHA qualification must finish in default-branch code before write/OIDC authority or target checkout exists.
- A global pending-index query containing comments lets one hostile PR block every candidate. Keep the index lightweight
  and terminate each exact evidence capture at its byte limit inside that candidate's failure boundary.
- Repository-default workflow permissions and checkout-persisted credentials let PR-controlled build steps act with
  more authority than their source deserves. The standard build must explicitly deny every write scope and make the
  checkout credential unavailable to all later steps.
- `gh pr create --label` can create the PR before its follow-up label mutation fails. Without a separate exact bounded
  discovery, that durable but unlabeled proposal is invisible to the label-indexed recovery drain; without full proof,
  admitting it would instead bypass the index's trust boundary.
- Filtering distribution metadata can accidentally expose Maven or environment overrides at runtime. Only build-time Maven properties may generate the packaged resource; runtime property sources must remain irrelevant.

## Documentation

- Update publisher `README.md` for trusted deploy binding, failure diagnostics, and operational verification.
- Update primary `documentation/experimental-channel.md` and `documentation/workflows.md` for exact synchronization and recovery behavior.
- Correct `documentation/hexagonal-architecture.md` to show `Seed4JModuleSetCatalog` consuming the immutable distribution metadata reader port directly.
- Capture exact evidence and residual external gates in the ignored structural-corrections report.

## Rollout and recovery

No external rollout occurs in this task. Workflow behavior is validated locally and remains uncommitted. If later validation finds a regression, recovery is to discard only the additive local correction diff from the owning repository; no published artifact, branch, issue, PR, workflow run, tag, or credential state is changed here.

## Validation

- Publisher focused and full: `npm test`; `npm run dry-run`; `npm run prettier:check`; `git diff --check`.
- Primary workflow/release: `npm run test:workflows`; `npm run test:release`; focused Node tests for synchronization and metadata policy.
- Primary Java/build: focused metadata-reader tests as needed; `./mvnw test`; `npm run prettier:check`; `habit-hooks`; `git diff --check`.
- Acceptance: adversarial bundle identity/legal/provenance changes are rejected before side effects; real failures report safe current diagnostics; pre-identity failures report without fabricated provenance; synchronization never merges stale/red/conflicting state or reverses flow; packaged metadata and both Seed4J dependencies derive from one Maven authority; both repositories end with only authorized uncommitted changes.
