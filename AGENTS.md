# Repository Guidelines

## Project Structure & Module Organization

`src/main/java/com/seed4j/cli` contains the CLI application, bootstrap logic, command adapters, and shared kernel code. Keep new production code inside that package tree and preserve the existing hexagonal split, especially `bootstrap`, `command/infrastructure/primary`, and `shared/*`. Runtime and Spring configuration live in `src/main/resources/config`. Tests mirror the main package layout under `src/test/java`, with support fixtures in `command/infrastructure/primary`. Reference material and architecture notes live in `documentation/`, and CI helpers live in `tests-ci/`.
Use Types Driven Development. The idea is pretty simple: create a dedicated type for each business concept.

## Hexagonal Architecture Boundaries

Keep hexagonal boundaries explicit. `domain` contains business rules, business types, pure domain services, and ports; it must not depend on Spring, application services, or infrastructure. `application` orchestrates use cases, authorization and transactions when applicable, and receives ports and domain types. It may construct pure domain services when that is the right shape for the use case.

`application` must not depend on concrete adapters, filesystem access, Spring configuration, `System.getProperty`, or classes in `..infrastructure..`. `infrastructure.primary` translates CLI, HTTP, and framework input into domain types and calls application services. `infrastructure.secondary` implements domain ports and encapsulates filesystem access, process execution, Spring Security, configuration loading, and other external mechanisms.

For new or changed code, model business concepts with Value Objects before they cross into `application` or `domain`. `primary` adapters may receive raw CLI/framework values such as `String`, `Path`, booleans, or primitive options, but they should translate those values into domain types before calling application services. Domain aggregate records should not expose multiple raw business values when dedicated domain types can express those concepts. A single-field Value Object may wrap a raw value and should validate or normalize the represented concept. Keep exceptions small, intentional, and documented by the architecture test allowlist when they are legacy compatibility cases.

Physical locations, runtime layouts, persisted configuration files, cache directories, generated file names, and other storage details are secondary infrastructure concerns. Do not model those details as concrete domain records just to pass filesystem locations through `primary`, `application`, or `domain`. If the domain needs to use something stored externally, model the need as a domain port named after the capability or business concept, and keep the concrete path/layout resolution inside `infrastructure.secondary`.

A `Path` in the domain is acceptable only when it represents a user-visible or business-relevant value, such as an input project path, an artifact explicitly supplied by the user, or a path returned for CLI feedback. A `Path` is not acceptable in the domain when it represents hidden operational layout, such as where the CLI persists runtime metadata, active runtime files, caches, config internals, or adapter-managed storage. In those cases, expose domain concepts and ports instead, for example "active runtime extension", "runtime selection", or "install runtime artifacts", and let the secondary adapter decide which files implement them.

`composition` and pre-Spring bootstrap code are manual composition roots needed before Spring is available. They may assemble primary, application, domain, and secondary objects, but they must stay explicit and must not become a shortcut for mixing layer responsibilities.

`Seed4JCliHome` is the domain concept for paths derived from `user.home`. Read `user.home` only in Spring configuration or pre-Spring adapters, then pass the resulting domain type inward. Keep this exception narrow: `Seed4JCliHome` may represent the CLI home concept, but hidden operational layouts derived from it should normally be resolved inside secondary adapters, not exposed as separate domain records.

## Build, Test, and Development Commands

Use Java 25 and Node.js 22+ before running the toolchain.

- `./mvnw clean package` builds the CLI JAR in `target/`.
- `./mvnw clean verify` is the complete local validation gate; it runs unit/integration tests, JaCoCo aggregation, Checkstyle, and coverage gates. Its output is large, so agents must not run it automatically.
- `./mvnw test` runs only the JUnit 5 test suite and is acceptable for narrower validation.
- `npm run prettier:check` validates formatting for Java, XML, YAML, Markdown, and JSON.
- `npm run prettier:format` rewrites supported files using the repository formatter configuration.

## Agent Validation Behavior

Agents may run smaller, targeted checks such as `./mvnw test`, specific Maven tests, `npm run prettier:check`, or focused Maven goals. Before the final gate is needed, ask the user to run `./mvnw clean verify` locally and send the exit code plus a concise summary of any relevant failure. If the user explicitly asks an agent to run `./mvnw clean verify`, the agent may run it, preferably with output limited or redirected to a file.

## Coding Style & Naming Conventions

Follow `.editorconfig`: 2-space indentation, LF line endings, UTF-8, and final newlines. Prettier is the formatter of record, including Java via `prettier-plugin-java`; run it instead of hand-formatting. Checkstyle enforces import hygiene, visibility, declaration order, and standard Java naming. Match current naming patterns: `*Command`, `*Configuration`, `*Exception`, and package names in lowercase. Domain interfaces that represent ports should be named by business capability, such as `RuntimeExtensionInstaller`, `RuntimeDisplayReader`, or `RuntimeModeConfigurationRepository`; do not use the `Port` suffix for domain ports. Secondary implementations of domain ports should use mechanism, source, or context prefix plus capability, such as `FileSystemProjectsRepository`, `BootstrapRuntimeDisplayReader`, or `BootstrapRuntimeExtensionInstaller`. Reserve `Adapter` for technical wrappers that do not implement a domain port, such as adapters around APIs or frameworks. Prefer one public type per file. In production code, order private helper methods immediately after their first use; when a helper is used from multiple places, place it after the earliest caller that introduces it. Avoid `is*` prefixes for boolean methods; prefer names that express the business predicate directly, such as `atLeast(...)`, `missingBootInfClasses(...)`, or `standardMode(...)`. Prefer `Optional` over direct `null` comparisons when modeling optional values. Do not use `@Autowired`; prefer a single explicit constructor and direct wiring. Do not use `var` in Java; prefer explicit types. When validating mandatory references in Java, do not use `Objects.requireNonNull`; use `Assert.notNull("fieldName", value)` from `src/main/java/com/seed4j/cli/shared/error/domain/Assert.java`.

