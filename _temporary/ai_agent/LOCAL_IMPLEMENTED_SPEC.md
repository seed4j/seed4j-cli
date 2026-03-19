# Local Implemented Spec

Local-only file. Ignored by Git on purpose.

Updated on 2026-03-19.

## Goal of this file

This document records what is already implemented in the repository for the Seed4J CLI runtime-selection work.

It is intentionally not a future-state handoff. It describes the code as it exists now, including important gaps.

## Current implemented baseline

### Existing CLI behavior already preserved

- `list` and `apply` remain derived from the runtime modules exposed by the active Spring context.
- `apply --help` and `apply <module> --help` remain dynamic.
- `--project-path`, `--[no-]commit`, mandatory parameter validation, and parameter reuse remain covered by the existing codebase and tests.
- External config loading for the current Spring runtime still happens from `~/.config/seed4j-cli.yml`.
- `seed4j.hidden-resources` is the effective namespace used by the current baseline and integration tests.

### Runtime selection domain implemented

The repository already contains a runtime-selection domain in `src/main/java/com/seed4j/cli/bootstrap`:

- [`RuntimeMode`](/home/renanfranca/projects/seed4j-cli/src/main/java/com/seed4j/cli/bootstrap/RuntimeMode.java) with `STANDARD` and `EXTENSION`
- [`RuntimeConfiguration`](/home/renanfranca/projects/seed4j-cli/src/main/java/com/seed4j/cli/bootstrap/RuntimeConfiguration.java)
- [`RuntimeExtensionConfiguration`](/home/renanfranca/projects/seed4j-cli/src/main/java/com/seed4j/cli/bootstrap/RuntimeExtensionConfiguration.java)
- [`RuntimeSelection`](/home/renanfranca/projects/seed4j-cli/src/main/java/com/seed4j/cli/bootstrap/RuntimeSelection.java)
- [`RuntimeMetadata`](/home/renanfranca/projects/seed4j-cli/src/main/java/com/seed4j/cli/bootstrap/RuntimeMetadata.java)
- [`CliVersion`](/home/renanfranca/projects/seed4j-cli/src/main/java/com/seed4j/cli/bootstrap/CliVersion.java)
- [`InvalidRuntimeConfigurationException`](/home/renanfranca/projects/seed4j-cli/src/main/java/com/seed4j/cli/bootstrap/InvalidRuntimeConfigurationException.java)

### What `RuntimeSelection.resolve(...)` already enforces

When runtime mode is `STANDARD`:

- selection resolves to standard runtime
- no extension jar is propagated
- no distribution id/version is propagated

When runtime mode is `EXTENSION`:

- metadata file must exist
- extension jar must exist
- `distribution.id` is mandatory
- `distribution.version` is mandatory
- `compatibility.min-cli-version` is optional
- when `compatibility.min-cli-version` is present, it is treated as a minimum compatible CLI version
- malformed compatibility versions fail as `InvalidRuntimeConfigurationException`
- malformed current CLI versions fail as `InvalidRuntimeConfigurationException` only when `compatibility.min-cli-version` is present
- `distributionId` and `distributionVersion` are exposed in the resolved selection
- legacy metadata extras are ignored:
  - `distribution.kind`
  - `distribution.vendor`
  - `artifact.filename`
  - `compatibility.cli`

### Extension filesystem contract currently implemented

The current code already supports default extension paths rooted at the user home passed to the launcher:

```text
~/.config/seed4j-cli/runtime/active/extension.jar
~/.config/seed4j-cli/runtime/active/metadata.yml
```

This is implemented via [`RuntimeExtensionConfiguration.withDefaultPaths(...)`](/home/renanfranca/projects/seed4j-cli/src/main/java/com/seed4j/cli/bootstrap/RuntimeExtensionConfiguration.java).

## Launcher behavior already implemented

[`Seed4JCliLauncher`](/home/renanfranca/projects/seed4j-cli/src/main/java/com/seed4j/cli/bootstrap/Seed4JCliLauncher.java) already exists and is covered by tests.

### Behaviors currently implemented in the launcher

- no external config file means standard runtime
- child mode short-circuits to the local runner instead of launching another child process
- explicit `seed4j.runtime.mode: standard` means standard runtime
- explicit `seed4j.runtime.mode: extension` triggers extension validation through `RuntimeSelection.resolve(...)`
- valid extension config launches the abstract child-process contract with `mode`, `distributionId`, and `distributionVersion`
- invalid extension config returns non-zero before child process launch

### YAML behaviors already implemented in the launcher

If `~/.config/seed4j-cli.yml` exists:

