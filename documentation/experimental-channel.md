# Experimental Seed4J main channel

The experimental channel is a deliberate opt-in for testing Seed4J CLI against a recent official Seed4J `main`
revision. It is an unofficial, best-effort integration channel maintained by `renanfranca`; it is not supported or
endorsed by the Seed4J maintainers and is not recommended for production generation workflows.

The channel becomes installable only after the external publisher, protected `experimental` branch, npm Trusted
Publisher, and first observed release have all been activated. Until those gates are complete, the commands below
describe the operating contract rather than announcing availability.

## Channel contract

| Concern                      | Stable                             | Experimental                                               |
| ---------------------------- | ---------------------------------- | ---------------------------------------------------------- |
| Git branch                   | `main`                             | `experimental`                                             |
| npm installation             | `npm install -g seed4j-cli@latest` | `npm install -g seed4j-cli@experimental`                   |
| npm version                  | Stable semantic version            | `<next-stable>-experimental.<n>`                           |
| npm dist-tag                 | `latest`                           | `experimental`                                             |
| Seed4J dependency            | `com.seed4j:seed4j` release        | `io.github.renanfranca:seed4j-main-snapshot` snapshot      |
| Maven repository             | Release repositories               | `https://central.sonatype.com/repository/maven-snapshots/` |
| `seed4j-extension` generator | Available                          | Unavailable                                                |
| Support                      | Normal project support             | Unofficial, best effort, and retention-limited             |

Install experimental only when you accept snapshot expiry and possible incompatibility with a newer Seed4J `main`:

```bash
npm install -g seed4j-cli@experimental
seed4j --version
```

Return to stable at any time:

```bash
npm install -g seed4j-cli@latest
```

Updates are not automatic across npm dist-tags. Installing `@experimental` is a deliberate one-way choice; returning
to `@latest` is also explicit. Never use an unqualified install when validating which channel is active.

## Identity, provenance, and retention

Each package carries immutable distribution metadata under `META-INF`. It records the release channel, exact dependency
coordinate, exact 40-character official `seed4j/seed4j` upstream commit, and unavailable module slugs. The metadata is
filtered at build time from the same Maven-owned group, artifact, version, channel, upstream-SHA, repository, and
availability properties used by both Seed4J dependencies. It cannot be replaced by user configuration, environment
properties, command options, or a runtime extension.

That Maven authority belongs to the branch, not to an activation profile. The main-bound POM contains only the stable
official coordinate and declares no personal snapshot repository. The later experimental branch replaces those same
top-level authority values with the personal coordinate, full upstream SHA, and unavailable-module facts, and adds its
snapshot-only repository. Ordinary Maven build and release commands therefore package the identity already owned by the
checked-out branch; they never choose a channel with `-Pexperimental` or another runtime flag.

On experimental, `seed4j --version` includes the channel, personal snapshot version, and upstream SHA. Record that
output in every compatibility report. The SHA identifies the official Seed4J source used by the external publisher; it
does not identify the CLI commit or a runtime extension.

Central Portal snapshots have a limited retention window of approximately 90 days. A generated project that depends on
an expired snapshot may stop rebuilding. The publisher refresh policy aims to republish a still-current snapshot after
60 days, but it is not a durability guarantee. Prefer stable for long-lived or reproducible projects.

## Command behavior

Experimental root help displays a warning once when help is requested. Ordinary command output remains unchanged.
`seed4j-extension` is removed from module listings, visible apply help, apply-set catalogs, and shell completion.
Requesting it directly or in an apply set exits `2` before project, history, filesystem, Git, dependency, property, or
execution inspection and reports that no changes were applied.

The `seed4j extension` command group remains available. Installing, enabling, disabling, and inspecting runtime
extensions is separate from generating a new extension project. Runtime extensions cannot override the packaged channel
metadata.

## Build, release, and branch isolation

The standard `build` workflow validates pushes and pull requests for both `main` and `experimental`. Local Sonar runs on
both branches; SonarCloud publication remains restricted to the official stable `main` branch. The build declares only
`contents: read`, checkout does not persist its GitHub credential for later repository-controlled steps, and SonarCloud
uses its dedicated analysis token. Unspecified GitHub permissions therefore remain denied.

Both protected branches require a current `tests` check before merge. Updates flow from `main` to `experimental` through
a checked synchronization pull request. There is no automatic wholesale `experimental` to `main` merge. A generally
useful experimental adaptation needs its own stable-focused pull request to `main` and then flows forward again.

