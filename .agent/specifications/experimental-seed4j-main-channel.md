# Experimental Seed4J main channel

Status: approved, decision-complete implementation specification derived from the Experimental Seed4J Main Channel
requirements.

This specification defines an explicitly experimental Seed4J CLI distribution that follows the official Seed4J
`main` branch without credentials, publication rights, or operational cooperation from the Seed4J repository owner.
The terms **MUST**, **MUST NOT**, **SHOULD**, and **MAY** are normative.

## Purpose and observable success

Users who deliberately opt into the experimental channel need a recent Seed4J `main` revision instead of the latest
stable Seed4J release. That channel succeeds when:

- a build of the current official Seed4J `main` is published under a clearly unofficial personal Maven coordinate;
- `seed4j-cli@experimental` consumes that build, exposes its exact upstream provenance, and remains isolated from the
  stable CLI distribution;
- updates flow from stable CLI `main` into the experimental branch and never in the opposite direction as an automatic
  wholesale merge;
- an incompatible upstream change stops the affected update instead of silently shipping a broken CLI;
- publisher credentials cannot be reached by code checked out from the upstream Seed4J repository; and
- `seed4j-extension` cannot create a project whose build later depends on an expiring personal snapshot.

The channel remains experimental until Seed4J provides an official per-`main`, canary, or equivalent Maven publication.
It is not an official Seed4J distribution and MUST NOT be described as one.

## Distribution channel contract

| Contract                  | Stable channel                       | Experimental channel                                                          |
| ------------------------- | ------------------------------------ | ----------------------------------------------------------------------------- |
| Git branch                | `main`                               | `experimental`                                                                |
| Seed4J dependency         | Official `com.seed4j:seed4j` release | Personal `io.github.renanfranca:seed4j-main-snapshot` snapshot                |
| Maven repository          | Maven Central release repositories   | `https://central.sonatype.com/repository/maven-snapshots/`                    |
| npm installation          | `npm install -g seed4j-cli@latest`   | `npm install -g seed4j-cli@experimental`                                      |
| npm version               | Stable semantic version              | `<next-stable>-experimental.<n>`                                              |
| npm dist-tag              | `latest`                             | `experimental`                                                                |
| `seed4j-extension` module | Available                            | Unavailable                                                                   |
| Ordinary command output   | Existing behavior                    | Existing behavior unless channel disclosure or the unavailable module applies |

`main` MUST continue to depend on the official stable coordinate for both its runtime and tests-classifier dependencies.
It MUST NOT declare the personal snapshot repository. Existing stable npm publication, GitHub Release, attached JAR,
version output, command help, module catalog, completion, and exit behavior MUST remain unchanged.

The experimental branch MUST use the personal coordinate for both its runtime and tests-classifier dependencies. It
MUST use the snapshot repository only for that coordinate and MUST NOT replace, publish, or claim the official
`com.seed4j:seed4j` coordinate.

### Release-channel metadata

The CLI MUST model release channel and module availability as explicit concepts rather than raw boolean switches.
`main` MUST contain the neutral capability and configure it as stable with every Seed4J module available. The
`experimental` branch MUST configure the same capability as experimental with `seed4j-extension` unavailable.

Release channel, upstream SHA, dependency coordinate, and module availability are immutable distribution metadata. They
MUST come from the packaged build and MUST NOT be replaceable through `~/.config/seed4j-cli/config.yml`, environment
properties, command options, runtime extensions, or another external override.

Domain code MAY decide whether a module slug is available, but it MUST NOT contain Picocli option spelling, formatted
help text, npm commands, or user-facing diagnostics. Primary adapters own help, version rendering, completion, and error
messages. Secondary adapters that expose Seed4J resources to application or domain code MUST apply the same availability
policy so unavailable modules cannot leak through a separate catalog path.

### Experimental disclosure

The root help of the experimental CLI MUST include a concise warning that:

