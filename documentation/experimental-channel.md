# Experimental Seed4J main channel

Using the experimental channel requires a deliberate installation choice. It lets users test Seed4J CLI against a recent
official Seed4J `main` revision. This unofficial integration channel is maintained by `renanfranca` when capacity
permits; it is not supported or endorsed by the Seed4J maintainers and is not recommended for production generation
workflows.

The channel is publicly installable. Early adopters should start with the channel contract and identity checks below.
Maintainers should also follow the build, publisher, intervention, rollback, and exit procedures in this runbook.

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
| Support                      | Normal project support             | Unofficial, best effort, and limited retention             |

Install experimental only when you accept snapshot expiry and possible incompatibility with a newer Seed4J `main`:

```bash
npm install -g seed4j-cli@experimental
seed4j --version
```

Return to stable at any time:

```bash
npm install -g seed4j-cli@latest
```

Updates are not automatic across npm dist-tags. Installing `@experimental` explicitly selects that channel. Returning
to `@latest` also requires an explicit installation. Never use an unqualified install when validating which channel is
active.

After a successful experimental npm invocation, the launcher may print an informational `stderr` notice when a newer
experimental version is available. It identifies the installed and available versions and suggests
`npm install -g seed4j-cli@experimental` for a later, opt-in update. It may also report that a local or user-level
installed agent skill differs from the skill bundled with the active CLI, suggesting `seed4j skill install` or
`seed4j skill install --global` for the affected destination. Neither notice changes the current command or updates
anything automatically. Notices are suppressed during completion generation, skill installation, and unsuccessful
commands; direct JAR invocations and the stable npm channel do not perform these checks.

Inspect the registry and installed identity without copying a current version or SHA into documentation that must remain
accurate over time:

```bash
npm view seed4j-cli dist-tags --json
seed4j --version
npm view seed4j-cli@experimental dist.attestations --json
```

The attestation result should expose an npm provenance URL and a SLSA provenance predicate.

## Identity, provenance, and retention

Each package carries immutable distribution metadata under `META-INF`. It records the release channel, exact dependency
coordinate, and unavailable module slugs. For an experimental distribution, the complete official `seed4j/seed4j`
upstream commit SHA is part of the Maven version and the CLI derives it from that version. The metadata is filtered at
build time from Maven-owned properties and cannot be replaced by user configuration, environment properties, command
options, or a runtime extension.

That Maven authority belongs to the branch, not to a runtime selector. `main` contains only the stable official
coordinate and declares no personal snapshot repository. `experimental` replaces those authority values at the top
level with the personal coordinate and module availability facts. It also adds a repository that
contains only snapshots. Ordinary Maven build and release commands package the identity owned by the branch in the
checkout.

On experimental, `seed4j --version` includes the channel, personal snapshot version, and upstream SHA. Record that
output in every compatibility report. The SHA identifies the official Seed4J source used by the external publisher; it
does not identify the CLI commit or a runtime extension.

Central Portal snapshots have a limited retention window of approximately 90 days. A generated project that depends on
an expired snapshot may stop rebuilding. The publisher refresh policy makes a snapshot eligible for republication after
it has remained current for 60 days, but it is not a durability guarantee. Prefer stable for projects that must remain
reproducible or maintained for a long time.

## Command behavior

Experimental root help displays a warning once when help is requested. Ordinary command output remains unchanged.
`seed4j-extension` is removed from module listings, visible apply help, apply-set catalogs, and shell completion.
Requesting it directly or in an apply set exits `2` before project, history, filesystem, Git, dependency, property, or
execution inspection and reports that no changes were applied.

The `seed4j extension` command group remains available. Installing, enabling, disabling, and inspecting runtime
extensions is separate from generating a new extension project. Runtime extensions cannot override the packaged channel
metadata.

## Build, release, and branch isolation

The complete path is:

1. the external publisher qualifies official Seed4J `main` and publishes the personal snapshot to Central;
2. Renovate's native Maven manager proposes the published full-SHA version to the CLI `experimental` branch;
3. a protected `experimental` merge receives the standard build; and
4. the build, authenticated as `github-actions[bot]`, requests qualification and npm publication from trusted `main`
   workflow code.

Stable CLI changes travel separately from `main` to `experimental` through a checked synchronization PR. There is no
automatic wholesale `experimental` to `main` merge. A generally useful experimental adaptation needs its own PR to
`main`, designed for stable use, and then flows forward again.

