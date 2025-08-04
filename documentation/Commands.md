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

This will display the Seed4J CLI version and the Seed4J version.

### List Available Modules

To see all available modules that can be applied to your project:

```bash
seed4j list
```

This command displays a list of all available modules with their names and descriptions.

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
jhlite:
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

**Effects of hidden resources:**

- Hidden modules will not appear in the output of `seed4j list`
- Hidden modules cannot be applied using `seed4j apply <hidden-module>`
- Attempting to apply a hidden module will result in an "Unmatched arguments" error

**Example:**
If you hide the `gradle-java` module, running `seed4j list` will not show it in the available modules, and running `seed4j apply gradle-java` will fail with an error.