After a successful push build for the exact current `main` SHA, `synchronize main to experimental` creates or refreshes
the disposable `automation/sync-main-to-experimental` branch from the latest `experimental`, merges that exact `main`
SHA, and opens one PR back to `experimental`. Git conflicts stop before any protected-branch write and create or update
the assigned `synchronization-failure` issue; automation never guesses a resolution. A later clean preparation closes
that issue. Before enabling synchronization, a maintainer must create the repository label `synchronization-pending`;
the workflow verifies this prerequisite before proposal publication and attaches the label to every pending PR.

PR creation with `GITHUB_TOKEN` does not reliably recurse into ordinary workflows, so synchronization explicitly
dispatches `github-actions.yml` on the disposable branch immediately after publishing the exact PR, before conflict-issue
housekeeping. If publication stops before that dispatch, scheduled recovery checks the exact recorded proposal head and
dispatches only when no matching run exists. A queued, running, successful, or failed completed run is reused; only the
ordinary green-build review can enable merging. The PR body binds the source, target, and proposal head SHAs.
The finalizer re-fetches both protected branches and the disposable branch, requires the recorded head to equal both
the live PR head and fetched Git head, and requires that merge commit's parents to be the recorded target followed by
the recorded source. Only that exact head with a completed successful standard build and a conflict-free open PR can
enable auto-merge. Stale source, target, head, topology, or test evidence causes a refresh and another explicit test
dispatch; pending or red tests leave the PR open without auto-merge.

Auto-merge is asynchronous, so the bounded confirmation window always dispatches a durable finalizer. Scheduled
15-minute recovery queries a lightweight number/state index of up to 100 PRs carrying `synchronization-pending`, so older
unfinished work is not displaced by completed history. It then reads each exact PR independently and terminates that
capture at 2 MiB; malformed or oversized comments fail only that candidate. Explicit recovery reads the requested PR
directly without relying on the list window. Scheduled recovery separately performs one result-bounded lookup for an
OPEN PR with the exact `automation/sync-main-to-experimental` head and `experimental` base, independent of labels. This
closes the partial-publication gap where `gh pr create --label` creates the PR but its metadata update returns nonzero.
No result is inert; malformed or multiple results fail before candidate effects; and a result already in the normal
label index is deduplicated. Before an unlabeled result receives `synchronization-pending`, recovery validates its
canonical state, live and fetched PR head, fetched fixed-branch head, current protected heads, and exact two-parent
target/source topology. It then reuses or dispatches exactly one proposal build and never enables merge.
Executable policy deterministically handles older
merged work before open work and skips only an exact completion record authored by `github-actions[bot]` and bound to the
PR number, proposal head, merge commit, and dispatched `experimental` SHA. Plain, forged, decorated, stale, or mismatched
comments cannot suppress recovery. Candidate failures are isolated, and all stale candidates request at most one refresh
per run. An open PR is left pending only while current `main` and `experimental` still equal its recorded source and
target; either movement requests a refreshed proposal. Once an exact PR is observed merged and its merge commit is
reachable from current `experimental`, recovery reuses an exact queued, running, or successful standard build or
dispatches one for one stable current `experimental` head. It deletes the disposable branch atomically only under an
expected-head lease, writes the authenticated completion record, and removes the pending label last. A label-removal
failure is retried without repeating completed effects. Historical finalization never closes a conflict issue belonging
to newer current preparation. These operations use only the repository's ephemeral `GITHUB_TOKEN` with job-scoped
Actions, contents, and pull-request permissions; current preparation separately owns issue updates.

Renovate keeps the dependency contexts separate:

- `main` tracks stable `com.seed4j:seed4j` releases and ignores the personal coordinate;
- `experimental` tracks unstable `io.github.renanfranca:seed4j-main-snapshot` versions only through the Central snapshot
  registry; and
- snapshot pull requests auto-merge only with every required check green and after rebasing onto current
  `experimental`. The main manager matches only the stable authority marker present on `main`; the snapshot manager
  becomes applicable only where the experimental branch has replaced that marker and its top-level values. Renovate
  updates the Maven-owned snapshot version; build policy also requires its 12-character SHA component to match the
  separately recorded full upstream SHA, so a version-only update remains red until provenance is updated. An
  incompatible update remains open and red for maintainer adaptation.

After an exact current `experimental` push, or the exact bot-issued post-synchronization `workflow_dispatch`, passes
`build`, semantic-release may create
`<next-stable>-experimental.<n>`, push its immutable `v<version>` tag, and publish npm with dist-tag `experimental`.
Publication uses npm Trusted Publishing with GitHub OIDC and provenance; no persistent npm token is stored. A stale SHA,
wrong branch, untrusted dispatch, pull-request build, unsuccessful build, or already tagged revision is rejected. Stable
release eligibility remains push-only. A permission-minimal qualification job checks provenance using code from trusted
default `main` before the write/OIDC publication job can start or the qualified target revision is checked out. The
publisher consumes only the accepted channel and SHA, then rechecks that channel's protected head before running target
code. Qualification fetches only the selected channel, so stable release and recovery do not depend on `experimental`
existing.

