# Main Flow Resume: Real Two-Stage Bootstrap Without Main Overloads

## Summary

Finish the runtime-switching feature by making the production entrypoint use the real two-stage bootstrap, but stop growing `Seed4JCliApp.main(...)` as a test seam.

This next slice must improve production behavior and reduce bootstrap complexity at the same time:

- `Seed4JCliApp` becomes a thin composition root again
- the two-stage bootstrap remains in the `bootstrap` package, not in `main(...)` overload chains
- local Spring startup becomes an explicit runner type
- child JVM execution becomes an explicit process launcher type
- launcher orchestration is split into smaller responsibilities instead of concentrating more logic in one class

## Key Changes

### 1. Collapse the `main(...)` entrypoint

- Remove the overload-based `main(...)` chain used only to inject test doubles.
- Keep:
  - the public `main(String[] args)` entrypoint
  - at most one package-private seam for tests if still needed
- `Seed4JCliApp.main(...)` must only:
  - create the bootstrap dependencies
  - invoke the bootstrap entrypoint
  - delegate process exit to a single exit handler
- Do not keep `EntryPointLauncherFactory`, `MainDependencies`, `MainDependenciesFactory`, `PublicMainDependencies`, or `PublicMainDependenciesFactory`.
- Tests must stop asserting delegation through factory layers that do not exist in production.

### 2. Introduce explicit bootstrap types instead of widening `Seed4JCliLauncher`

- Keep `Seed4JCliLauncher` as the orchestration point, but remove responsibilities that deserve dedicated types.
- Extract explicit collaborators for the bootstrap concerns already visible in the current code:
  - a local Spring runner that owns `SpringApplicationBuilder` creation and external config loading
  - a runtime mode/config reader that inspects `~/.config/seed4j-cli.yml`
  - a child-process request launcher that executes the JVM command
  - a CLI version resolver used before Spring starts
- `Seed4JCliLauncher` should decide only:
  - whether the current process is already the child process
  - whether execution stays local or relaunches
  - which `RuntimeSelection` is active
  - which collaborator to invoke next
- `Seed4JCliLauncher` should stop parsing YAML directly and stop building Spring locally by itself.

### 3. Make the local Spring path a first-class runner

- Add a dedicated runner type for the in-process Spring execution path.
- Move the current logic from `Seed4JCliApp` into that runner:
  - create the `SpringApplicationBuilder`
  - disable banner
  - set `WebApplicationType.NONE`
  - enable lazy initialization
  - load `~/.config/seed4j-cli.yml` through `spring.config.location` when present
  - return the exit code from `SpringApplication.exit(...)`
- This runner is the only place that knows how to boot the local CLI application.
- The parent bootstrap must use this runner only when:
  - `seed4j.cli.runtime.child=true`
  - or execution is outside a regular JAR and the selected mode is `standard`

### 4. Make the child JVM execution concrete

- Add a production `ChildProcessLauncher` implementation that executes the `JavaChildProcessRequest`.
- Use `${java.home}/bin/java` as the executable.
- Build the command as:
  - `java`
  - one `-Dkey=value` per request system property
  - `-cp <current-boot-jar>`
  - `org.springframework.boot.loader.launch.PropertiesLauncher`
  - original CLI args
- Inherit stdio and return the child exit code.
- Fail fast with a clear bootstrap error when child-process handoff is required but the current executable location is not a regular JAR.

### 5. Keep the execution-layout policy explicit

- Packaged JAR execution is the only supported path for the real two-stage bootstrap.
- Outside a regular JAR:
  - `standard` runs the local Spring runner in the same process
  - `extension` fails before Spring with a clear error saying that extension mode requires the packaged CLI JAR
- Do not silently downgrade `extension` to `standard`.
- Keep the child-side runtime reconstruction contract based on system properties exactly as it is now.

### 6. Resolve the current CLI version without depending on Spring bootstrapping

- Resolve the launcher-side CLI version before Spring starts.
- Do not make `Seed4JCliLauncher` read `config/application.yml` directly.
- Introduce a dedicated resolver type for the current CLI version and let the launcher depend on that abstraction.
- The resolver may read the filtered classpath resource if needed, but the file parsing and failure behavior belong to the resolver, not to the launcher.
- If the current version cannot be resolved, fail fast with a clear bootstrap error instead of inventing a fallback version.

## Tests

- Replace the current `Seed4JCliApp` delegation tests with a much smaller entrypoint test surface:
  - one test proving the entrypoint forwards args to the bootstrap entrypoint and exits with the returned code
  - no tests for factory/dependency indirection that no longer exists
- Keep launcher tests focused on behavior:
  - child mode runs the local Spring runner
  - standard mode outside a regular JAR runs locally and emits the warning
  - extension mode outside a regular JAR fails before Spring
  - packaged execution relaunches through the child-process launcher
- Add tests for the concrete child-process launcher command in both `standard` and `extension`:
  - java executable path
  - required runtime system properties
  - classpath set to the current JAR
  - `PropertiesLauncher`
  - original args
- Add tests for the extracted collaborators:
  - runtime config reader handles valid and invalid YAML shapes
  - local Spring runner applies external config loading rules
  - CLI version resolver fails clearly when the version cannot be read
- Keep the existing runtime-selection/provider tests unchanged unless extraction requires moving them.

## Assumptions and Defaults

- `PropertiesLauncher` remains the official child main class.
- The child-process handoff continues to use system properties, not environment variables or temporary files.
- `loader.path` stays in system properties, not a separate CLI option.
- IDE/class-directory execution remains intentionally limited:
  - supported for `standard`
  - unsupported for `extension`
- This slice stops at real JVM relaunch plus bootstrap simplification; it does not add new diagnostic commands or broaden support for non-JAR extension execution.
