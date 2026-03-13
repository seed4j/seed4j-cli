# Main Flow Resume: Real Two-Stage Bootstrap

## Summary

Finish the runtime-switching feature by wiring the real application entrypoint to the launcher and by executing the child JVM for real.

This next slice must make the current work actually drive production behavior:

- `Seed4JCliApp.main(...)` stops booting Spring directly
- the parent process uses `Seed4JCliLauncher`
- the launcher spawns a child JVM through `PropertiesLauncher` when running from a packaged JAR
- the child process reconstructs the active runtime from system properties, which is already implemented
- IDE/class-directory execution remains usable for `standard`, but `extension` fails fast outside a bootable JAR

## Key Changes

### 1. Entry point and local runner

- Change `Seed4JCliApp.main(...)` to delegate to `Seed4JCliLauncher` instead of starting Spring immediately.
- Keep the existing Spring boot path as a dedicated local runner used only when:
  - `seed4j.cli.runtime.child=true`
  - or the process is not running from a regular JAR and the selected mode is `standard`
- Keep the current external config loading behavior inside that local Spring path, not in the parent bootstrap.

### 2. Concrete child-process execution

- Add a production `ChildProcessLauncher` implementation that executes the `JavaChildProcessRequest` with the current Java runtime.
- Use `${java.home}/bin/java` as the executable.
- Build the child command as:
  - `java`
  - one `-Dkey=value` per request system property
  - `-cp <current-boot-jar>`
  - `org.springframework.boot.loader.launch.PropertiesLauncher`
  - original CLI args
- Inherit stdio and return the child exit code.
- Fail fast with a clear bootstrap error if the current executable location is not a regular JAR but the selected mode requires child-process handoff.

### 3. Execution-layout policy

- Packaged JAR execution is the only supported path for the real two-stage bootstrap.
- Outside a regular JAR:
  - `standard` bypasses to the local Spring path in the same process
  - `extension` fails before Spring with a clear error saying that extension mode requires running the packaged CLI JAR
- Do not silently downgrade `extension` to `standard`.

### 4. Current CLI version in the launcher

- Resolve the launcher-side CLI version without Spring.
- Read `project.version` from the filtered classpath resource `config/application.yml`.
- Use that value as the input to `RuntimeSelection.resolve(...)`.
- If that value cannot be resolved, fail fast with a clear bootstrap error instead of inventing a fallback version.

## Tests

- Add a launcher-level test for the concrete child-process command in `standard`:
  - java executable path
  - `-Dseed4j.cli.runtime.child=true`
  - `-Dseed4j.cli.runtime.mode=standard`
  - classpath set to the current JAR
  - `PropertiesLauncher`
  - original args
- Add the equivalent command test for `extension`, including:
  - `loader.path`
  - `seed4j.cli.runtime.distribution.id`
  - `seed4j.cli.runtime.distribution.version`
- Add a launcher test for non-JAR execution:
  - `standard` runs the local path
  - `extension` fails fast before Spring
- Add an entrypoint-focused test that `Seed4JCliApp.main(...)` routes through the launcher and respects `seed4j.cli.runtime.child=true`.
- Keep the existing runtime-selection/provider tests unchanged; they already cover the handoff contract and child-side reconstruction.

## Assumptions and Defaults

- `PropertiesLauncher` remains the official child main class.
- The child-process request keeps using system properties for runtime handoff; no environment variables or temp files.
- `loader.path` stays in system properties, not a separate CLI option.
- IDE support is intentionally limited:
  - good enough for `standard`
  - explicitly unsupported for `extension`
- This slice stops at real JVM relaunch plus entrypoint wiring; it does not add new diagnostic commands or broaden support for non-JAR extension execution.