Experimental publication never creates or changes a GitHub Release, Release Drafter draft, stable JAR asset, stable
tag, or npm `latest` tag. Stable publication and recovery remain `main`-only.

## Publisher operations

The personal Maven publisher belongs in the separate public repository
[`renanfranca/seed4j-main-snapshots`](https://github.com/renanfranca/seed4j-main-snapshots). Before activation, verify:

- Central Portal namespace `io.github.renanfranca` is verified through the `renanfranca` GitHub account;
- the protected GitHub Environment is named `central-snapshots` and is limited to the publisher repository's `main`;
- `CENTRAL_USERNAME` and `CENTRAL_PASSWORD` contain a dedicated Central user token, never a personal account password;
- the recorded token lifetime is 180 days and a rotation-warning issue is due 30 days before expiry;
- build jobs have no secrets, environment, repository-write permission, or publication credential;
- only a separate trusted deployment job can enter `central-snapshots`; and
- Java 25, Node.js 24, Maven Wrapper 3.9.16, and pinned third-party actions match the publisher policy.

The first publication is a manually observed `head` pilot. Confirm the resolved official upstream SHA, successful
upstream build, derived snapshot version, manifest digests, deployed POM/main JAR/tests JAR, clean Maven resolution, and
CLI compatibility before enabling the schedule.

After the pilot, the default schedule is Monday at `06:17 UTC`. Daily `06:17 UTC` execution is allowed only after the
maintainer records a Central Usage Center calculation showing projected monthly release count and stored bytes at no
more than 80% of their limits. Otherwise keep weekly operation and request a community OSS allowance; automation must
not purchase or authorize a paid plan.

Manual publisher operations are limited to `head` and `retry-last-failed`. Retry derives the recorded candidate from the
single publisher-failure issue, confirms that its SHA remains reachable from official upstream, and re-derives the same
version. There is no free-form repository, ref, SHA, coordinate, or version input.

Monitor the weekly run, the one open publisher-failure issue, token-rotation issue, Central snapshot availability, npm
provenance, and the experimental CLI build. An upstream CI skip is expected and does not open a publisher-failure issue.
Failures after qualification update the one issue with stage, run, full SHA, version, and concise diagnostic. A
qualifier checkout, setup, dependency, cancellation, or timeout failure before candidate outputs exist instead records
a nonretryable qualification failure without invented identity or provenance. Every captured diagnostic is bounded to
one inert line so untrusted mentions and Markdown links cannot trigger notifications or trusted-looking links; only the
template's explicit `@renanfranca` mention remains active. A successful retry or newer publication closes the issue.

## Roll back a bad experimental npm version

Rollback is manual from a trusted local npm session protected by 2FA. First verify the registry's current `latest` and
`experimental` values and create a public `seed4j/seed4j-cli` issue. Then run the local validator with all observed
values:

```bash
node scripts/experimental-rollback.cjs \
  --package seed4j-cli \
  --good 1.2.3-experimental.3 \
  --bad 1.2.3-experimental.4 \
  --current-latest 1.2.2 \
  --current-experimental 1.2.3-experimental.4 \
  --issue-url https://github.com/seed4j/seed4j-cli/issues/123
```

The validator never executes npm. A valid request prints only these two commands with the supplied values:

```bash
npm dist-tag add seed4j-cli@1.2.3-experimental.3 experimental
npm deprecate seed4j-cli@1.2.3-experimental.4 "Bad experimental build; see https://github.com/seed4j/seed4j-cli/issues/123"
```

Review and execute those commands yourself. Do not change `latest`, unpublish a version, move an immutable Git tag, or
create an ad hoc replacement version. Forward fixes return through the checked experimental build and release flow.

## Official-channel exit

If Seed4J begins publishing an official per-`main`, canary, or equivalent durable coordinate, stop the personal
publisher schedule first. In reviewed pull requests, validate the official provenance and retention contract, replace
the personal coordinate and repository on `experimental`, update packaged identity and support wording, and run both
channel suites. Publish a new experimental version before retiring personal-snapshot monitoring. Preserve historical npm
versions and immutable Git tags; never rewrite them to claim official provenance.

This channel design follows the general precedent of explicit, pinned pre-release channels such as
[React Canary](https://react.dev/blog/2023/05/03/react-canaries) and the
[Next.js canary channel](https://nextjs.org/support-policy). Those projects do not endorse this Seed4J CLI channel, and
their support guarantees do not apply here.
