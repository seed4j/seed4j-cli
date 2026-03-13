# Runtime Bootstrap Next Slice

## Goal

Finish the runtime-switching feature so production startup uses the real two-stage bootstrap, with `Seed4JCliApp` as a thin composition root.

## Current Status (2026-03-13)

### Done

- `Seed4JCliApp` no longer has factory/dependency indirection layers.
- Local startup logic was extracted to `LocalSpringCliRunner`.
- Child-process command assembly was extracted to `JavaProcessChildLauncher`.
- Runtime mode parsing of `~/.config/seed4j-cli.yml` was extracted to `RuntimeModeConfigReader`.
- `Seed4JCliLauncher` now delegates runtime mode parsing to `RuntimeModeConfigReader` (launcher no longer parses YAML directly).
- Existing behavior tests for launcher policy are still green.

### Missing

- `Seed4JCliApp.main(String[] args)` still starts Spring directly and does not use `Seed4JCliLauncher`.
- `JavaProcessChildLauncher` still does not execute real child processes with `ProcessBuilder`, `inheritIO`, and `${java.home}/bin/java` by default.
- CLI version resolution is still a plain string input to the launcher; there is no dedicated resolver type yet.
- Concrete child-launcher tests still need full coverage for extension-mode command expectations.

## Non-Negotiable Rules

- Packaged JAR execution is the only supported path for real two-stage bootstrap.
- Outside a regular JAR:
  - `standard` runs local in-process startup.
  - `extension` fails before Spring with a clear error.
- Never downgrade `extension` to `standard` silently.
- Child-process handoff contract remains system-properties based.

## Next Implementation Steps (Ordered)

### 1. Wire production entrypoint to bootstrap launcher

- Make `Seed4JCliApp.main(String[] args)` build bootstrap dependencies and delegate to launcher entrypoint.
- Keep process exit centralized in one exit handler.
- Preserve existing lightweight entrypoint tests.

### 2. Finalize concrete child JVM execution

- Make `JavaProcessChildLauncher` run `JavaChildProcessRequest` via `ProcessBuilder`.
- Use `${java.home}/bin/java` as default executable.
- Build command with:
  - `-Dkey=value` properties from request
  - `-cp <current-boot-jar>`
  - `org.springframework.boot.loader.launch.PropertiesLauncher`
  - original CLI arguments
- Inherit stdio and return child exit code.

### 3. Extract CLI version resolver

- Introduce a dedicated type responsible for obtaining current CLI version before Spring startup.
- Move version-reading/parsing failures to that type.
- Fail fast with a clear bootstrap error when version cannot be resolved.

### 4. Keep orchestration boundaries explicit

- `Seed4JCliLauncher` should only decide:
  - child or parent execution path
  - local or relaunch strategy
  - selected runtime mode
  - collaborator invocation
- Do not move Spring bootstrapping logic back into launcher.

## Test Backlog

- Entry point:
  - verify production `main(String[] args)` delegates to bootstrap launcher and exits with launcher code.
- Child process launcher:
  - standard mode command composition and execution path.
  - extension mode command composition including `loader.path` and distribution properties.
- CLI version resolver:
  - success path for filtered version.
  - clear failure when version is missing/invalid.

## Slice Boundary

This slice ends when production startup uses the launcher flow with real JVM handoff and extracted collaborators, without broadening support for extension execution outside packaged JAR.