- the distribution is experimental;
- it tracks Seed4J `main` through an unofficial snapshot that can expire; and
- stable use is restored with `npm install -g seed4j-cli@latest`.

The warning MUST NOT be repeated for every ordinary command invocation. Normal command `stdout` and `stderr` remain
unchanged unless a command renders root help, renders version information, or requests an unavailable module.

`seed4j --version` on the experimental channel MUST render all existing applicable runtime information and additionally
make these facts unambiguous:

```text
Seed4J CLI v<cli-prerelease-version>
Release channel: experimental
Seed4J version: <personal-snapshot-version>
Seed4J upstream commit: <40-character-sha>
```

The exact ordering may preserve the existing runtime-mode and extension-distribution fields, but each required line MUST
appear exactly once. The SHA MUST identify the official `seed4j/seed4j` commit used to build the Maven artifact; it MUST
NOT identify an overlay, publisher, or CLI commit.

### Unavailable extension-generator module

Only the Seed4J module with slug `seed4j-extension` is unavailable in the experimental channel. The local
`seed4j extension ...` command group for installing, enabling, disabling, inspecting, or otherwise managing runtime
extensions MUST remain available.

On the experimental channel, `seed4j-extension` MUST be absent from:

- module listings;
- the visible `seed4j apply --help` command tree;
- module catalogs exposed to `apply-set`;
- shell-completion candidates; and
- any other discovery interface that represents available Seed4J modules.

An explicit `seed4j apply seed4j-extension` invocation MUST write this diagnostic to `stderr`, return exit code `2`, and
perform no project, history, filesystem, Git, event, or runtime-extension mutation:

```text
ERROR: Module 'seed4j-extension' is unavailable in the experimental channel because Central Portal snapshots expire and generated extensions would not remain rebuildable. Install seed4j-cli@latest to generate a stable Seed4J extension. No changes were applied.
```

An explicit help request for the hidden module MAY explain the restriction and return exit code `0`, but the module MUST
remain absent from parent help and completion.

An `apply-set` request containing `seed4j-extension` MUST fail with exit code `2` during selection preflight, before any
project path, project history, filesystem, Git, dependency, property, or execution inspection and before any mutation.
The diagnostic MUST identify the unavailable slug, explain the retention reason, recommend `seed4j-cli@latest`, and state
that no changes were applied. The CLI MUST NOT treat the slug as generically unknown, silently omit it, apply the
remaining requested modules, or aggregate later planning errors after finding it.

The stable channel MUST continue to expose and apply `seed4j-extension` normally.

## Personal Seed4J snapshot publication

### Repository, namespace, and provenance

Publication belongs in the separate public GitHub repository `renanfranca/seed4j-main-snapshots`. It MUST NOT be
implemented inside `seed4j/seed4j-cli`, inside a fork that could be mistaken for the official repository, or with an
official Seed4J credential.

The publisher MUST use the Central Portal namespace `io.github.renanfranca`, verified through the GitHub account
`renanfranca`, and publish this GAV:

```text
io.github.renanfranca:seed4j-main-snapshot:<snapshot-version>
```

The published POM and retained legal metadata MUST:

- identify the artifact as an unofficial rebuild of Seed4J `main`;
- retain the applicable upstream license and notice obligations;
- link to the official upstream source repository and the exact source SHA;
- identify the personal publisher rather than an official Seed4J publisher; and
- avoid metadata that implies ownership of the `com.seed4j` namespace or endorsement by Seed4J maintainers.

### Deterministic version identity

The snapshot base version MUST have this format:

```text
<upstream-base>-main.<yyyyMMdd>.<HHmmss>.<12-character-sha>-SNAPSHOT
```

`upstream-base` is the official upstream POM version with one final `-SNAPSHOT` suffix removed when present. The date
and time come from the upstream commit timestamp in UTC, not workflow start time. The SHA component is the first 12
lowercase hexadecimal characters of the full upstream commit SHA.