- missing `seed4j` key falls back to standard
- missing `seed4j.runtime` falls back to standard
- missing `seed4j.runtime.mode` falls back to standard
- invalid YAML root fails
- invalid `seed4j` type fails
- invalid `seed4j.runtime.mode` type fails
- invalid `seed4j.runtime.mode` value fails
- unreadable config fails

### Current launcher contract limitation

The launcher currently delegates to an abstract [`ChildProcessLauncher`](/home/renanfranca/projects/seed4j-cli/src/main/java/com/seed4j/cli/bootstrap/ChildProcessLauncher.java) interface.

That means the code currently has pre-bootstrap selection and validation, but not yet the real Java child-process implementation.

## `--version` work already implemented

[`Seed4JCommandsFactory`](/home/renanfranca/projects/seed4j-cli/src/main/java/com/seed4j/cli/command/infrastructure/primary/Seed4JCommandsFactory.java) now knows how to print:

- CLI version
- Seed4J version
- runtime mode
- distribution id
- distribution version

This behavior is already covered by tests.

## Important gaps still open

These points are not implemented yet and must not be described as done:

### Main entrypoint is still not using the launcher

[`Seed4JCliApp`](/home/renanfranca/projects/seed4j-cli/src/main/java/com/seed4j/cli/Seed4JCliApp.java) still boots Spring directly in the current process.

It still:

- references the fixed Spring bootstrap path
- loads `~/.config/seed4j-cli.yml` directly into Spring config
- does not delegate to `Seed4JCliLauncher`

So the two-stage bootstrap exists in domain and tests, but is not yet wired into the real application entrypoint.

### Real Java child-process bootstrap is still missing

Not implemented yet:

- `ProcessBuilder`-based Java child process launch
- `PropertiesLauncher`
- `loader.path`
- propagation of `seed4j.cli.runtime.child=true`
- propagation of runtime mode/distribution info through JVM system properties
- stdio/exit-code propagation from a real child JVM

### Runtime identity inside Spring is not yet sourced from the launcher

[`StandardRuntimeSelectionProvider`](/home/renanfranca/projects/seed4j-cli/src/main/java/com/seed4j/cli/command/infrastructure/primary/StandardRuntimeSelectionProvider.java) still always returns standard runtime.

So although `Seed4JCommandsFactory` can print runtime identity, the real running application still receives standard runtime identity from the provider.

### Metadata contract is narrower than the revised handoff

Currently implemented metadata fields are:

- `distribution.id`
- `distribution.version`
- `compatibility.min-cli-version` (optional)

Accepted as legacy extras with no runtime effect:

- `distribution.vendor`
- `distribution.kind`
- `artifact.filename`
- `compatibility.cli`

Not implemented yet from the revised handoff:

- `distribution.runtime-version`
- `distribution.bootstrap-class`
- `distribution.runtime-contract-version`

### Runtime mode naming is still `standard`, not `base`

The current code and tests use:

- `standard`
- `extension`

The revised handoff text that uses `base` has not been implemented in code.

### `fail-on-invalid-extension` is not implemented

The launcher currently behaves as fail-fast for invalid extension environments.

There is no implemented parsing or branching for:

- `seed4j.runtime.fail-on-invalid-extension`

## Effective implemented schema today

This is the schema actually reflected by the code today, not the future-state schema:

```yaml
seed4j:
  runtime:
    mode: standard | extension
  hidden-resources:
    slugs: []
    tags: []
```

Behavior of the current code:

- config file absent: standard
- config file present with no `seed4j`: standard
- config file present with no `seed4j.runtime`: standard
- config file present with no `seed4j.runtime.mode`: standard
- config file present with malformed structure: fail before child-process handoff

## Test coverage already added in this work

The current repository already contains focused tests for:

- runtime selection semantics in [`RuntimeSelectionTest`](/home/renanfranca/projects/seed4j-cli/src/test/java/com/seed4j/cli/bootstrap/RuntimeSelectionTest.java)
- launcher pre-bootstrap decisions in [`Seed4JCliLauncherTest`](/home/renanfranca/projects/seed4j-cli/src/test/java/com/seed4j/cli/bootstrap/Seed4JCliLauncherTest.java)
- `--version` formatting in [`Seed4JCommandsFactoryTest`](/home/renanfranca/projects/seed4j-cli/src/test/java/com/seed4j/cli/command/infrastructure/primary/Seed4JCommandsFactoryTest.java)

## Practical interpretation

What is real today:

- runtime-selection domain exists
- extension metadata and compatibility validation exist
- launcher decision logic exists
- launcher validation rules are tested
- version output shape exists

What is still missing for the full feature:

- main app delegated to launcher
- real child JVM bootstrap
- dynamic runtime identity inside the real Spring process
- extension bootstrap class handoff
- extension runtime-contract handshake
