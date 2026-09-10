# Preserve release drafts while automating releases

## Purpose and success

Automate Seed4J CLI releases after a successful `main` build while preserving the existing Release Drafter presentation.
Conventional Commits remain the single SemVer policy for automatic and manual publication. A manual release means
"publish the current main": it uses the normal analyzed release type and falls back to patch only when that analysis
would produce no release. Success means npm, the Git tag, the published draft, CLI output, and the attached JAR all use
one version, with an idempotent recovery path for partial publication.

## Context and limits

The working branch already contains the first automation implementation: semantic-release policy tests, ephemeral
version preparation, `0.0.0-SNAPSHOT` source metadata, commitlint, and a recovery job. That first implementation removed
Release Drafter and delegated GitHub Release creation to `@semantic-release/github`; this revision must reverse only
that responsibility decision while retaining the useful automation base.

The latest release baseline is `v0.0.5`. There is no pre-1.0 version cap and no npm `next` channel. The trusted npm
publisher must remain `.github/workflows/release.yml` with no GitHub environment. Full publication must run only for the
official repository and current `main`; manual publication additionally requires a successful push build for that SHA.

## Decisions

- Conventional Commits are the only version policy. A local analyzer delegates to
  `@semantic-release/commit-analyzer`; manual mode changes only a null result to patch and never reduces minor or major.
- Semantic-release owns release detection, version calculation, the Git tag, and npm publication. Release Drafter owns
  release notes and GitHub Release publication. `@semantic-release/github` must not be configured.
- The manual interface exposes `operation=release|recover` plus an optional recovery version. Maintainers never choose
  a number or increment for a new release.
- Keep the existing draft and label categories. A normal manual release uses the same pipeline as automation; recovery
  accepts an exact existing version and completes only missing npm, GitHub Release, or JAR state.

## Milestones

### 1. Unify automatic and manual release analysis

Add a local semantic-release analyzer around the conventional analyzer, make the conventional analyzer a direct pinned
development dependency, and update the focused tests. Automatic neutral commits must return no release; manual neutral
commits must return patch; fix, feat, and breaking commits must retain patch, minor, and major in both modes.

Files: `scripts/release-analyzer.cjs`, `release.config.cjs`, `package.json`, `package-lock.json`, and
`test/release/release-policy.test.js`.

Validation: `npm run test:release` and `npm run prettier:check` must exit 0.

### 2. Restore drafts and add the safe manual/recovery interface

Restore the Release Drafter configuration and updater workflow. Extend `release.yml` with `operation=release|recover`
and optional `version`. Automatic and manual publication share semantic-release; manual publication validates current
main, a successful push build, and absence of an existing stable tag at HEAD. After npm succeeds, Release Drafter must
publish the refreshed draft with the computed version, followed by the JAR upload. Recovery must publish the draft only
when the GitHub Release is missing and must not overwrite published notes.

Add a small request guard with focused tests for dispatch operation, recovery version, current-main equality, green CI,
and already-tagged revisions. Keep the workflow path and trusted-publishing permissions stable.

Validation: focused release tests, YAML/config parsing, and non-publishing workflow guard probes must exit 0.

### 3. Reconcile documentation and perform the release preparation probe

Update the maintainer guide for automatic release, manual "publish current main", draft ownership, and recovery. Remove
the obsolete instruction to delete the existing draft. Verify source metadata remains `0.0.0-SNAPSHOT` and run the
existing npm and Maven gates plus an isolated stable-version preparation without publishing.

Validation: `npm run test:npm-package`, `./mvnw test`, `npm run prettier:check`, `git diff --check`, and `habit-hooks`
must exit 0. The isolated preparation must align Maven/npm versions, CLI output, npm contents, and the JAR asset.

## Progress

- [x] Created the working branch and initial automatic-release implementation.
- [x] Milestone 1: shared automatic/manual analyzer.
- [x] Milestone 2: restored drafts and safe dispatch/recovery workflow.
- [x] Milestone 3: documentation reconciliation and full agent-side validation.
- [x] Final handoff audit.

## Validation

Run checks from narrow to broad:

