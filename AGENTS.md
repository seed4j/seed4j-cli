# Repository Guidelines

## Project Structure & Module Organization

`src/main/java/com/seed4j/cli` contains the CLI application, bootstrap logic, command adapters, and shared kernel code. Keep new production code inside that package tree and preserve the existing hexagonal split, especially `bootstrap`, `command/infrastructure/primary`, and `shared/*`. Runtime and Spring configuration live in `src/main/resources/config`. Tests mirror the main package layout under `src/test/java`, with support fixtures in `command/infrastructure/primary`. Reference material and architecture notes live in `documentation/`, and CI helpers live in `tests-ci/`.
Use Types Driven Development. The idea is pretty simple: create a dedicated type for each business concept.

## Build, Test, and Development Commands

Use Java 25 and Node.js 22+ before running the toolchain.

- `./mvnw clean package` builds the CLI JAR in `target/`.
- `./mvnw clean verify` is the default validation command to use locally; it runs unit/integration tests, JaCoCo aggregation, Checkstyle, and coverage gates.
- `./mvnw test` runs only the JUnit 5 test suite, but prefer `./mvnw clean verify` unless there is a specific reason to narrow the scope.
- `npm run prettier:check` validates formatting for Java, XML, YAML, Markdown, and JSON.
- `npm run prettier:format` rewrites supported files using the repository formatter configuration.

## Coding Style & Naming Conventions

Follow `.editorconfig`: 2-space indentation, LF line endings, UTF-8, and final newlines. Prettier is the formatter of record, including Java via `prettier-plugin-java`; run it instead of hand-formatting. Checkstyle enforces import hygiene, visibility, declaration order, and standard Java naming. Match current naming patterns: `*Command`, `*Configuration`, `*Exception`, and package names in lowercase. Prefer one public type per file. In production code, order private helper methods immediately after their first use; when a helper is used from multiple places, place it after the earliest caller that introduces it. Avoid `is*` prefixes for boolean methods; prefer names that express the business predicate directly, such as `atLeast(...)`, `missingBootInfClasses(...)`, or `standardMode(...)`. Prefer `Optional` over direct `null` comparisons when modeling optional values. Do not use `@Autowired`; prefer a single explicit constructor and direct wiring. Do not use `var` in Java; prefer explicit types.

## Testing Guidelines

This project uses JUnit 5 with Spring Boot test support. Name test classes `*Test.java` and keep them in the mirrored package for the class under test. Reuse the existing meta-annotations: `@UnitTest`, `@ComponentTest`, and `@IntegrationTest` to signal scope consistently. `./mvnw clean verify` fails on uncovered lines or branches, so add tests with every behavior change rather than relying on partial coverage. Do not break the TDD loop by introducing several behaviors at once in a new test class; avoid mixing “closing coverage” with “discovering design”, because that creates steps large enough for real bugs to hide inside them. Keep assertions explicit in the test body instead of encapsulating them in helper methods, because inline assertions improve readability and failure diagnosis. Structure test methods with clear Given/When/Then blocks separated by a blank line. Keep `Then` focused on assertions only; do not build `expected` values after `When`. Keep trivial expected values inline. Do not extract helpers for one-step transforms or direct projections (for example `path.toString()`, simple constant concatenation, or single-field mapping). Extract helper methods for expected values only when the structure is complex (for example multi-line content, derived path trees, or large object graphs) or when the helper is reused by two or more tests. If a helper is introduced during RED only to satisfy compilation, remove or inline it during GREEN unless it clearly improves readability. Heuristic: when a helper body is a single expression and has a single call site in the same test class, inline it. Avoid computing expected values with production decision logic. When a test introduces a seam (for example, an overload for deterministic environment control), that seam must be connected to the production path or removed; avoid test-only methods that are never exercised by real runtime flow.

## Commit & Pull Request Guidelines

Recent history follows Conventional Commits with scopes, for example `feat(bootstrap): validate extension metadata kind` and `refactor(bootstrap): extract runtime metadata parser`. Keep commits focused and use `type(scope): imperative summary`. For pull requests, include a short problem/solution description, link the relevant issue when one exists, and list the verification you ran locally. Screenshots are only useful when command output or documentation rendering changed.

## Configuration & Architecture Notes

The CLI loads external overrides from `~/.config/seed4j-cli.yml`; document any new configuration keys in both code and `README.md`. If you change command flow or boundaries, update the relevant files in `documentation/`, especially the hexagonal architecture and commands guides.

## Sonar and Cleanup Learnings

For Sonar-specific cleanup patterns and the validated local Sonar workflow used in this repository, see `.agent/SONAR_LEARNINGS.md`.

## Agent Terminal Behavior

When the user asks to "use seed4j in the terminal" (or equivalent wording in Portuguese/English), run the `seed4j` command directly in the shell and report the real terminal output and exit code. Do not answer only with documentation or examples unless the user explicitly asks for explanation instead of execution.
