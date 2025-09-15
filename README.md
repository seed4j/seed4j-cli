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

You need to have Java 21:

- [JDK 21](https://openjdk.java.net/projects/jdk/21/)

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
