# JHLite Cli <img src="https://renanfranca.github.io/assets/icons/icon-terminal-solid-blue.svg" alt="console icon" height="20" width="20"/>

## Description

JHipster Lite CLI is a command-line interface tool that helps you apply and manage JHipster Lite modules. It provides a modular approach to application generation, allowing you to select specific modules and features for your project. Visit [JHipster Lite](https://github.com/jhipster/jhipster-lite) to learn more about it.

## Quick Start

You need to clone this project and go into the folder:

```bash
git clone https://github.com/jhipster/jhipster-lite
cd jhipster-lite
```

Install `jhlite` command in your bin folder:

```bash
./mvnw clean package && echo "java -jar \"/usr/local/bin/jhlite.jar\" \"\$@\"" | sudo tee /usr/local/bin/jhlite > /dev/null && sudo chmod +x /usr/local/bin/jhlite && JAR_SOURCE=$(ls target/jhlite-cli-*.jar | head -n 1) && [ -n "$JAR_SOURCE" ] && sudo mv "$JAR_SOURCE" /usr/local/bin/jhlite.jar || echo "No JAR file found in target directory"
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

<!-- jhipster-needle-localEnvironment -->

## Start up

```bash
./mvnw
```

## Install CLI Command

```bash
./mvnw clean package

echo "java -jar \"/usr/local/bin/jhlite.jar\" \"\$@\"" | sudo tee /usr/local/bin/jhlite > /dev/null

# Make the script executable
sudo chmod +x /usr/local/bin/jhlite

# Find the JAR file in the target directory and move it as jhlite.jar
JAR_SOURCE=$(ls target/jhlite-cli-*.jar | head -n 1)
if [ -n "$JAR_SOURCE" ]; then
  sudo mv "$JAR_SOURCE" /usr/local/bin/jhlite.jar
else
  echo "No JAR file found in target directory"
fi
```

Copy and paste the above script into a terminal to install the jhlite command.

You can use a single command:

```bash
./mvnw clean package && echo "java -jar \"/usr/local/bin/jhlite.jar\" \"\$@\"" | sudo tee /usr/local/bin/jhlite > /dev/null && sudo chmod +x /usr/local/bin/jhlite && JAR_SOURCE=$(ls target/jhlite-cli-*.jar | head -n 1) && [ -n "$JAR_SOURCE" ] && sudo mv "$JAR_SOURCE" /usr/local/bin/jhlite.jar || echo "No JAR file found in target directory"
```

### Usage

After the installation, you can use the `jhlite` with help command to know the options:

```bash
jhlite --help
```

<!-- jhipster-needle-startupCommand -->

## Documentation

- [Commands Guide](documentation/Commands.md)
- [Hexagonal architecture](documentation/hexagonal-architecture.md)
- [Package types](documentation/package-types.md)
- [Assertions](documentation/assertions.md)
- [sonar](documentation/sonar.md)

<!-- jhipster-needle-documentation -->
