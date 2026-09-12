# Complete the experimental-channel trust and synchronization contracts

## Purpose and success

Deliver the remaining approved experimental-channel behavior across `seed4j-cli` and the separate personal snapshot publisher. Success means publisher workflows use resolvable immutable action pins, privileged deployment independently binds candidate contents to the trusted qualified identity and legal/provenance policy, all genuine failures produce safe actionable persistent reporting, one-way `main` to `experimental` synchronization is executable and stale-safe, and the CLI packages distribution metadata from the same build-owned authority as its Seed4J dependencies.

Safety boundary: This task is limited to authorized, defensive maintenance of these repositories. Do not execute upstream-produced artifacts in privileged paths or broaden credentials and workflow permissions.

## Context and limits

- Primary repository: `/home/renanfranca/projects/seed4j-cli`, immutable base `c3c05500acdb61853d39f77404f2254fbd3ab975`, implementation checkpoint `014ebb05bb0fba57b8a802cbffff2b7b17577e9f`.
- Publisher repository: `/home/renanfranca/projects/seed4j-main-snapshots`, immutable base `c9524cd9003f46284f0a9550b8cb2739d56ebf03`, implementation checkpoint `618ffdd653518dea9b0d6f67851685c8ca89b07f`.
- Normative source: `.agent/specifications/experimental-seed4j-main-channel.md`; defect evidence: `.agent/tmp/experimental-seed4j-main-channel.structural-review.md`.
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

## Progress

- [x] Read the approved specification and structural review; verify both repositories are clean at the assigned checkpoints.
- [x] Create this self-contained implementation plan.
- [x] Complete publisher action-pin policy (official tags resolved with `git ls-remote`; focused policy suite green).
- [x] Complete privileged identity/legal/provenance binding (adversarial self-consistent bundle tests reject before settings or Maven invocation).
- [x] Complete bounded build/deploy and pre-identity failure reporting (40-test publisher suite and deterministic dry run green).
- [x] Complete one-way synchronization behavior, workflow, tests, and documentation (including bounded auto-merge confirmation found by the design audit).
- [x] Correct the authoritative CLI distribution identity to the normative branch-owned model and reconcile its tests and documentation.
- [x] Repeat final validation, correction report, repository audit, and handoff after the branch-isolation correction.

## Decisions

- Keep the optional `Seed4JModuleAvailability` typed-signature cleanup deferred. It is not required to establish the build-owned identity and would add domain churn without resolving a current defect.
- Treat the trusted qualification output as the authorization source for deployment. Artifact manifests remain integrity evidence but cannot self-authorize identity, legal metadata, or provenance after the untrusted build boundary.
- Keep synchronization decisions in a deterministic local script invoked by a thin workflow adapter. This permits exhaustive stale/conflict/dry-run tests without GitHub writes and keeps the workflow declarative.
- Keep distribution identity branch-owned rather than profile-owned. The current main-bound POM exposes only stable top-level values; the later experimental branch replaces those same values and adds its snapshot repository, so ordinary Maven commands always build the checked-out branch's own identity.

## Risks

- Publisher build artifacts are attacker-controlled relative to deployment credentials. Verification must finish before settings creation and must not load or execute candidate code.
- Failure diagnostics cross workflow-job boundaries and enter issue bodies. They must be length-bounded, single-line, control-character-free, and sourced from trusted adapter summaries rather than arbitrary logs.
- Synchronization evidence becomes stale whenever either protected branch or the PR head moves. Every merge-affecting transition must compare current source, target, and head SHAs and refresh instead of trusting earlier checks.
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
