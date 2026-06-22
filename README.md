# Seed4J CLI <img src="https://renanfranca.github.io/assets/icons/icon-complete-terminal-seed4j-cli.svg" alt="console icon" height="43" align="top"/>

[![Build Status][github-actions-seed4j-cli-image]][github-actions-url]
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=seed4j_seed4j-cli&metric=coverage)](https://sonarcloud.io/project/overview?id=seed4j_seed4j-cli)

[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=seed4j_seed4j-cli&metric=alert_status)](https://sonarcloud.io/project/overview?id=seed4j_seed4j-cli)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=seed4j_seed4j-cli&metric=alert_status)](https://sonarcloud.io/project/overview?id=seed4j_seed4j-cli)
[![Maintainability](https://sonarcloud.io/api/project_badges/measure?project=seed4j_seed4j-cli&metric=sqale_rating)](https://sonarcloud.io/project/overview?id=seed4j_seed4j-cli)

[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=seed4j_seed4j-cli&metric=bugs)](https://sonarcloud.io/project/overview?id=seed4j_seed4j-cli)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=seed4j_seed4j-cli&metric=vulnerabilities)](https://sonarcloud.io/project/overview?id=seed4j_seed4j-cli)
[![Security](https://sonarcloud.io/api/project_badges/measure?project=seed4j_seed4j-cli&metric=security_rating)](https://sonarcloud.io/project/overview?id=seed4j_seed4j-cli)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=seed4j_seed4j-cli&metric=code_smells)](https://sonarcloud.io/project/overview?id=seed4j_seed4j-cli)

## Description

Seed4J CLI is a command-line interface tool that helps you apply and manage Seed4J modules. It provides a modular approach to application generation, allowing you to select specific modules and features for your project. Visit [Seed4J](https://github.com/seed4j/seed4j) to learn more about it.

## Quick Start

You need to clone this project and go into the folder:

```bash
git clone https://github.com/seed4j/seed4j-cli
cd seed4j-cli
```

Install `seed4j` command in your bin folder:

```bash
./mvnw clean package && echo "java -jar \"/usr/local/bin/seed4j.jar\" \"\$@\"" | sudo tee /usr/local/bin/seed4j > /dev/null && sudo chmod +x /usr/local/bin/seed4j && JAR_SOURCE=$(ls target/seed4j-cli-*.jar | head -n 1) && [ -n "$JAR_SOURCE" ] && sudo mv "$JAR_SOURCE" /usr/local/bin/seed4j.jar || echo "No JAR file found in target directory"
```

Then you can follow the [Commands Guide](documentation/Commands.md)

## Prerequisites

### Java

You need to have Java 25:

- [JDK 25](https://openjdk.org/projects/jdk/25/)

### Node.js and NPM

Before you can build this project, you must install and configure the following dependencies on your machine:

[Node.js](https://nodejs.org/): We use Node to run a development web server and build the project.
Depending on your system, you can install Node either from source or as a pre-packaged bundle.

After installing Node, you should be able to run the following command to install development tools.
You will only need to run this command when dependencies change in [package.json](package.json).

```
npm install
```

## Local environment

<!-- seed4j-needle-localEnvironment -->

## Start up

```bash
./mvnw
```

## Install CLI Command

```bash
./mvnw clean package

echo "java -jar \"/usr/local/bin/seed4j.jar\" \"\$@\"" | sudo tee /usr/local/bin/seed4j > /dev/null

# Make the script executable
sudo chmod +x /usr/local/bin/seed4j

# Find the JAR file in the target directory and move it as seed4j.jar
JAR_SOURCE=$(ls target/seed4j-cli-*.jar | head -n 1)
if [ -n "$JAR_SOURCE" ]; then
  sudo mv "$JAR_SOURCE" /usr/local/bin/seed4j.jar
else
  echo "No JAR file found in target directory"
fi
```

Copy and paste the above script into a terminal to install the `seed4j` command.

You can use a single command:

```bash
./mvnw clean package && echo "java -jar \"/usr/local/bin/seed4j.jar\" \"\$@\"" | sudo tee /usr/local/bin/seed4j > /dev/null && sudo chmod +x /usr/local/bin/seed4j && JAR_SOURCE=$(ls target/seed4j-cli-*.jar | head -n 1) && [ -n "$JAR_SOURCE" ] && sudo mv "$JAR_SOURCE" /usr/local/bin/seed4j.jar || echo "No JAR file found in target directory"
```

### Usage

After the installation, you can use the `seed4j` with help command to know the options:

```bash
seed4j --help
```

To confirm the installed binary and embedded platform version:

```bash
seed4j --version
```

This prints both the Seed4J CLI version and the bundled Seed4J version.

To inspect module parameters before applying a module, add `--plan`:

```bash
seed4j apply init --plan
```

The plan prints the resolved text values and shows whether each value came from the current CLI input, project history, or a module metadata default. If required values are missing, it prints a `Missing required parameters` section and still does not apply files, write history, or create commits.

To install Bash completion for the active runtime:

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

The generated script is static. Regenerate it after installing or changing an extension runtime, switching runtime mode, or changing hidden-resource configuration, then run `source ~/.local/share/bash-completion/completions/seed4j` again in the current terminal or open a new terminal.

By default, Bash completion includes static option value candidates from CLI metadata, known Seed4J module property values, and module default values. For example, `seed4j apply init --project-name <TAB>` can suggest `"Seed4J Sample Application"`, `seed4j apply init --node-package-manager <TAB>` can suggest `npm` and `pnpm`, `seed4j apply spring-boot --spring-configuration-format <TAB>` can suggest `yaml` and `properties`, and `seed4j apply init --end-of-line <TAB>` can suggest `lf` and `crlf`. To generate a script without option value suggestions, use:

```bash
seed4j completion bash --no-complete-values --install
```

Value completion is limited to explicit static candidates in CLI metadata, known module property values, and module default values. It does not complete filesystem paths, shell history values, project history values, or values inferred from `.seed4j`.

To install or replace the active extension runtime:

```bash
seed4j extension install target/<your-extension-artifact>.jar --distribution-id my-company-extension --distribution-version 1.0.0
```

The install command validates the artifact, writes the active runtime files, and enables extension mode. You can also switch modes explicitly:

```bash
seed4j extension enable
seed4j extension disable
```

`enable` validates the active extension runtime before writing `mode: extension`. `disable` writes `mode: standard`, creates the config file when missing, and leaves the active extension JAR and metadata in place.

After installation, validate runtime activation with:

```bash
seed4j --version
seed4j list
```

Current limitation: `seed4j extension disable` uses the normal CLI bootstrap. It cannot recover automatically if a broken extension-mode configuration prevents the launcher from creating commands; in that case, set `seed4j.runtime.mode: standard` manually in `~/.config/seed4j-cli/config.yml`.

<!-- seed4j-needle-startupCommand -->

## Documentation

- [Commands Guide](documentation/Commands.md)
- [Hexagonal architecture](documentation/hexagonal-architecture.md)
- [Package types](documentation/package-types.md)
- [Assertions](documentation/assertions.md)
- [sonar](documentation/sonar.md)

<!-- seed4j-needle-documentation -->

[github-actions-seed4j-cli-image]: https://github.com/seed4j/seed4j-cli/actions/workflows/github-actions.yml/badge.svg?branch=main
[github-actions-url]: https://github.com/jhipster/seed4j-cli/actions