For upstream commit `4eebd07bce14c9a6ac70bace157fcc616133e950`, committed at `2026-09-07T05:58:00Z` with POM
version `2.2.1-SNAPSHOT`, the resulting version is:

```text
2.2.1-main.20260907.055800.4eebd07bce14-SNAPSHOT
```

The same SHA MUST always derive the same snapshot base version. A retry or retention refresh of that SHA MUST reuse the
base version; Central may create a newer timestamped snapshot build beneath it. Missing, malformed, or ambiguous
upstream version, commit timestamp, or SHA data MUST stop publication as a publisher failure.

### Candidate qualification and artifact set

For a normal candidate, the publisher MUST resolve `main` from the official `seed4j/seed4j` repository and MUST bind all
later checks to that exact full SHA. It MUST require the official repository's aggregated GitHub Actions result for that
SHA to be successful. A missing, queued, pending, cancelled, or unsuccessful upstream result is an expected skip: no
artifact is published and no persistent publisher-failure issue is opened.

After the upstream check succeeds, an unprivileged job MUST:

1. create a disposable checkout of the exact upstream SHA;
2. apply only the coordinate, version, repository, nonofficial metadata, license/notice, and provenance overlay required
   for personal publication;
3. run `npm ci`, the applicable upstream lint checks, and `./mvnw clean verify`;
4. collect only the generated POM, main JAR, and tests-classifier JAR; and
5. produce a candidate manifest containing the full upstream SHA, derived version, exact expected filenames, sizes, and
   SHA-256 digests.

Sources and Javadoc JARs MUST NOT be published in this channel. The official source at the recorded SHA and the public
publisher overlay are the source-provenance record.

The build MUST use the personal GAV consistently enough that the published POM and both JARs can be resolved together
from a clean Maven environment. The overlay MUST NOT modify Seed4J behavior merely to make CLI compatibility tests pass.
An upstream incompatibility belongs in the experimental CLI update flow.

### Credential boundary

Upstream Seed4J code is untrusted relative to Central credentials. The job that checks out, patches, builds, tests, or
packages upstream code MUST NOT receive Central credentials, environment access, repository write access, an npm token,
or another publication secret.

A separate deployment job MUST:

- run in a clean trusted checkout of the personal publisher repository;
- download only the candidate artifacts and manifest from the unprivileged job;
- recompute and verify every SHA-256 digest;
- reject unexpected files, classifiers, types, coordinates, versions, or manifest fields;
- use pinned trusted Maven deployment tooling to upload the POM, main JAR, and tests-classifier JAR; and
- never execute the upstream Maven lifecycle, scripts, binaries, JARs, or generated executable content.

Every third-party GitHub Action in the publisher MUST be pinned to a full commit SHA. Workflow and job permissions MUST
be empty by default and expanded only to the minimum required by an individual job.

The Central Portal credential MUST be a dedicated user token with a 180-day lifetime, stored only as
`CENTRAL_USERNAME` and `CENTRAL_PASSWORD` in a protected GitHub Environment available to deployment from the publisher
repository's `main`. Pull requests, forks, `pull_request_target`, arbitrary refs, and build jobs MUST NOT access that
environment. The publisher MUST create a rotation-warning issue assigned to `renanfranca` 30 days before recorded token
expiry and close it after the expiry record is updated for a rotated token.

### Triggers, retries, and retention

The publisher supports scheduled execution and `workflow_dispatch`. A manual invocation MUST accept only these logical
operations:

| Operation           | Candidate                                                                         |
| ------------------- | --------------------------------------------------------------------------------- |
| `head`              | Current official upstream `main` HEAD resolved by the workflow                    |
| `retry-last-failed` | Most recent recorded publisher candidate that failed after upstream qualification |

There MUST be no free-form SHA, ref, version, repository, coordinate, or artifact input. `retry-last-failed` MUST derive
the candidate from trusted publisher state, confirm that the SHA is still reachable from the official upstream
repository, and re-derive its version before building. It MUST NOT reinterpret an expected upstream-CI skip as a failed
candidate.

