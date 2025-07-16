# JHipster Lite CLI Commands

This document provides an overview of the JHipster Lite CLI commands available in this project.

## Table of Contents

- [Getting Started](#getting-started)
- [Basic Commands](#basic-commands)
  - [Version](#version)
  - [List Available Modules](#list-available-modules)
  - [Apply a Module](#apply-a-module)
- [Project Creation Workflow Example](#project-creation-workflow-example)
- [Options and Parameters](#options-and-parameters)
  - [Parameters Reuse](#parameter-reuse)

## Getting Started

To use JHipster Lite CLI, make sure it's installed and available in your PATH. You can check if it's properly installed with:

```bash
jhlite --version
```

## Basic Commands

### Version

To check the JHipster Lite CLI version:

```bash
jhlite --version
```

This will display the JHipster Lite CLI version and the JHipster Lite version.

### List Available Modules

To see all available modules that can be applied to your project:

```bash
jhlite list
```

This command displays a list of all available modules with their names and descriptions.

### Apply a Module

To apply a specific module to your project:

```bash
jhlite apply <module-name> [options]
```

Most modules require specific parameters. If you miss required parameters, the CLI will inform you which ones are missing.

To see the specific parameters for a module and which one is required, run:

```bash
jhlite apply <module-name> --help
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
   jhlite apply init --project-name "My Project" --base-name MyProject --node-package-manager npm
   ```

3. Add code formatting support:

   ```bash
   jhlite apply prettier
   ```

4. Set up a Maven project structure:

   ```bash
   jhlite apply maven-java --package-name com.example.myproject
   ```

5. Add Maven wrapper:

   ```bash
   jhlite apply maven-wrapper
   ```

6. Add Java base classes:

   ```bash
   jhlite apply java-base
   ```

7. Add Spring Boot:
   ```bash
   jhlite apply spring-boot
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

JHipster Lite CLI automatically reuses parameters from previous module applications. This means:

- Parameters you've provided when applying one module will be remembered for subsequent module applications
- You don't need to specify the same parameters repeatedly for different modules
- Only new parameters or parameters you want to override need to be specified

For example, if you've already run:

```bash
jhlite apply init --project-name "My Project" --base-name MyProject --node-package-manager npm
```

Then when applying another module, you can omit the previously provided parameters:

```bash
jhlite apply maven-java --package-name com.example.myproject
```

The CLI will automatically reuse the `project-name`, `base-name`, and `node-package-manager` values from your previous command.
