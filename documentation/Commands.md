# Seed4J CLI Commands

This document provides an overview of the Seed4J CLI commands available in this project.

## Table of Contents

- [Getting Started](#getting-started)
- [Basic Commands](#basic-commands)
  - [Version](#version)
  - [List Available Modules](#list-available-modules)
  - [Apply a Module](#apply-a-module)
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

To see the specific parameters for a module and which one is required, run:

```bash
seed4j apply <module-name> --help
```

To inspect the values Seed4J would use without applying the module, add `--plan`:

```bash
seed4j apply init --project-name "My Project" --base-name MyProject --node-package-manager pnpm --plan
```

The plan is text-only and exits without generated files, history entries, or commits. Required parameters can come from the current command or project history. When required parameters are still missing, the plan exits successfully and prints a `Missing required parameters` section so callers know which options to pass before applying the module.

Example output:

```text
Plan for module: init
Project path: /home/user/my-project

Resolved parameters:

projectName: My Project
  Source: explicit CLI input
  CLI option: --project-name

baseName: MyProject
  Source: explicit CLI input
  CLI option: --base-name

nodePackageManager: pnpm
  Source: explicit CLI input
  CLI option: --node-package-manager

endOfLine: lf
  Source: default
  CLI option: --end-of-line

No changes were applied.
```

Plan source labels mean:

- `explicit CLI input`: the option was passed in the current command
- `project history`: the value came from the latest saved project parameters
- `default`: the module metadata defines a display default and no explicit or historical value exists

Project-history values include a note telling callers they can omit that option to keep the remembered value. Defaults in the plan are informational; they are not injected into normal `apply` parameters. JSON output and `--format` are not part of this text-only phase.

If required values are missing, the plan includes them after the resolved parameters:

```text
Missing required parameters:

projectName:
  CLI option: --project-name
  Note: pass this option or apply a module that records it in project history.
```

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

The script completes available command names, nested subcommands, `apply` module slugs, option names, negated option names such as `--no-commit`, and static option value candidates from CLI metadata, known Seed4J module property values, and module default values. For example, `seed4j apply init --project-name <TAB>` can suggest `Seed4J Sample Application`, `seed4j apply init --node-package-manager <TAB>` can suggest `npm` and `pnpm`, `seed4j apply spring-boot --spring-configuration-format <TAB>` can suggest `yaml` and `properties`, and `seed4j apply init --end-of-line <TAB>` can suggest `lf` and `crlf`.

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

After this basic setup, you can add more specific modules based on your project requirements.

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