A normal run for a SHA successfully published less than 60 days earlier MUST skip deployment. When the same upstream
HEAD remains current for 60 days, the next eligible scheduled run MUST republish the same snapshot base version to keep
the dependency available ahead of the approximate Central snapshot-retention boundary. If Central no longer exposes a
previously published build, the publisher MUST also republish it at the next eligible run.

The first publication is a manually observed pilot. After the pilot succeeds, the default schedule is every Monday at
`06:17 UTC`. Daily publication at `06:17 UTC` MAY be enabled only after the maintainer inspects the Central Usage Center
and demonstrates that projected monthly release count and stored bytes are each no more than 80% of the account limits.
The workflow MUST NOT enable daily mode automatically.

If the daily projection exceeds either margin, publication remains weekly while the maintainer requests a community OSS
allowance or exemption. The automation MUST NOT purchase, select, or authorize a paid plan. A changed Central allowance
requires a new recorded calculation before daily mode is enabled.

### Failure reporting

An overlay, local build, manifest, artifact, credential, deployment, rate-limit, or Central availability failure after a
candidate has qualified MUST create or update one open issue in `renanfranca/seed4j-main-snapshots`. The issue MUST:

- carry a dedicated publisher-failure label;
- be assigned to `renanfranca` and mention `@renanfranca` in its body;
- identify the failing stage, workflow run, full upstream SHA, derived version, and latest concise diagnostic;
- accumulate later failures without creating duplicate open issues; and
- close automatically after the failed candidate or a newer current candidate publishes successfully.

Expected skips, including unchanged recently published HEAD and upstream CI that is not successful, MUST remain visible
in the workflow summary but MUST NOT open the failure issue.

## CLI branch, update, and release flow

### Protected branches

Both `main` and `experimental` MUST require pull requests and the `tests` status check. The status check MUST be strict,
so its commit is current with the target branch before merge. Force pushes and branch deletion MUST be disabled.

No mandatory human approval count is added because automated, checked dependency and synchronization PRs need to merge.
Administrator bypass remains enabled (`enforce_admins=false`), and its reduced protection is an explicitly accepted
maintainer risk. No automation may use that bypass to merge a red or stale PR.

The standard build workflow MUST run for pull requests targeting either branch and for direct post-merge builds of both
branches. SonarCloud publication MUST remain limited to official stable `main`; local validation continues on both.

### One-way stable synchronization

Successful changes flow automatically from `main` to `experimental`; there is no automatic wholesale
`experimental -> main` merge.

After a successful build of current `main`, synchronization MUST:

1. start a disposable branch from the latest `experimental`;
2. merge the exact successful `main` SHA into it;
3. stop and create or update a synchronization-failure issue if Git reports a conflict;
4. push the disposable branch and open a PR targeting `experimental` with the repository `GITHUB_TOKEN`;
5. explicitly dispatch the standard build against the disposable branch so the exact PR-head SHA receives `tests`;
6. enable auto-merge only after the strict check succeeds and the PR remains current with `experimental`;
7. dispatch a post-merge build for the resulting current `experimental` SHA when token recursion suppression would
   otherwise prevent it; and
8. delete the disposable branch after merge.

The workflow MUST verify the current source and target SHAs at every state transition. If either branch changes, it MUST
refresh and retest the PR rather than merging stale evidence. A merge conflict MUST be left for a maintainer; automation
MUST NOT guess a conflict resolution.

Synchronization uses only the ephemeral repository `GITHUB_TOKEN` with narrowly scoped `contents`, `pull-requests`, and
`actions` permissions where needed. It MUST NOT introduce a classic PAT, fine-grained PAT, deploy key, or persistent
personal credential. Explicit `workflow_dispatch` is the approved way to cross GitHub's workflow-recursion boundary.

Experimental behavior MUST NOT be copied back wholesale. When an experimental adaptation represents a generally useful
stable change, it requires an independently reviewed PR to `main` based on that change's own stable contract.

