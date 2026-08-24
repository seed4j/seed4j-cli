# Seed4J CLI workflows

These recipes organize commands around concrete outcomes. Use the [commands reference](Commands.md) when you need exact options, exit behavior, configuration keys, or failure contracts.

## Create a project with modules

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

After this basic setup, you can add more specific modules based on your project requirements. The [apply reference](Commands.md#apply-a-module) explains dependency planning, parameter reuse, `--plan`, and blocking exit behavior.

## Create and install a runtime extension

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

Install the built artifact with concrete distribution metadata:

```bash
seed4j extension install target/<your-extension-artifact>.jar --distribution-id my-company-extension --distribution-version 1.0.0
```

You can then switch runtime modes explicitly:

```bash
seed4j extension enable
seed4j extension disable
```

Validate runtime activation after installation or mode changes:

```bash
seed4j --version
seed4j list
```

Before installing, check the normative [runtime metadata](Commands.md#extension-runtime-metadata) and [validation failures](Commands.md#runtime-validation-and-failure-cases). Then use the exact [extension install procedure](Commands.md#install-a-runtime-extension).