## Testing Guidelines

This project uses JUnit 5 with Spring Boot test support. Name test classes `*Test.java` and keep them in the mirrored package for the class under test. Reuse the existing meta-annotations: `@UnitTest`, `@ComponentTest`, and `@IntegrationTest` to signal scope consistently. `./mvnw clean verify` fails on uncovered lines or branches, so add tests with every behavior change rather than relying on partial coverage; request the full gate from the user when final coverage/checkstyle validation is needed. Coverage failures do not automatically mean implementation-detail tests should be added. Prefer behavior tests that exercise observable outcomes through public APIs, commands, persisted files, output, or domain results. Avoid tests that only assert Spring annotations, bean counts, constructor wiring, delegation, collaborator call order, or other framework/internal implementation details. If a class has no meaningful behavior beyond wiring or delegation, either cover the user-visible behavior at a higher boundary or remove the test instead of preserving fragile coverage-only assertions. Do not break the TDD loop by introducing several behaviors at once in a new test class; avoid mixing “closing coverage” with “discovering design”, because that creates steps large enough for real bugs to hide inside them. Keep assertions explicit in the test body instead of encapsulating them in helper methods, because inline assertions improve readability and failure diagnosis. Structure test methods with clear Given/When/Then blocks separated by a blank line. Keep `Then` focused on assertions only; do not build `expected` values after `When`. Keep trivial expected values inline. Do not extract helpers for one-step transforms or direct projections (for example `path.toString()`, simple constant concatenation, or single-field mapping). Extract helper methods for expected values only when the structure is complex (for example multi-line content, derived path trees, or large object graphs) or when the helper is reused by two or more tests. If a helper is introduced during RED only to satisfy compilation, remove or inline it during GREEN unless it clearly improves readability. Heuristic: when a helper body is a single expression and has a single call site in the same test class, inline it. Avoid computing expected values with production decision logic. When a test introduces a seam (for example, an overload for deterministic environment control), that seam must be connected to the production path or removed; avoid test-only methods that are never exercised by real runtime flow.

## Commit & Pull Request Guidelines

Recent history follows Conventional Commits with scopes, for example `feat(bootstrap): validate extension metadata kind` and `refactor(bootstrap): extract runtime metadata parser`. Keep commits focused and use `type(scope): imperative summary`. For pull requests, include a short problem/solution description, link the relevant issue when one exists, and list the verification you ran locally. Screenshots are only useful when command output or documentation rendering changed.

## Configuration & Architecture Notes

The CLI loads external overrides from `~/.config/seed4j-cli/config.yml`; document any new configuration keys in both code and `README.md`. If you change command flow or boundaries, update the relevant files in `documentation/`, especially the hexagonal architecture and commands guides.

## LLM-First CLI Design

Seed4J CLI is intended to be used by LLM agents as well as humans. Design CLI behavior, help text, examples, and errors to reduce model interpretation variance.

CLI help must be clear, prescriptive, and machine-friendly, but help text alone is not considered enough for reliable LLM behavior. Prefer intent-shaped commands, explicit defaults, structured output, dry-run or plan modes, and validation errors that tell the caller the safe next action.

Avoid ambiguous negative options for important behavior when possible. If a negative option exists, document when to use it and when not to use it. For project initialization, the normal Seed4J behavior is to initialize Git if needed and create the Seed4J commit; `--no-commit` is only for callers that explicitly do not want Seed4J to create a Git repository or commit.

MCP may be added later as a structured intent layer over the CLI, especially for LLM clients, but it should complement a well-designed CLI rather than replace it. For the detailed rationale and future design guidance, see `.agent/LLM_DESIGN_DECISIONS.md`.

Do not turn the CLI into a textual clone of `seed4j-mcp`. Keep the CLI simple, local, scriptable, and usable from any terminal; reserve rich planning workflows, prompts, resources, schemas, previews, and guided validation for MCP or for explicitly structured CLI features with clear zero-setup value.

## Sonar and Cleanup Learnings

For Sonar-specific cleanup patterns and the validated local Sonar workflow used in this repository, see `.agent/SONAR_LEARNINGS.md`.

## Agent Terminal Behavior

When the user asks to "use seed4j in the terminal" (or equivalent wording in Portuguese/English), run the `seed4j` command directly in the shell and report the real terminal output and exit code. Do not answer only with documentation or examples unless the user explicitly asks for explanation instead of execution.