The standard `build` workflow validates pushes and pull requests for both protected branches. Both branches require pull
requests, the strict `tests` check, and zero mandatory approvals; force pushes and deletion are disabled, while
administrator bypass remains available as an accepted maintainer risk. Automation must never use that bypass to merge
red or stale work. Local Sonar runs on both branches, while SonarCloud publication remains restricted to official stable
`main`.

After a successful push build for the exact current `main` SHA, synchronization creates or refreshes
`automation/sync-main-to-experimental` from current `experimental`, merges that exact `main` SHA, and opens one PR back
to `experimental`. A Git conflict stops before either protected branch changes and creates or updates the assigned
`synchronization-failure` issue. A later authoritative clean or already-contained preparation closes that issue.

Synchronization separates identities deliberately:

- an ephemeral GitHub App installation token authenticates the trusted checkout, the push of the disposable branch, and
  PR creation or update;
- the repository `GITHUB_TOKEN` authenticates workflow dispatches, issue operations, and finalization; and
- the App client ID comes from variable `SYNC_APP_CLIENT_ID`, while its private key comes from secret
  `SYNC_APP_PRIVATE_KEY`; values must never be copied into documentation or logs.

The App installation is restricted to `seed4j/seed4j-cli` and grants only `Contents: write` and
`Pull requests: write`. The required `synchronization-pending` and `synchronization-failure` labels must remain present.

Because the App creates the PR, GitHub emits an ordinary pull request event and the proposal receives its normal build.
Synchronization also explicitly dispatches `github-actions.yml` for the exact head of the disposable branch. This gives
the workflow immutable evidence that it can repair if a callback is missed. Finalization accepts only the supplied
numeric run ID and revalidates the run SHA, source repository, proposal branch, bot actor, completion, current source and
target branches, topology with exactly two parents in target/source order, live PR head, and green `tests` result. Only
current evidence enables automatic merge. Stale, pending, red, malformed, or conflicting evidence leaves the PR open or
requests a refresh.

