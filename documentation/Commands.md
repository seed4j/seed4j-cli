# Seed4J CLI Commands

This document provides an overview of the Seed4J CLI commands available in this project.

## Table of Contents

- [Getting Started](#getting-started)
- [Basic Commands](#basic-commands)
  - [Version](#version)
  - [List Available Modules](#list-available-modules)
  - [Apply a Module](#apply-a-module)
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
- Active distribution ID
- Active distribution version

Example output in `standard` mode:

```text
Seed4J CLI v0.0.1-SNAPSHOT
Seed4J version: 2.2.0
Runtime mode: standard
Distribution ID: standard
Distribution version: 0.0.1-SNAPSHOT
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
- `--[no-]commit`: Whether to commit changes to git (defaults to true)
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
~/.config/seed4j-cli.yml
```

If this file exists, it will be loaded automatically when the CLI starts.

### Configuration Options

#### Hidden Resources

You can hide specific modules from being displayed in the `list` command and prevent them from being applied. This is useful for customizing which modules are available in different environments.

Create a `~/.config/seed4j-cli.yml` file with the following structure:

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
- Attempting to apply a hidden module will result in an "Unmatched arguments" error

**Example:**
If you hide the `gradle-java` module, running `seed4j list` will not show it in the available modules, and running `seed4j apply gradle-java` will fail with an error.

#### Runtime Mode

Use runtime mode to control how the CLI bootstraps:

- `standard` (default): uses the standard runtime
- `extension`: loads an additional runtime extension JAR and metadata

Configure it in `~/.config/seed4j-cli.yml`:

```yaml
seed4j:
  runtime:
    mode: extension
```

If `seed4j.runtime.mode` is not declared, Seed4J CLI falls back to `standard`.

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

Example setup for extension mode:

```bash
# 1) build your Seed4J extension project
./mvnw clean package

# 2) install the generated runtime artifact with the expected filename
cp target/<your-extension-artifact>.jar ~/.config/seed4j-cli/runtime/active/extension.jar

# 3) keep metadata.yml in the same runtime folder
ls ~/.config/seed4j-cli/runtime/active/metadata.yml

# 4) validate extension runtime loading
seed4j --version
seed4j list
```

`metadata.yml` contract:

```yaml
distribution:
  id: company-extension
  version: 1.0.0
compatibility:
  min-cli-version: 0.0.1
```

Rules:

- `distribution.id` is required
- `distribution.version` is required
- `compatibility.min-cli-version` is optional

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
- Invalid YAML structure in `~/.config/seed4j-cli.yml`
- Missing `extension.jar` or `metadata.yml` in extension mode
- Invalid `metadata.yml` required fields (`distribution.id`, `distribution.version`)
- Invalid `compatibility.min-cli-version` format
- Current CLI version lower than `compatibility.min-cli-version`
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
6. Install runtime files:
   - `~/.config/seed4j-cli/runtime/active/extension.jar`
   - `~/.config/seed4j-cli/runtime/active/metadata.yml`
7. Enable extension mode in `~/.config/seed4j-cli.yml` and run `seed4j --version` / `seed4j list` to validate.

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
compatibility:
  min-cli-version: 0.0.1
```

Important notes:

- `distribution.id` and `distribution.version` are mandatory.
- `compatibility.min-cli-version` is optional but recommended.
- Avoid shipping unintended overrides (for example, `config/application.yml`) unless you intentionally want to change core behavior.
