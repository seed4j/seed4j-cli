# Seed4J CLI Commands

This document provides an overview of the Seed4J CLI commands available in this project.

## Table of Contents

- [Getting Started](#getting-started)
- [Basic Commands](#basic-commands)
  - [Version](#version)
  - [List Available Modules](#list-available-modules)
  - [Apply a Module](#apply-a-module)
  - [Apply a Module Set](#apply-a-module-set)
  - [Bash Completion](#bash-completion)
  - [Install a Runtime Extension](#install-a-runtime-extension)
  - [Enable a Runtime Extension](#enable-a-runtime-extension)
  - [Disable a Runtime Extension](#disable-a-runtime-extension)
- [Project Creation Workflow Example](#project-creation-workflow-example)
- [Options and Parameters](#options-and-parameters)
  - [Parameters Reuse](#parameter-reuse)
- [External Configuration](#external-configuration)
  - [Hidden Resources](#hidden-resources)
  - [Runtime Mode](#runtime-mode)
  - [Extension Runtime Metadata](#extension-runtime-metadata)
  - [Extension Mode Behavior](#extension-mode-behavior)
  - [Runtime Validation and Failure Cases](#runtime-validation-and-failure-cases)
  - [Creating a Seed4J Extension](#creating-a-seed4j-extension)

## Getting Started

To use Seed4J CLI, make sure it's installed and available in your PATH. You can check if it's properly installed with:

```bash
seed4j --version
```

## Basic Commands

### Version

To check the Seed4J CLI version:

```bash
seed4j --version
```

This command displays:

- Seed4J CLI version
- Seed4J version
- Active runtime mode (`standard` or `extension`)
- Active distribution ID, in extension mode only
- Active distribution version, in extension mode only

Example output in `standard` mode:

```text
Seed4J CLI v0.0.1-SNAPSHOT
Seed4J version: 2.2.0
Runtime mode: standard
```

Example output in `extension` mode:

```text
Seed4J CLI v0.0.1-SNAPSHOT
Seed4J version: 2.2.0
Runtime mode: extension
Distribution ID: company-extension
Distribution version: 1.0.0
```

### List Available Modules

To see all available modules that can be applied to your project:

```bash
seed4j list
```

This command displays all available modules using three columns:

- `Module`: module slug
- `Dependencies`: typed dependencies from the visible catalog (`module:<slug>` and `feature:<slug>`)
- `Description`: module operation description

When a module has no dependencies, `Dependencies` displays `-`.

When a `module:<slug>` dependency target is not visible in the current catalog, the token is marked with ` (hidden)`.

`Dependencies` column rendering rules:

- natural width is the largest dependencies cell (or the header width)
- effective width is `min(naturalWidth, 60)`
- wrapping is deterministic with no truncation
- continuation lines do not repeat `Module` or `Description`

In `extension` mode, extension-provided modules are added to the standard catalog.

### Apply a Module

To apply a specific module to your project:

```bash
seed4j apply <module-name> [options]
```

Most modules require specific parameters. If you miss required parameters, the CLI will inform you which ones are missing.

Before normal apply validates those parameters or generates files, it calculates the target module's complete dependency
plan. Any pending direct module, transitive module, or feature dependency blocks the command with exit code 2. The
diagnostic is written to stderr, includes only pending dependencies, and leaves generated files, project history, and Git
commits unchanged. Apply every pending module and one visible provider from each pending feature choice, then retry the
requested module.

For example, after `init` is recorded but `prettier` is not, applying `angular-core` is blocked:

```bash
seed4j apply angular-core --package-name com.mycompany.myapp
```

```text
Cannot apply module: angular-core

Missing required dependencies:

○ module:prettier - pending

Next action: apply every pending module and one module from each pending choice, then retry this module.
No changes were applied.
```

The dependency check has precedence over required options. If `--package-name` is also omitted in this example, the same
dependency diagnostic appears first. Once dependencies are ready, the existing required-parameter validation runs
normally.

To see the specific parameters for a module and which one is required, run:

```bash
seed4j apply <module-name> --help
```

To inspect the values Seed4J would use without applying the module, add `--plan`:

```bash
seed4j apply init --plan
```

The plan is text-only and exits without generated files, history entries, or commits. Required parameters can come from
the current command or project history. When required parameters are still missing, the plan exits successfully and shows
which options to pass before applying the module. Lines marked with `✓` are resolved, and lines marked with `○` are
pending or missing.

Unlike normal apply, `--plan` never blocks on pending dependencies: it remains read-only, returns exit code 0, and renders
both resolved and pending dependency lines together with parameter resolution.

```text
Plan for module: init
Project path: .

Dependency plan:

✓ No dependencies.

Resolved parameters:

✓ endOfLine: lf
  Source: default
  CLI option: --end-of-line

✓ indentSize: 2
  Source: default
  CLI option: --indent-size

Missing required parameters:

○ projectName:
  CLI option: --project-name
  Note: pass this option or apply a module that records it in project history.

○ baseName:
  CLI option: --base-name
  Note: pass this option or apply a module that records it in project history.

○ nodePackageManager:
  CLI option: --node-package-manager
  Note: pass this option or apply a module that records it in project history.

No changes were applied.
```

Pass the missing options when applying the module:

```bash
seed4j apply init --project-name "Seed4J Sample Application" --base-name seed4jSampleApplication --node-package-manager npm
```

Planning `maven-java` shows the values already selected by project history and the remaining required option:

```bash
seed4j apply maven-java --plan
```

```text
Plan for module: maven-java
Project path: .

Dependency plan:

✓ module:init - already applied

Resolved parameters:

✓ projectName: Seed4J Sample Application
  Source: project history
  CLI option: --project-name
  Note: already selected by project history; omit this option to keep it.

✓ baseName: seed4jSampleApplication
  Source: project history
  CLI option: --base-name
  Note: already selected by project history; omit this option to keep it.

Missing required parameters:

○ packageName:
  CLI option: --package-name
  Note: pass this option or apply a module that records it in project history.

No changes were applied.
```

Pass the missing option when applying the module:

```bash
seed4j apply maven-java --package-name com.mycompany.myapp
```

Dependency status labels mean:

- `✓ module:<slug> - already applied`: the module dependency is already recorded in project history
- `○ module:<slug> - pending`: the module dependency is not recorded in project history
- `✓ feature:<slug> - satisfied by <module>`: an applied module provides the required feature
- `○ feature:<slug> - pending choice: <candidate>, <candidate>`: no applied module provides the feature yet; choose one of
  the sorted visible candidates

For example, after applying `maven-java`, planning `sonarqube-java-backend` can show one satisfied feature and one feature
that still needs a choice:

```text
Dependency plan:

✓ feature:java-build-tool - satisfied by maven-java
○ feature:code-coverage-java - pending choice: jacoco, jacoco-with-min-coverage-check
```

Normal apply renders only the missing feature and blocks until one of its alphabetically ordered visible providers is in
project history:

```bash
seed4j apply sonarqube-java-backend
```

```text
Cannot apply module: sonarqube-java-backend

Missing required dependencies:

○ feature:code-coverage-java - pending choice: jacoco, jacoco-with-min-coverage-check

Next action: apply every pending module and one module from each pending choice, then retry this module.
No changes were applied.
```

After applying either `jacoco` or `jacoco-with-min-coverage-check`, the feature is satisfied by the module recorded in
history and normal application of `sonarqube-java-backend` can continue.

Plan source labels mean:

- `explicit CLI input`: the option was passed in the current command
- `project history`: the value came from the latest saved project parameters
- `default`: the module metadata defines a display default and no explicit or historical value exists

Project-history values include a note telling callers they can omit that option to keep the remembered value. Defaults in
the plan are informational; they are not injected into normal `apply` parameters. JSON output and `--format` are not part
of this text-only phase.

<a id="plan-a-module-set"></a>

### Apply a Module Set

Use `apply-set` to validate and sequentially apply an explicitly selected set of visible modules:

```bash
seed4j apply-set init maven-java \
  --project-path . \
  --project-name "Sample application" \
  --base-name sampleApplication \
  --node-package-manager npm \
  --package-name com.mycompany.sample
```

The command requires at least one visible module slug. `--project-path` defaults to `.`, and the command accepts the union
of property options declared by visible modules. Unknown, hidden, and duplicate slugs invalidate preflight. Omitting
`--plan` executes after a valid preflight; adding `--plan` prints a fresh read-only snapshot and exits without authorizing
or reserving a later execution.

Before invoking any module, `apply-set` completes a read-only preflight. It validates the practical writability of the
project path without creating it or using a write probe, requires the Seed4J landscape order to contain exactly the
requested modules, checks dependencies and feature providers, reconciles property definitions, reads compatible history
values, and reports every predictable problem together. An invalid preflight writes its complete report only to
`stderr`, leaves `stdout` empty, ends with `No changes were applied.`, and exits with code 2.

Every physically existing path component must resolve to a directory. A regular file or broken symbolic link at the
destination invalidates it as a non-directory; either one in an intermediate component makes the destination not
apparently creatable. A symbolic link that resolves to an accessible, writable directory remains valid. These checks do
not create the destination, a missing link target, or a write probe.

A predictably invalid path stops every project-dependent stage before history is read. Duplicate, unknown-module, or
execution-order problems already calculated remain in the invalid plan, but dependency and parameter sections remain
`NOT_EVALUATED` and Git is not inspected. They render as `Dependency validation: (not evaluated)` and
`Resolved parameters: (not evaluated)`. `✓ No dependencies.` and `(none)` only describe evaluated empty results. This
makes the path diagnostic authoritative instead of allowing a history-read failure from an unusable destination to
replace it.

The output separates `Requested modules` from `Execution order`. Requested order preserves the command line. Execution
order is calculated exclusively by the Seed4J landscape and is the exact order used by the same invocation. A module
already recorded in the preflight history remains in this order, is invoked again, and is annotated `(reapplied)` in the
plan, progress, and summary. History can satisfy dependencies but never erases explicit execution intent.

Dependencies are discovered recursively, rendered once, and annotated with every requested module that requires them. A
module dependency is satisfied by project history or by an explicitly selected module ordered before the dependent. A
feature dependency is satisfied by a visible provider in history or by an explicitly selected provider ordered before the
dependent. The planner never selects a provider implicitly. When a provider is absent, it lists visible candidates in
alphabetical order so the caller can add one explicitly.

Selected property definitions are reconciled by key and shown once. A key is a global catalog concept and has exactly one
type across all visible modules; `STRING`, `INTEGER`, and `BOOLEAN` definitions for the same key cannot be mixed. The
official Seed4J catalog validates this before exposing the single global Picocli option. A nonconforming alternate
catalog makes selected conflicting types an invalid preflight, regardless of module order. The conflict lists types
deterministically, the key is not also reported as an unused option, and no explicit input, history value, or default is
resolved for it. Other keys continue to be validated and aggregated.

The catalog is assembled from resources, landscape, and extensions before the Picocli tree is created and remains stable
for the planner's lifetime. Each CLI process executes one command, so `apply-set` does not snapshot a changing catalog or
support catalog hot reload.

A property is mandatory when any selected module makes it mandatory, and at most one distinct default and description
may remain. Compatible default and description conflicts retain the existing behavior. Display order follows the
property's first occurrence in execution order and then its declaration order. Values resolve in this order:

1. explicit CLI input;
2. latest project history value;
3. metadata default for display only.

Defaults for mandatory properties remain informational and do not satisfy the requirement. Picocli converts explicit CLI
input directly to the global option's declared type and rejects invalid typed values before planning. Programmatic
callers must likewise provide correctly typed domain values. A value that differs from the reconciled type violates an
internal invariant and throws a dedicated domain exception before resolution; it is not a correctable preflight problem
or a CLI diagnostic. Only explicit and compatible history values enter the immutable effective parameter map sent
unchanged to every individual module call; display defaults are never sent. Passing a known property option that none of
the selected modules uses invalidates the plan.

A project-history value is reused only when its stored type matches the selected property type. A relevant mismatch
invalidates the plan without falling back to a default or also reporting the property as missing. The diagnostic names
the expected and stored types and tells the caller which explicit option overrides the stored value; a correctly typed
explicit value therefore takes precedence over incompatible history. History keys unused by the selected modules remain
irrelevant and do not invalidate the plan. History values outside Seed4J's `STRING`, `INTEGER`, and `BOOLEAN` variants are
reported as an unsupported value type when their key is relevant.

The planner aggregates all predictable post-resolution problems, including recursive dependencies, feature choices,
property conflicts, irrelevant options, history type mismatches, and every missing required parameter. A history-read
exception is an unexpected pre-execution failure: the CLI does not inspect or expose it, invokes no module, prints a
generic message to `stderr`, and exits with code 1.

Add `--plan` to stop after the valid snapshot:

```bash
seed4j apply-set init maven-java \
  --project-name "Sample application" \
  --base-name sampleApplication \
  --node-package-manager npm \
  --package-name com.mycompany.sample \
  --plan
```

```text
Preflight: VALID
Plan for module set

Project path: .

Requested modules:
  1. init
  2. maven-java

Execution order:
  1. init
  2. maven-java

Dependency validation:
  ✓ module:init - satisfied by requested module: init; required by: maven-java

Resolved parameters:
  ✓ projectName: Sample application
    Source: explicit CLI input
    CLI option: --project-name
  ...
  ✓ endOfLine: lf
    Source: default (informational)
    CLI option: --end-of-line

Commit mode: one commit per succeeded module

Status: VALID
No changes were applied.
```

Execution is sequential, non-atomic, and has no automatic rollback. The CLI calls the Seed4J individual-module API once
per planned item. `SUCCEEDED` means the individual call, including synchronous event dispatch, returned normally. At the
first thrown call, that module is `FAILED`, every later module is `SKIPPED`, earlier successes are preserved, and the
overall result is `PARTIAL_FAILURE`. Effects of the failed module are indeterminate; inspect the working tree, project
history, and relevant external event effects before deciding whether to retry. Inspect Git log as well when commits were
enabled; `--no-commit` failure guidance does not mention Git or Git log.

Commits are enabled by default. In this mode the individual flow initializes Git when needed and completes one commit per
`SUCCEEDED` module. `--no-commit` disables both Git initialization and commits while files, history, and events continue
normally. When commits are enabled for an existing dirty worktree, a warning on `stderr` explains that module commits can
include or be affected by pre-existing changes. Execution confirms that it continues automatically. `--plan` instead
says that commits in a later execution can be affected and explicitly confirms that the current plan is read-only and no
modules will be applied. `--plan --no-commit` performs no Git inspection and emits no Git warning.

The execution output starts with a compact preflight intended for agents: execution order (including `(reapplied)`),
effective parameters with their sources and CLI options, and commit mode. It omits requested modules, dependency
validation, informational defaults, and the detailed `Status: VALID` footer. Informational defaults are display-only in
`--plan`; they never enter this effective section or any call to the Seed4J core. Progress and a complete final summary
follow immediately:

```text
Preflight: VALID
Execution order:
  1. init
  2. maven-java

Effective parameters:
  ✓ projectName: Sample application
    Source: explicit CLI input
    CLI option: --project-name
  ✓ baseName: sampleApplication
    Source: explicit CLI input
    CLI option: --base-name
  ✓ nodePackageManager: npm
    Source: explicit CLI input
    CLI option: --node-package-manager
  ✓ packageName: com.mycompany.sample
    Source: explicit CLI input
    CLI option: --package-name

Commit mode: one commit per succeeded module

Applying module set:
[1/2] init
      Status: SUCCEEDED
      History: updated
      Events: dispatched
      Commit: created
[2/2] maven-java
      Status: SUCCEEDED
      History: updated
      Events: dispatched
      Commit: created

Summary:
  init  SUCCEEDED
  maven-java  SUCCEEDED
Module set status: SUCCEEDED
```

Exit codes are stable for scripts and agents:

- `0`: a valid `--plan`, or every module succeeded;
- `2`: command usage or predictable preflight validation failed, with no module or Git mutation; and
- `1`: an unexpected pre-execution failure or an execution ending in `PARTIAL_FAILURE`.

`apply-set` is stricter than the existing single-module plan. `seed4j apply <module> --plan` remains informational and
returns 0 with pending dependencies or parameters, while `seed4j apply-set ... --plan` returns 2 whenever the combined
plan is not ready. Existing `seed4j apply <module>` behavior is unchanged. Presets, JSON output, parallel execution,
automatic dependency/provider selection, and rollback remain outside this command.

### Bash Completion

To print a Bash completion script for the active runtime:

```bash
seed4j completion bash
```

Install it for the current user:

```bash
seed4j completion bash --install
source ~/.local/share/bash-completion/completions/seed4j
```

The `source` command loads completion in the terminal session that is already open. Without it, open a new terminal so Bash loads the generated script during startup.

To inspect or install the script manually, redirect the generated output:

```bash
mkdir -p ~/.local/share/bash-completion/completions
seed4j completion bash > ~/.local/share/bash-completion/completions/seed4j
```

The script completes available command names, nested subcommands, `apply` module slugs, option names, negated option names such as `--no-commit`, and static option value candidates from CLI metadata, known Seed4J module property values, and module default values. For example, `seed4j apply init --project-name <TAB>` can suggest `"Seed4J Sample Application"`, `seed4j apply init --node-package-manager <TAB>` can suggest `npm` and `pnpm`, `seed4j apply spring-boot --spring-configuration-format <TAB>` can suggest `yaml` and `properties`, and `seed4j apply init --end-of-line <TAB>` can suggest `lf` and `crlf`.

To generate a completion script without option value candidates, disable value completion:

```bash
seed4j completion bash --no-complete-values
```

The same flag works with installation:

```bash
seed4j completion bash --no-complete-values --install
```

Value completion is limited to explicit static candidates in CLI metadata, known module property values, and module default values. It does not complete filesystem paths, shell history values, project history values, or values inferred from `.seed4j`.

The generated script is static. Regenerate it after installing or changing an extension runtime, switching runtime mode, or changing hidden-resource configuration so Bash sees the same commands as the active CLI runtime. After regenerating, run `source ~/.local/share/bash-completion/completions/seed4j` again in the current terminal or open a new terminal.

### Install a Runtime Extension

To install or replace the active runtime extension:

```bash
seed4j extension install <jar> --distribution-id <id> --distribution-version <version>
```

Behavior:

- validates that `<jar>` is a Seed4J runtime extension JAR (`BOOT-INF/classes` is required)
- writes `~/.config/seed4j-cli/runtime/active/extension.jar`
- writes `~/.config/seed4j-cli/runtime/active/metadata.yml`
- guarantees `seed4j.runtime.mode: extension` in `~/.config/seed4j-cli/config.yml`
- replaces existing active runtime files without `--force`

On success, the command prints:

```text
Extension runtime installed successfully.
Runtime jar: /home/user/.config/seed4j-cli/runtime/active/extension.jar
Metadata: /home/user/.config/seed4j-cli/runtime/active/metadata.yml
Config: /home/user/.config/seed4j-cli/config.yml
Validate installation with:
  seed4j --version
  seed4j list
```

When a runtime is already installed, the command also prints:

```text
Replaced active runtime extension.
```

Fail-fast behavior:

- returns non-zero exit code when the extension JAR layout is invalid
- returns non-zero exit code when `~/.config/seed4j-cli/config.yml` is invalid

### Enable a Runtime Extension

To switch the CLI runtime mode to the active extension runtime:

```bash
seed4j extension enable
```

Behavior:

- validates the active runtime files before changing the mode
- writes `seed4j.runtime.mode: extension` in `~/.config/seed4j-cli/config.yml`
- preserves other existing config keys when the YAML is valid
- returns non-zero exit code and leaves the config unchanged when active runtime artifacts are invalid

On success, the command prints:

```text
Extension runtime enabled successfully.
Config: /home/user/.config/seed4j-cli/config.yml
```

### Disable a Runtime Extension

To switch the CLI runtime mode back to the standard runtime:

```bash
seed4j extension disable
```

Behavior:

- writes `seed4j.runtime.mode: standard` in `~/.config/seed4j-cli/config.yml`
- creates `~/.config/seed4j-cli/config.yml` when missing
- preserves other existing config keys when the YAML is valid
- does not remove `~/.config/seed4j-cli/runtime/active/extension.jar`
- does not remove `~/.config/seed4j-cli/runtime/active/metadata.yml`
- returns non-zero exit code and leaves the config unchanged when `~/.config/seed4j-cli/config.yml` is invalid

On success, the command prints:

```text
Extension runtime disabled successfully.
Config: /home/user/.config/seed4j-cli/config.yml
```

MVP limitation: `seed4j extension disable` runs through the normal CLI bootstrap. If `seed4j.runtime.mode: extension` already makes the launcher fail before commands are created, this command cannot recover automatically yet. Use a valid config file or edit `~/.config/seed4j-cli/config.yml` manually to set `seed4j.runtime.mode: standard`. A launcher bypass recovery path is intentionally left for a later implementation.

## Project Creation Workflow Example

Follow the canonical [project creation workflow](workflows.md#create-a-project-with-modules) for a concrete sequence from an empty directory to a Spring Boot project. This heading remains available so existing links to this reference path and fragment continue to resolve.

## Options and Parameters

Most commands accept additional options and parameters:

- `--project-path=<projectpath>`: Specifies the project directory (defaults to current directory)
- `--[no-]commit`: Initializes Git if needed and commits generated changes (defaults to true). `--no-commit` skips both Git
  initialization and commit.
- `--plan`: Prints resolved module parameters and their value sources without applying changes.
- `--debug`: Enables runtime bootstrap diagnostics (mainly for `extension` mode runtime troubleshooting)
- `--project-name=<projectname>`: The full project name (required for some modules)
- `--base-name=<basename>`: The project's short name, used for naming files and classes (only letters and numbers allowed)
- `--package-name=<packagename>`: The base Java package (required for Java projects)
- `--node-package-manager=<npm|pnpm>`: The node package manager to use for Node.js projects

Options are module-specific. When a required option is missing, the CLI will show an error message indicating which option is required.

### Parameter Reuse

Seed4J CLI automatically reuses parameters from previous module applications. This means:

- Parameters you've provided when applying one module will be remembered for subsequent module applications
- You don't need to specify the same parameters repeatedly for different modules
- Only new parameters or parameters you want to override need to be specified

For example, if you've already run:

```bash
seed4j apply init --project-name "My Project" --base-name MyProject --node-package-manager npm
```

Then when applying another module, you can omit the previously provided parameters:

```bash
seed4j apply maven-java --package-name com.example.myproject
```

The CLI will automatically reuse the `project-name`, `base-name`, and `node-package-manager` values from your previous command.

## External Configuration

Seed4J CLI supports external configuration files to customize its behavior. The CLI automatically looks for a configuration file at:

```
~/.config/seed4j-cli/config.yml
```

If this file exists, it will be loaded automatically when the CLI starts.

### Configuration Options

#### Hidden Resources

You can hide specific modules from being displayed in the `list` command and prevent them from being applied. This is useful for customizing which modules are available in different environments.

Create a `~/.config/seed4j-cli/config.yml` file with the following structure:

```yaml
seed4j:
  hidden-resources:
    slugs:
      - gradle-java
      - module-slug-to-hide
    tags:
      - setup
      - tag-to-hide
```

**Configuration properties:**

- `slugs`: List of specific module slugs to hide
- `tags`: List of module tags to hide (hides all modules with these tags)

These values are exposed through the `seed4j.hidden-resources.*` configuration namespace.

**Effects of hidden resources:**

- Hidden modules will not appear in the output of `seed4j list`
- Hidden modules cannot be applied using `seed4j apply <hidden-module>`
- Hidden modules will not appear in `seed4j completion bash`
- Attempting to apply a hidden module will result in an "Unmatched arguments" error

**Example:**
If you hide the `gradle-java` module, running `seed4j list` will not show it in the available modules, and running `seed4j apply gradle-java` will fail with an error.

#### Runtime Mode

Use runtime mode to control how the CLI bootstraps:

- `standard` (default): uses the standard runtime
- `extension`: loads an additional runtime extension JAR and metadata

Configure it in `~/.config/seed4j-cli/config.yml`:

```yaml
seed4j:
  runtime:
    mode: extension
```

If `seed4j.runtime.mode` is not declared, Seed4J CLI falls back to `standard`.

`seed4j extension install` config note:

- sets `seed4j.runtime.mode` to `extension` automatically on successful installation
- creates `~/.config/seed4j-cli/config.yml` when missing
- preserves other existing config keys when the YAML is valid

`seed4j extension enable` and `seed4j extension disable` config note:

- `enable` validates the active runtime extension before writing `extension` mode
- `disable` writes `standard` mode without deleting active runtime extension files
- both commands fail fast without rewriting invalid YAML/config content

`--debug` runtime note:

- `--debug` is a CLI flag (no value required) shown in `seed4j --help`
- in `extension` mode it enables bootstrap diagnostics for `com.seed4j.cli.bootstrap.domain`
- in `extension` mode it does not force `logging.level.root=ERROR`, so DEBUG diagnostics can be emitted
- the supported operational contract is the literal token `--debug`

#### Extension Runtime Metadata

When `seed4j.runtime.mode: extension` is enabled, Seed4J CLI expects:

- `~/.config/seed4j-cli/runtime/active/extension.jar`
- `~/.config/seed4j-cli/runtime/active/metadata.yml`

`extension.jar` requirements:

- `extension.jar` must be generated from a project built as a Seed4J extension.
- Follow [Creating a Seed4J Extension](#creating-a-seed4j-extension) to create and package the artifact correctly.
- Use `seed4j-sample-extension` as the practical reference implementation for structure and packaging.

Recommended setup for extension mode:

```bash
# 1) build your Seed4J extension project
./mvnw clean package

# 2) install/replace the active runtime extension
seed4j extension install target/<your-extension-artifact>.jar \
  --distribution-id company-extension \
  --distribution-version 1.0.0

# 3) validate extension runtime loading
seed4j --version
seed4j list

# Optional) switch back to standard mode without deleting active extension files
seed4j extension disable
```

The install command creates or replaces these runtime files:

- `~/.config/seed4j-cli/runtime/active/extension.jar`
- `~/.config/seed4j-cli/runtime/active/metadata.yml`

`metadata.yml` contract:

```yaml
distribution:
  id: company-extension
  version: 1.0.0
```

Rules:

- `distribution.id` is required
- `distribution.version` is required

#### Extension Mode Behavior

`extension` mode has two explicit contracts:

- additive discovery for `seed4j list`
- shared runtime behavior for `seed4j apply`

`seed4j list` contract in `extension` mode:

- keeps all core modules from the standard catalog
- adds extension modules on top of the core catalog
- keeps module slugs unique (no duplicated entries in `list`)
- is not reduced by extension-level global config resources (`config/application*`, `logback*`)

`seed4j apply` contract in `extension` mode:

- runs with one shared Spring runtime context (core + extension)
- shares dependency readers/resources globally across core and extension modules
- allows extension overrides to affect core module output only when there is real overlap
- requires overlap on the same logical source/key for dependency version overrides
- requires overlap on the same classpath resource path for template/resource overrides

Practical implications for overrides:

- Node dependency overrides for core modules require the same logical source used by core readers (for example `COMMON`)
- adding a custom source namespace does not override the core value by itself
- template/resource overrides require collision on the exact classpath path consumed by the core module (for example `/generator/prettier/.prettierrc.mustache`)

`BOOT-INF/lib` policy in `extension` mode:

- CLI packaged runtime is the infrastructure baseline
- extension libraries are added to `loader.path` only when they are missing from the CLI runtime
- identity resolution prioritizes nested `META-INF/maven/**/pom.properties` and falls back to jar file name inference only when metadata is unavailable
- extension older version for the same coordinate is non-blocking (CLI version wins)
- extension newer version for the same coordinate is blocking (fail-fast)
- not safely comparable versions for the same coordinate are blocking (fail-fast)
- no inferable identity plus same file-name collision with a CLI library is blocking (fail-fast)

#### Runtime Validation and Failure Cases

Seed4J CLI fails fast (non-zero exit code) in these runtime configuration errors:

- Invalid `seed4j.runtime.mode` value or type
- Invalid YAML structure in `~/.config/seed4j-cli/config.yml`
- Missing `extension.jar` or `metadata.yml` in extension mode
- Invalid `metadata.yml` required fields (`distribution.id`, `distribution.version`)
- Extension runtime jar missing `BOOT-INF/classes`
- Extension nested runtime library metadata is incomplete or conflicting
- Extension library requires a newer version than the CLI for the same coordinate
- Extension/CLI library versions are not safely comparable for the same coordinate

Representative fail-fast messages:

Invalid extension jar layout:

```text
Invalid runtime jar file: /home/user/.config/seed4j-cli/runtime/active/extension.jar. Expected a Spring Boot fat jar containing BOOT-INF/classes.
```

Invalid runtime library metadata (`pom.properties`) in a nested extension library:

```text
Runtime library metadata for 'shared-lib.jar' is incomplete: pom.properties must define groupId, artifactId and version.
Runtime library metadata for 'shared-lib.jar' is conflicting: multiple identities found [com.acme:shared-lib:1.0.0, org.example:shared-lib:2.0.0].
```

Blocking version conflict when extension requires a newer library than the CLI:

```text
Extension runtime library conflict detected for coordinate 'ch.qos.logback:logback-classic': CLI uses version 1.5.32 while extension requires 1.6.0.
```

Blocking conflict for versions that are not safely comparable:

```text
Extension runtime library conflict detected for coordinate 'com.acme:shared-lib': CLI version RELEASE and extension version v1 are not safely comparable.
```

In this case, follow [Creating a Seed4J Extension](#creating-a-seed4j-extension), rebuild the artifact, and replace `~/.config/seed4j-cli/runtime/active/extension.jar`.

Operational note:

- `extension` mode requires executing the packaged CLI JAR
- `standard` mode can still run locally outside a packaged JAR (with a fallback warning)

#### Creating a Seed4J Extension

Follow the canonical [runtime extension workflow](workflows.md#create-and-install-a-runtime-extension) to build the sample module, package it, install it, and validate activation. This heading remains available so existing links to this reference path and fragment continue to resolve.