The bounded finalizer and recovery, which runs every 15 minutes, complete asynchronous merges and repair a single exact
proposal that was only partially published. They dispatch a missing build for the exact head once, finalize a reachable
merge, and remove the disposable branch and pending label in an order that is safe to retry. Recovery never fabricates
evidence, guesses conflict resolution, or enables merge without a current green review. See
[`Synchronize stable changes into experimental`](workflows.md#synchronize-stable-changes-into-experimental) for the short
operator recipe.

Renovate keeps dependency contexts separate. `main` tracks stable `com.seed4j:seed4j` releases and ignores the personal
coordinate. The full-SHA snapshot has resolved and built on `experimental`, so the final configuration uses one native
Maven rule against Central snapshots, with unstable versions
enabled, and changes only `seed4j.version`. A snapshot PR merges automatically only after required checks pass on current
`experimental`; an incompatible update remains open and red for a maintainer adaptation. The hosted Renovate application
determines polling cadence, so the repository does not guarantee a fixed six-hour interval.

After the exact current `experimental` HEAD passes a build explicitly dispatched by `github-actions[bot]`, that build
dispatches `release.yml` on `main` with `operation=experimental`, `experimental-sha`, and `build-id`. Qualification runs
from trusted `main`, retrieves the build evidence again, and validates the run ID, SHA, repository, branch, event, bot
actor, conclusion, and protected branch head. The publish job checks out only the selected SHA, rechecks that it is still
the protected `experimental` HEAD, and then runs semantic-release.

An eligible release may create `<next-stable>-experimental.<n>`, push its immutable `v<version>` tag, and publish npm
with dist-tag `experimental` through npm Trusted Publishing and provenance. A commit that only changes documentation, or
any other commit that does not qualify for release, completes without a new npm version. Experimental publication never
creates or changes a GitHub Release, Release Drafter draft, stable JAR asset, stable tag, or npm `latest` tag.

## Publisher operations

The personal Maven publisher belongs to the separate public
[`renanfranca/seed4j-main-snapshots`](https://github.com/renanfranca/seed4j-main-snapshots) repository. Its
[`config/publisher.json`](https://github.com/renanfranca/seed4j-main-snapshots/blob/main/config/publisher.json) and
[`README.md`](https://github.com/renanfranca/seed4j-main-snapshots#pilot-and-schedule-policy) are the dynamic authorities
for cadence, token expiry, quota review, qualification, retention refresh, failure reporting, and
retry behavior. Read them again before an operational change instead of treating this CLI repository as a second live
configuration source.

The expected current configuration is `pilotCompleted=true`, `scheduleMode=weekly`, and `quotaReview=null`. Under that
configuration, Monday at `06:17 UTC` is eligible. The other `06:17 UTC` cron entries remain inert with
`daily-schedule-not-enabled`. Daily operation requires a reviewed publisher configuration change backed by a newly
recorded Central Usage Center calculation showing both projected monthly release count and stored bytes at no more than
80% of their limits. Automation never selects a paid plan.

The publisher skips an unsuccessful upstream build and a snapshot published less than 60 days ago without opening a
failure issue. A build or deployment failure after qualification creates or updates the single assigned publisher
issue. The only manual publisher operations are `head` and
`retry-last-failed`; retry derives the candidate from trusted recorded state and does not accept a repository, ref, SHA,
coordinate, or version supplied freely by the caller.

### Automatic versus maintainer intervention

| Situation                                                    | Normal handling                                                                                          |
| ------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------- |
| Expected skip because of upstream CI or a recent snapshot    | Automatic; inspect the workflow summary only.                                                            |
| Retention refresh after 60 days                              | Automatic on the next eligible publisher run.                                                            |
| Clean `main` synchronization                                 | Automatic proposal, normal and dispatched builds, review of current evidence, merge, and finalization.   |
| Compatible green Renovate snapshot update                    | Automatic protected merge, build, qualification, and experimental release evaluation.                    |
| Release evaluation when no commit qualifies for release      | Automatic successful completion without a new npm version.                                               |
| Synchronization conflict                                     | Maintainer resolves it in a reviewed PR, then dispatches `synchronize main to experimental` from `main`. |
| Incompatible or red Renovate update                          | Maintainer adapts `experimental`; tests and protected merge remain mandatory.                            |
| Genuine publisher issue                                      | Maintainer repairs the trusted publisher or outage, then uses `retry-last-failed`.                       |
| Central token rotation                                       | Maintainer rotates the dedicated token and records the new expiry in the publisher configuration.        |
| Missing GitHub App variable, secret, installation, or labels | Maintainer restores the documented repository setup; do not substitute a PAT or relax protection.        |
| Change from weekly to daily                                  | Maintainer records a quota review showing no more than 80% and changes configuration through a green PR. |
| Bad experimental npm version                                 | Maintainer follows the rollback procedure below from a trusted local npm session with 2FA.               |
| Official replacement for the personal channel                | Maintainer follows the procedure below for moving to an official channel.                                |

Accept the native Maven integration after one real snapshot creates exactly one PR whose diff changes only
`seed4j.version`. If it misses the snapshot or creates duplicates, disable the experimental Maven rule and update that
single property manually until a separate design decision. Do not add a second experimental updater.

## Roll back a bad experimental npm version

Rollback is manual from a trusted local npm session protected by 2FA. First query the registry, select the last known
good and bad experimental versions, and create a public `seed4j/seed4j-cli` issue:

```bash
npm view seed4j-cli dist-tags --json
npm view seed4j-cli@experimental dist.attestations --json
```

Then run the local validator with the observed values:

```bash
GOOD_VERSION='replace with the last known good experimental version'
BAD_VERSION='replace with the bad experimental version'
CURRENT_LATEST="$(npm view seed4j-cli dist-tags.latest)"
CURRENT_EXPERIMENTAL="$(npm view seed4j-cli dist-tags.experimental)"
ISSUE_URL='replace with the public seed4j-cli issue URL'

node scripts/experimental-rollback.cjs \
  --package seed4j-cli \
  --good "$GOOD_VERSION" \
  --bad "$BAD_VERSION" \
  --current-latest "$CURRENT_LATEST" \
  --current-experimental "$CURRENT_EXPERIMENTAL" \
  --issue-url "$ISSUE_URL"
```

The validator never executes npm. A valid request prints the exact `npm dist-tag add` and `npm deprecate` commands for
the supplied values. Review and execute those commands yourself. Do not change `latest`, unpublish a version, move an
immutable Git tag, or create an ad hoc replacement version. Forward fixes return through the checked experimental build
and release flow.

## Official-channel exit

If Seed4J begins publishing an official per-`main`, canary, or equivalent durable coordinate, stop the personal
publisher schedule first. In reviewed pull requests, validate the official provenance and retention contract, replace
the personal coordinate and repository on `experimental`, update packaged identity and support wording, and run both
channel suites. Publish a new experimental version before retiring the monitoring of personal snapshots. Preserve
historical npm versions and immutable Git tags; never rewrite them to claim official provenance.

This channel design follows the general precedent of explicit, pinned prerelease channels such as
[React Canary](https://react.dev/blog/2023/05/03/react-canaries) and the
[Next.js canary channel](https://nextjs.org/support-policy). Those projects do not endorse this Seed4J CLI channel, and
their support guarantees do not apply here.
