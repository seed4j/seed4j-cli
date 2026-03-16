# Runtime Bootstrap Next Slice

## Snapshot (2026-03-16)

This file is now a slice execution status snapshot.

Baseline reference: `LOCAL_IMPLEMENTED_SPEC.md` (2026-03-12).

## Completed in this slice

- `Seed4JCliApp` production path delegates to `Seed4JCliLauncher` and exits with launcher return code.
- Real Java child-process execution is implemented through `ProcessBuilder(...).inheritIO().start().waitFor()`.
- Child JVM bootstrap uses `org.springframework.boot.loader.launch.PropertiesLauncher`.
- Parent-to-child propagation is implemented for:
  - `seed4j.cli.runtime.child=true`
  - `seed4j.cli.runtime.mode`
  - `seed4j.cli.runtime.distribution.id`
  - `seed4j.cli.runtime.distribution.version`
  - `loader.path` (extension mode)
- Child stdio and child exit code are propagated to parent.
- Runtime identity shown by `--version` is sourced from runtime system properties in the active process.
- Executable JAR resolution for production bootstrap was hardened for supported startup variants:
  - JAR path in `sun.java.command` (absolute)
  - JAR path in `sun.java.command` (relative to `user.dir`)
  - Fallback by `java.class.path` when command starts with `PropertiesLauncher`

## Acceptance criteria status

- Public entrypoint delegation: done.
- Standard mode child launch: done (packaged JAR execution path).
- Extension mode child launch with `loader.path` and active distribution properties: done.
- Invalid extension fail-fast before child launch: done.
- Child recursion guard (`seed4j.cli.runtime.child=true`): done.
- Runtime identity observability in effective Spring process (`--version`): done.

## Explicitly out of scope for this slice

- New metadata fields: `distribution.runtime-version`, `distribution.bootstrap-class`, `distribution.runtime-contract-version`.
- Runtime mode rename from `standard` to `base`.
- `seed4j.runtime.fail-on-invalid-extension` behavior/fallback branching.
- Runtime-contract handshake semantics beyond current id/version propagation.
- Manual multi-jar classpath startup prioritization logic (for example: `java -cp a.jar:b.jar ...`).
  - Supported contract remains `java -jar seed4j-cli-<version>.jar`.

## Verification checkpoints executed

- Repeated vertical checkpoint:
  - `./mvnw clean verify`
  - Latest result on 2026-03-16: `BUILD SUCCESS` (`Tests run: 310, Failures: 0, Errors: 0`)
- Public path checkpoints:
  - `java -jar target/seed4j-cli-0.0.1-SNAPSHOT.jar --version`
  - `java -cp target/seed4j-cli-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher --version`

## Primary tests that back this slice

- `src/test/java/com/seed4j/cli/Seed4JCliAppTest.java`
- `src/test/java/com/seed4j/cli/bootstrap/domain/JavaProcessChildLauncherTest.java`
- `src/test/java/com/seed4j/cli/bootstrap/domain/Seed4JCliLauncherTest.java`
- `src/test/java/com/seed4j/cli/bootstrap/domain/Seed4JCliLauncherFactoryTest.java`
- `src/test/java/com/seed4j/cli/command/infrastructure/primary/CurrentProcessRuntimeSelectionProviderTest.java`
- `src/test/java/com/seed4j/cli/command/infrastructure/primary/SystemPropertyRuntimeSelectionProviderTest.java`
