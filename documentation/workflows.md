# Seed4J CLI workflows

These recipes organize commands around concrete outcomes. Use the [commands reference](Commands.md) when you need exact options, exit behavior, configuration keys, or failure contracts.

Stable installation is the default. Before evaluating the opt-in npm experimental channel, read its
[provenance, retention, unsupported-module, update, and rollback contract](experimental-channel.md).

## Synchronize stable changes into experimental

A successful standard push build for current `main` starts the one-way synchronization workflow. It rebuilds the
disposable `automation/sync-main-to-experimental` branch from current `experimental`, merges the exact green `main` SHA,
and opens or refreshes a PR targeting `experimental`. The workflow explicitly dispatches the standard build on that
proposal head as the first repairable effect after PR publication, before issue housekeeping, because changes made by
`GITHUB_TOKEN` do not trigger an unrestricted recursive workflow chain.
Before enabling this workflow, a maintainer must create the repository label `synchronization-pending`. Preparation
fails before publishing a proposal when the label is absent, and every automation-owned synchronization PR receives it.

Automation enables merge only while the independently fetched source, target, and proposal head match the PR's recorded
SHAs, the proposal has exactly the recorded target and source as its parents, and that exact head owns a completed green
`tests` result. If any SHA or topology changes, it refreshes the proposal and tests; red or pending tests leave it open.
A Git conflict creates or updates the single assigned `synchronization-failure` issue without changing either protected
branch. Resolve the conflict through a reviewed change and dispatch `synchronize main to experimental` from `main` to
retry.

Auto-merge can finish after the bounded runner wait. The workflow therefore dispatches a durable finalizer immediately,
while the 15-minute recovery asks GitHub for only number/state metadata for up to 100 PRs carrying
`synchronization-pending`; completed PRs do not consume that bounded window. It loads each exact PR independently through
a streaming 2 MiB limit, so hostile or malformed comment history fails locally without blocking later candidates. An
explicit recovery loads its requested PR number directly. For a still-current open PR, recovery preserves any exact
proposal-head run regardless of whether it is queued, running, green, or red, and dispatches exactly once only when none
exists; it never enables merge. Executable policy skips
only an exact completion record authored by `github-actions[bot]` and bound to the PR number, proposal head, merge
commit, and dispatched `experimental` SHA; plain, forged, decorated, stale, or mismatched comments remain pending.
Older merged work is handled before open work, and one candidate failure does not stop later candidates or lose a
coalesced refresh request. An open PR remains pending only while current `main` and `experimental` still equal its
recorded source and target; otherwise synchronization refreshes it. After an exact merge is reachable from current
`experimental`, recovery reuses an exact queued, running, or successful standard build when present, otherwise dispatches
one for the stable current head. It then deletes the disposable branch with an expected-head lease, writes the trusted
completion record, and finally removes `synchronization-pending`. If that last removal fails, the next run only reconciles
the label. Historical finalization never closes the current conflict issue; only a current authoritative clean or
already-contained preparation may do that. The workflow never synchronizes `experimental` wholesale back to `main`.

Build identity remains branch-owned throughout this flow. `main` carries only the official stable Seed4J authority and
no personal repository. The experimental branch carries the reviewed top-level personal coordinate, full upstream SHA,
unavailable-module metadata, and snapshot-only repository. Synchronization and release commands use the checked-out
branch POM directly; there is no Maven profile or workflow flag that can select the other channel's identity.

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

## Create and install a runtime extension

You can use the official sample repository as a starting point:

- <https://github.com/seed4j/seed4j-sample-extension>
- <https://github.com/seed4j/seed4j-sample-extension/blob/main/documentation/module-creation.md>

Recommended implementation flow for this CLI runtime mode:

1. Create an extension project that exposes modules as Spring beans (`@Configuration` + `@Bean`).
2. Define a slug enum implementing `Seed4JModuleSlugFactory`.
3. Implement a factory that builds a `Seed4JModule`.
4. Expose a `Seed4JModuleResource` bean wired to your application service.
5. Build your extension JAR.
6. Run `seed4j extension install <jar> --distribution-id <id> --distribution-version <version>`.
7. Validate with `seed4j --version` and `seed4j list`.

Minimal module resource example:

```java
@Configuration
public class MyExtensionModuleConfiguration {

  @Bean
  Seed4JModuleResource myExtensionModule(MyExtensionApplicationService applicationService) {
    return Seed4JModuleResource.builder()
      .slug(MyExtensionModuleSlug.MY_EXTENSION_MODULE)
      .withoutProperties()
      .apiDoc("Runtime", "My extension module")
      .standalone()
      .tags("runtime", "extension")
      .factory(applicationService::buildModule);
  }
}
```

Minimal metadata example:

```yaml
distribution:
  id: my-company-extension
  version: 1.0.0
```

Important notes:

- `distribution.id` and `distribution.version` are mandatory.
- Avoid shipping unintended overrides (for example, `config/application.yml`) unless you intentionally want to change core behavior.

Install the built artifact with concrete distribution metadata:

```bash
seed4j extension install target/<your-extension-artifact>.jar --distribution-id my-company-extension --distribution-version 1.0.0
```

You can then switch runtime modes explicitly:

```bash
seed4j extension enable
seed4j extension disable
```

Validate runtime activation after installation or mode changes:

```bash
seed4j --version
seed4j list
```

Before installing, check the normative [runtime metadata](Commands.md#extension-runtime-metadata) and [validation failures](Commands.md#runtime-validation-and-failure-cases). Then use the exact [extension install procedure](Commands.md#install-a-runtime-extension).