### Renovate dependency separation

Renovate MUST inspect both `main` and `experimental`, but apply branch-specific dependency policy:

- `main` tracks official stable `com.seed4j:seed4j` versions with the repository's existing runtime-dependency release
  semantics and MUST ignore the personal snapshot coordinate;
- `experimental` tracks `io.github.renanfranca:seed4j-main-snapshot` through the Central snapshot registry with unstable
  versions enabled; and
- a personal snapshot update may auto-merge only when every required check succeeds and the branch is current.

If a snapshot changes an imported Seed4J API incompatibly, the Renovate PR remains open and red. A maintainer adapts the
experimental CLI in that PR or a superseding PR. Renovate MUST NOT fall back to an older official release, rewrite the
upstream artifact, bypass tests, or merge the breakage.

When Seed4J publishes a new official stable version, Renovate opens its normal independent PR to `main`. If adaptation
is necessary, `experimental` is reference material only; the stable change is designed and validated for the official
release, then flows back through the normal `main -> experimental` synchronization.

### npm experimental release

Semantic-release MUST treat `experimental` as a prerelease branch and publish versions shaped as
`<next-stable>-experimental.<n>` to npm dist-tag `experimental`. The package MUST use npm Trusted Publishing and
provenance through the authorized GitHub workflow; no persistent npm token may be stored.

An experimental release MUST create the immutable Git tag required by semantic-release and publish the npm package. It
MUST NOT publish or modify a GitHub Release, Release Drafter draft, stable JAR asset, `latest` dist-tag, or stable release
tag. Existing `main` release and recovery behavior remains unchanged.

An experimental release is eligible only after a successful standard build for the exact current `experimental` HEAD.
The release workflow MUST reject a stale build, a non-`experimental` ref, an unprotected pull-request revision, or an
already released exact commit. A compatibility failure therefore blocks publication instead of leaving the npm package
partially aligned with its Maven dependency.

### Experimental npm rollback

A confirmed bad experimental npm version is recovered manually from a trusted local npm session with 2FA. The runbook
MUST validate the package name, good version, bad version, current `latest`, current `experimental`, and issue URL before
displaying these maintainer commands:

```bash
npm dist-tag add seed4j-cli@<last-good-version> experimental
npm deprecate seed4j-cli@<bad-version> "Bad experimental build; see <issue-url>"
```

The maintainer, not CI, executes the commands. Rollback MUST NOT use a persistent npm token, change `latest`, unpublish a
version, move an immutable Git tag, or create a replacement version. A forward fix returns through the normal checked
experimental release flow.

## Documentation, support, and official exit

The README and maintainer/development documentation MUST describe:

- stable installation as the default and experimental installation as deliberate opt-in;
- the unofficial Maven coordinate, upstream-SHA provenance, snapshot-retention limitation, and support status;
- why `seed4j-extension` is unavailable while runtime-extension management remains supported;
- publisher account, namespace, environment, token rotation, pilot, quota review, retry, monitoring, and rollback
  procedures; and
- the one-way branch and dependency-update model.

The documentation SHOULD cite established explicit main-derived channel precedents such as React Canary and Next.js
canary while avoiding any claim that those projects endorse this implementation.

When Seed4J exposes an official Maven main/canary channel, detection MAY open a migration issue but MUST NOT switch the
CLI automatically. A maintainer MUST first change the experimental profile to the official GAV/repository and validate
the complete experimental build and npm package. Only after that migration is released and verified may the personal
publisher be disabled. Existing personal snapshots are not deleted and expire under Central retention.

## Acceptance scenarios

### Channel isolation

1. Given a stable build, `seed4j --version`, root help, module discovery, `seed4j-extension`, Maven resolution, npm
   publication, GitHub Release, and attached JAR behavior match the pre-feature stable contract.
2. Given an experimental npm installation, `seed4j --version` reports the prerelease CLI version, experimental channel,
   exact personal Maven snapshot, and exact official upstream SHA.
