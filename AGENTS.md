# Repository Guidelines

## Project Structure & Module Organization

`src/main/java/com/seed4j/cli` contains the CLI application, bootstrap logic, command adapters, and shared kernel code. Keep new production code inside that package tree and preserve the existing hexagonal split, especially `bootstrap`, `command/infrastructure/primary`, and `shared/*`. Runtime and Spring configuration live in `src/main/resources/config`. Tests mirror the main package layout under `src/test/java`, with support fixtures in `command/infrastructure/primary`. Reference material and architecture notes live in `documentation/`, and CI helpers live in `tests-ci/`.
Use Types Driven Development. The idea is pretty simple: create a dedicated type for each business concept.

## Build, Test, and Development Commands

Use Java 25 and Node.js 22+ before running the toolchain.

- `./mvnw clean package` builds the CLI JAR in `target/`.
- `./mvnw test` runs the JUnit 5 test suite.
- `./mvnw clean verify` runs unit/integration tests, JaCoCo aggregation, Checkstyle, and coverage gates; this is the closest local equivalent to CI.
- `npm run prettier:check` validates formatting for Java, XML, YAML, Markdown, and JSON.
- `npm run prettier:format` rewrites supported files using the repository formatter configuration.

## Coding Style & Naming Conventions

Follow `.editorconfig`: 2-space indentation, LF line endings, UTF-8, and final newlines. Prettier is the formatter of record, including Java via `prettier-plugin-java`; run it instead of hand-formatting. Checkstyle enforces import hygiene, visibility, declaration order, and standard Java naming. Match current naming patterns: `*Command`, `*Configuration`, `*Exception`, and package names in lowercase. Prefer one public type per file. Avoid `is*` prefixes for domain operations and capability checks; prefer names that express the concept directly, such as `atLeast(...)`. Do not use `@Autowired`; prefer a single explicit constructor and direct wiring. Do not use `var` in Java; prefer explicit types.

## Testing Guidelines

This project uses JUnit 5 with Spring Boot test support. Name test classes `*Test.java` and keep them in the mirrored package for the class under test. Reuse the existing meta-annotations: `@UnitTest`, `@ComponentTest`, and `@IntegrationTest` to signal scope consistently. `./mvnw clean verify` fails on uncovered lines or branches, so add tests with every behavior change rather than relying on partial coverage.

## Commit & Pull Request Guidelines

Recent history follows Conventional Commits with scopes, for example `feat(bootstrap): validate extension metadata kind` and `refactor(bootstrap): extract runtime metadata parser`. Keep commits focused and use `type(scope): imperative summary`. For pull requests, include a short problem/solution description, link the relevant issue when one exists, and list the verification you ran locally. Screenshots are only useful when command output or documentation rendering changed.

## Configuration & Architecture Notes

The CLI loads external overrides from `~/.config/seed4j-cli.yml`; document any new configuration keys in both code and `README.md`. If you change command flow or boundaries, update the relevant files in `documentation/`, especially the hexagonal architecture and commands guides.