1. Focused Node tests for release analysis and request guards.
2. Static parsing of JavaScript, JSON, and both GitHub workflows.
3. Existing npm package tests and an isolated stable release-preparation probe.
4. `npm run prettier:check`, `./mvnw test`, `git diff --check`, and `habit-hooks`.

Do not run `./mvnw clean verify` automatically. Request that complete gate from the user after handoff, per repository
policy.

Baseline evidence from the first implementation: 4 release-policy tests, 10 npm-wrapper tests, and all 616 Maven tests
passed. An isolated `9.9.9` preparation aligned metadata, built and exercised the CLI, inspected the npm tarball, tested
the packed skill, and created the versioned JAR without publishing.

Milestone 1 evidence: the focused suite now passes 5 tests through the local analyzer. Automatic neutral commits return
no release; manual neutral commits return patch; and manual fix, feature, and breaking commits remain patch, minor, and
major. `@semantic-release/commit-analyzer` is a direct pinned dependency, and the loaded semantic-release configuration
contains the local analyzer, the preparation/output hook, and npm publishing without `@semantic-release/github`.

Milestone 2 evidence: the focused suite passes 9 tests, including dispatch, stable recovery version, current-main,
successful-build, and existing-tag guards. All release YAML parses, the real GitHub CLI build lookup returned one
successful push build for the inspected official main SHA, and a non-publishing probe confirmed that semantic-release's
success hook writes explicit `released=true` and `version=1.2.3` step outputs. The original Release Drafter
configuration and updater are restored unchanged; automatic/manual publication and recovery now publish through that
draft.

Milestone 3 and final evidence: `npm ci` completed with zero vulnerabilities; 9 release tests, 10 npm-wrapper tests, and
all 616 Maven tests passed. The isolated `9.9.9` preparation passed again, including aligned npm metadata, the packaged
CLI reporting `9.9.9`, an 80.6 MB npm tarball, packed-skill validation, and the 86 MB versioned JAR. Prettier, commitlint,
YAML/config parsing, `git diff --check`, and `habit-hooks` all exit 0. Documentation now explains the automatic button,
manual `operation=release` with an empty version, `operation=recover` with an exact version, and continued ownership by
Release Drafter.

The behavior-preserving design review made release state explicit instead of treating an empty version as status,
rejected a manually supplied version for normal release, and distinguished a tagged draft from an already published
GitHub Release during recovery. No further structural change was justified: the analyzer and request guard are pure,
the artifact preparation script remains cohesive, and repeated workflow setup belongs to independently recoverable jobs.

## Risks

- Git tag, npm publication, GitHub Release publication, and asset upload are not transactional. Recovery must validate
  the immutable tag and create only missing external state.
- Manual publication must not bypass CI or accidentally create a second release for the same revision. It must check the
  current official `main` SHA, its successful push build, and stable tags pointing at HEAD before semantic-release.
- Release Drafter and semantic-release must not both own GitHub Release creation. Removing
  `@semantic-release/github` is required, while the core semantic-release tag behavior remains intact.
- The standalone draft updater can overlap a release run. The publishing step must invoke Release Drafter again with the
  exact computed tag and current main commit so the published notes are refreshed at the publication boundary.

## Documentation

`documentation/development.md` remains the canonical maintainer guide. It must explain which component owns versioning,
npm, drafts, and GitHub Releases; show the GitHub Actions inputs for manual release and recovery; and state that the
existing draft is intentionally preserved.

## Rollout and recovery

Merge the automation with a neutral Conventional Commit so the change alone does not trigger an automatic release. Do
not delete the current Release Drafter draft. The first later releasable change publishes it; a maintainer may also use
`operation=release` to publish current main, with neutral accumulated changes becoming a patch.

If a release fails before the tag is pushed, rerun the normal release for the same successful SHA. If the tag exists,
dispatch `operation=recover` with the stable version without `v`. Recovery verifies the tag belongs to main, publishes
the npm package only if absent, publishes the draft only if the GitHub Release is absent, and uploads the JAR only if
absent. It never moves tags, replaces release notes, or republishes an existing npm version.