3. Installing `seed4j-cli@experimental` does not move `latest`; publishing or rolling back experimental does not alter a
   stable tag, version, GitHub Release, or asset.

### Module availability

1. Experimental list, apply help, catalog, and completion interfaces omit `seed4j-extension`.
2. Direct experimental application of the unavailable module returns the prescribed exit code and diagnostic and makes
   no observable project or runtime change.
3. An experimental apply-set containing the unavailable module fails before project or planning inspection and applies
   none of the other requested modules.
4. Stable application of `seed4j-extension` remains available, while experimental `seed4j extension ...` runtime
   management continues to work.

### Snapshot identity and availability

1. Repeated derivation from the same upstream commit produces the same base version; a newer commit produces the version
   corresponding to its own POM version, UTC commit time, and SHA.
2. A candidate without a successful aggregated official-upstream check is skipped without credential access or
   publication.
3. A qualified candidate passes the upstream lint and complete Maven validation before any deployment credential becomes
   available.
4. A clean Maven environment resolves the published POM, main JAR, and tests-classifier JAR from the Central snapshot
   repository; no sources or Javadoc artifact is present.
5. A current SHA published less than 60 days ago is skipped, while an unchanged SHA at 60 days is republished under the
   same base version.
6. Manifest tampering, an extra artifact, a digest mismatch, or an unexpected coordinate is rejected without executing
   upstream code in the privileged job.

### Automation and recovery

1. A successful current `main` build creates a tested synchronization PR to `experimental`; a conflict creates the
   persistent issue and changes neither protected branch.
2. A PR created with `GITHUB_TOKEN` receives `tests` through explicit dispatch and cannot auto-merge while red, stale, or
   conflicting.
3. A compatible personal snapshot Renovate PR merges and releases through the experimental pipeline; an incompatible PR
   remains open without npm publication.
4. A genuine publisher failure updates one assigned issue with actionable provenance, and a later successful current
   publication closes it. Expected upstream skips do not open it.
5. A local rollback moves only `experimental` to the recorded good version and deprecates the bad version while
   preserving `latest` and all published artifacts.

## External prerequisites and explicit limits

End-to-end activation is blocked until the maintainer creates the public personal publisher repository, verifies the
Central namespace, creates the dedicated token and protected environment, configures npm Trusted Publishing for the
authorized workflow, creates `experimental`, and applies the approved branch protections. These are external setup
tasks, not dependencies on the Seed4J repository owner.

This specification deliberately excludes:

- publication under `com.seed4j`, use of Seed4J owner credentials, or changes to the upstream repository;
- a GitHub Packages Maven repository, a repository hosted inside `seed4j-cli`, or a fork presented as official;
- arbitrary-SHA publication, secrets in upstream build jobs, long-lived GitHub PATs, or persistent npm tokens;
- generated extension projects that depend on the personal snapshot;
- automatic conflict resolution, automatic compatibility rewrites, or reverse branch synchronization;
- automatic paid-plan selection, automatic switch to a future official feed, or deletion of old snapshots; and
- stable-channel warnings or behavior changes merely because the experimental channel exists.

Normative operational references:

- [Central namespace verification](https://central.sonatype.org/register/namespace/)
- [Central Portal snapshot publication](https://central.sonatype.org/publish/publish-portal-snapshots/)
- [Central publishing limits](https://central.sonatype.org/publish/maven-central-publishing-limits/)
- [Central Portal user tokens](https://central.sonatype.org/publish/generate-portal-token/)
- [npm Trusted Publishing](https://docs.npmjs.com/trusted-publishers/)
- [GitHub Actions workflow recursion](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow#triggering-a-workflow-from-a-workflow)
- [React versioning policy](https://react.dev/community/versioning-policy)
- [Next.js release channels](https://github.com/vercel/next.js/blob/canary/contributing/repository/release-channels-publishing.md)
