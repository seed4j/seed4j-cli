# Seed4J CLI workflows

These recipes organize commands around concrete outcomes. Use the [commands reference](Commands.md) when you need exact options, exit behavior, configuration keys, or failure contracts.

Stable installation is the default. Before choosing the optional npm experimental channel, read its
[provenance, retention, module restrictions, update, and rollback contract](experimental-channel.md).

The standard build deliberately has only `contents: read`. Checkout does not persist its GitHub credential into later
steps controlled by repository code, and SonarCloud authenticates with its dedicated `SONAR_TOKEN`; every unspecified
GitHub permission is denied.

## Synchronize stable changes into experimental

A successful standard push build for current `main` starts a flow that only moves changes toward `experimental`.
Automation prepares
`automation/sync-main-to-experimental` from current `experimental`, merges the exact green `main` SHA, and opens or
refreshes a PR targeting `experimental`. The GitHub App authenticates the branch and PR mutations; `GITHUB_TOKEN`
authenticates the explicit build dispatch, issue operations, and finalization.

Use this operator recipe:

1. Inspect the synchronization PR only when automation reports a problem. A current proposal receives both the normal
   build for the pull request and a build explicitly dispatched for the exact head.
2. Take no action while the exact proposal is green and current. The finalizer revalidates run identity, branches,
   topology, PR head, and `tests`, then enables automatic merge. Scheduled recovery completes the remaining housekeeping
   in an order that is safe to retry.
3. If `synchronization-failure` reports a Git conflict, resolve it through a reviewed PR and dispatch
   `synchronize main to experimental` from `main` to retry. Never bypass a pending or failed check.
4. If the App variable, secret, installation, or required labels are missing, restore that repository setup before
   retrying. Do not substitute a PAT or relax branch protection.

The workflow never synchronizes `experimental` wholesale back to `main`. The complete contract for credentials,
evidence, recovery, branch protection, and intervention is in the
[experimental channel runbook](experimental-channel.md#build-release-and-branch-isolation).

## Update the experimental Seed4J snapshot

The personal publisher deploys each qualified snapshot to Central with the complete upstream SHA embedded in the Maven
version. After migration, Renovate's native Maven manager reads Central on its hosted cadence and opens one PR against
`experimental` that changes only `seed4j.version`.

Use this operator recipe:

1. Publish and resolve a full-SHA snapshot, then update `experimental` manually and require its protected `tests` check.
2. Remove `seed4j.upstream-commit` from the POM and `seed4j-upstream-commit` from packaged metadata. The SHA now comes
   only from `seed4j.version`.
3. Enable the single native Maven rule and verify that no experimental regex manager or competing rule remains.
4. If a Renovate PR is red because Seed4J APIs changed, adapt the CLI in that PR or a superseding reviewed PR.
5. Accept the integration only after one real snapshot creates exactly one PR changing only `seed4j.version`. If native
   detection fails or duplicates appear, pause the rule and update the version manually pending a new decision.

See the
[experimental channel runbook](experimental-channel.md#publisher-operations) for failure and recovery details.

## Create a project with modules

A typical workflow to initialize a new project might look like:

1. Create a project directory and navigate to it:

   ```bash
   mkdir my-project
   cd my-project
   ```

2. Initialize a new project:

   ```bash
   seed4j apply init --project-name "My Project" --base-name MyProject --node-package-manager npm
   ```

3. Add code formatting support:

   ```bash
   seed4j apply prettier
   ```

4. Set up a Maven project structure:

   ```bash
   seed4j apply maven-java --package-name com.example.myproject
   ```

5. Add Maven wrapper:

   ```bash
   seed4j apply maven-wrapper
   ```

6. Add Java base classes:

   ```bash
   seed4j apply java-base
   ```

7. Add Spring Boot:

   ```bash
   seed4j apply spring-boot
   ```

After this basic setup, you can add more specific modules based on your project requirements. The [apply reference](Commands.md#apply-a-module) explains dependency planning, parameter reuse, `--plan`, and blocking exit behavior.

## Update an installation that used a runtime extension

Set `seed4j.runtime.mode: standard` in `~/.config/seed4j-cli/config.yml` and run `seed4j list`. The CLI preserves your
previously installed JAR, metadata and cache. `seed4j extension --help` explains the retirement of the old commands.

On stable, run `seed4j apply seed4j-extension --help` to see how to generate an extension project. Adoption of custom
extensions through Maven coordinates is tracked by [Seed4J #14926](https://github.com/seed4j/seed4j/issues/14926) and
[Seed4J CLI #6](https://github.com/seed4j/seed4j-cli/issues/6).
