# Repository Guidelines

## Project Structure & Module Organization

`src/main/java/com/seed4j/cli` contains the CLI application, bootstrap logic, command adapters, and shared kernel code. Keep new production code inside that package tree and preserve the existing hexagonal split, especially `bootstrap`, `command/infrastructure/primary`, and `shared/*`. Runtime and Spring configuration live in `src/main/resources/config`. Tests mirror the main package layout under `src/test/java`, with support fixtures in `command/infrastructure/primary`. Reference material and architecture notes live in `documentation/`, and CI helpers live in `tests-ci/`.

## Hexagonal Architecture Boundaries

Keep hexagonal boundaries explicit. `domain` contains business rules, business types, pure domain services, and ports; it must not depend on Spring, application services, or infrastructure. `application` orchestrates use cases, authorization and transactions when applicable, and receives ports and domain types. It may construct pure domain services when that is the right shape for the use case.

`application` must not depend on concrete adapters, filesystem access, Spring configuration, `System.getProperty`, or classes in `..infrastructure..`. `infrastructure.primary` translates CLI, HTTP, and framework input into domain types and calls application services. `infrastructure.secondary` implements domain ports and encapsulates filesystem access, process execution, Spring Security, configuration loading, and other external mechanisms.

Keep interface syntax and presentation metadata out of domain code. Domain types and services must not know CLI option spelling, HTTP field names, formatted user-facing messages, help labels, examples, or completion candidates unless those values are genuine business concepts. Domain validation results should carry structured facts; primary adapters and renderers translate those facts into interface-specific diagnostics.

Map enums from external contexts explicitly and exhaustively. Do not couple contexts with `TargetType.valueOf(external.name())`, because coincidental enum names are not a stable contract.

Use types-driven development in domain modeling: create a dedicated type for each business concept. Model business concepts with Value Objects before they cross into `application` or `domain`. `primary` adapters may receive raw CLI/framework values such as `String`, `Path`, booleans, or primitive options, but they should translate those values into domain types before calling application services. Domain aggregate records must not expose multiple raw business values when dedicated domain types can express those concepts. A single-field Value Object may wrap a raw value and should validate or normalize the represented concept.

Physical locations, runtime layouts, persisted configuration files, cache directories, generated file names, and other storage details are secondary infrastructure concerns. Do not model those details as concrete domain records just to pass filesystem locations through `primary`, `application`, or `domain`. If the domain needs to use something stored externally, model the need as a domain port named after the capability or business concept, and keep the concrete path/layout resolution inside `infrastructure.secondary`.

A `Path` in the domain is acceptable only when it represents a user-visible or business-relevant value, such as an input project path, an artifact explicitly supplied by the user, or a path returned for CLI feedback. A `Path` is not acceptable in the domain when it represents hidden operational layout, such as where the CLI persists runtime metadata, active runtime files, caches, config internals, or adapter-managed storage. In those cases, expose domain concepts and ports instead, for example "active runtime extension", "runtime selection", or "install runtime artifacts", and let the secondary adapter decide which files implement them.

Reserve `composition` packages for manual composition roots that must run before Spring is available. They may assemble primary, application, domain, and secondary objects, but they must stay explicit and must not become a shortcut for mixing layer responsibilities. Once Spring is available, use ordinary Spring-managed adapters and constructor injection instead of adding a context-level `composition` package.

`Seed4JCliHome` is the domain concept for paths derived from `user.home`. Read `user.home` only in Spring configuration or pre-Spring adapters, then pass the resulting domain type inward. Keep this exception narrow: `Seed4JCliHome` may represent the CLI home concept, but hidden operational layouts derived from it should normally be resolved inside secondary adapters, not exposed as separate domain records.

## Build, Test, and Development Commands

Use Java 25 and Node.js 22+ before running the toolchain.

- `./mvnw clean package` builds the CLI JAR in `target/`.
- `./mvnw clean verify` is the complete local validation gate; it runs unit/integration tests, JaCoCo aggregation, Checkstyle, and coverage gates. Its output is large, so agents must not run it automatically.
- `./mvnw test` runs only the JUnit 5 test suite and is acceptable for narrower validation.
- `npm run prettier:check` validates formatting for Java, XML, YAML, Markdown, and JSON.
- `npm run prettier:format` rewrites supported files using the repository formatter configuration.

## Agent Validation Behavior

Agents may run smaller, targeted checks during code implementation. Before finishing a code task, run `./mvnw test` as the default agent-side gate. When the complete final gate is needed, ask the user to run `./mvnw clean verify` locally and send the exit code plus a concise summary of any relevant failure. Run that complete gate as an agent only when the user explicitly asks, preferably with output limited or redirected to a file. For documentation-only changes, run the applicable documentation and formatting checks without treating them as code tasks.

## Habit Hooks

- Before considering a code task complete, run `habit-hooks`.
- Analyze every finding during the task; do not silently ignore findings.
- Resolve enforced findings before declaring the task complete.
- Do not run `habit-snooze` or change `.habit-hooks/snooze.json` without explicit user authorization.

## Coding Style & Naming Conventions

### Formatting and naming

Follow `.editorconfig`: 2-space indentation, LF line endings, UTF-8, and final newlines. Prettier is the formatter of record, including Java via `prettier-plugin-java`; run it instead of hand-formatting. Checkstyle enforces import hygiene, visibility, declaration order, and standard Java naming. Match current naming patterns: `*Command`, `*Configuration`, `*Exception`, and package names in lowercase. Prefer one public type per file. Do not use `var` in Java; prefer explicit types.

### Ports, adapters, and APIs

Name domain interfaces that represent ports by business capability, such as `RuntimeExtensionInstaller`, `RuntimeDisplayReader`, or `RuntimeModeConfigurationRepository`; do not use the `Port` suffix. Prefix secondary implementations with their mechanism, source, or context plus capability, such as `FileSystemProjectsRepository`, `BootstrapRuntimeDisplayReader`, or `BootstrapRuntimeExtensionInstaller`. Reserve `Adapter` for technical wrappers that do not implement a domain port, such as wrappers around APIs or frameworks. Do not use `@Autowired`; prefer a single explicit constructor and direct wiring.

### Types and representation of concepts

Avoid raw boolean parameters when they select a business behavior, mode, status, or strategy. Model that choice with a dedicated type, usually a small enum with named states and a predicate method, following patterns such as `BootstrapDebugMode`, `RuntimeProcessMode`, or `RuntimeExtensionReplacementStatus`. Use private records for implementation-local progress or context types, and promote them only when they represent real domain concepts.

### Helpers and immutable flow

In production code, order private helper methods immediately after their first use; when a helper is used from multiple places, place it after the earliest caller that introduces it. Avoid local variables that merely rename a single-use accessor result before immediately forwarding it. Inline the accessor when the invocation remains readable. Keep a named intermediate when it expresses a meaningful business step, is reused, avoids repeated work or side effects, or prevents excessive nesting.

Do not use mutable out-parameters, such as passing `List`, `Set`, or holder objects into helpers so they can append progress by side effect. Prefer returning an explicit result or progress type and threading it through loops or recursion. When a helper applies an ordered sequence of transformations to an immutable progress or result object, prefer a named fold over `forEach` with external state. A stream is appropriate when each element maps to a clearly named `Function<Progress, Progress>` composed with `Function::andThen` or an equally explicit fold, but only when the helper names make the data flow clearer than an explicit loop. Do not use streams to hide mutation.

### Predicates, optionals, and validation

Avoid `is*` prefixes for boolean methods; prefer names that express the business predicate directly, such as `atLeast(...)`, `missingBootInfClasses(...)`, or `standardMode(...)`. When a branch uses the opposite of a project-owned predicate to express a business decision, expose a predicate that names the branch condition directly; for example, prefer `if (dependencyPlan.notReady())` over `if (!dependencyPlan.ready())`. Direct negation remains appropriate for standard-library and technical predicates when a wrapper would not add business meaning.

Prefer `Optional` over direct `null` comparisons when modeling optional values. When validating mandatory references in Java, do not use `Objects.requireNonNull`; use `Assert.notNull("fieldName", value)` from `src/main/java/com/seed4j/cli/shared/error/domain/Assert.java`.

## Testing Guidelines

### Framework, location, and meta-annotations

This project uses JUnit 5 with Spring Boot test support. Name test classes `*Test.java` and keep them in the mirrored package for the class under test. Reuse `@UnitTest`, `@ComponentTest`, and `@IntegrationTest` to signal scope consistently.

### Observable boundaries and coverage

Add tests with every behavior change. The complete validation gate fails on uncovered lines or branches, but coverage failures do not justify implementation-detail tests. Prefer tests that exercise observable outcomes through public APIs, commands, persisted files, output, or domain results. Exercise the relevant states, including predicate branches, through observable behavior instead of unit tests written solely for a predicate method.

Tests should use the same intentional API shape as production code. Avoid tests that only assert Spring annotations, bean counts, constructor wiring, delegation, collaborator call order, or other framework and implementation details. If a class has no meaningful behavior beyond wiring or delegation, cover user-visible behavior at a higher boundary or remove the test instead of preserving fragile coverage-only assertions. Request the complete gate from the user when final coverage and Checkstyle validation is needed.

### Given/When/Then and assertions

Keep assertions explicit in the test body to improve readability and failure diagnosis. Structure test methods with clear Given/When/Then blocks separated by a blank line. Keep `Then` focused on assertions only, and define nontrivial expected values before `When`. Keep trivial expected values inline.

### Helpers, expected values, and seams

Do not extract helpers for one-step transforms or direct projections, such as `path.toString()`, simple constant concatenation, or single-field mapping. Extract expected-value helpers only for complex structures, such as multi-line content, derived path trees, or large object graphs, or when reused by two or more tests. When a helper is a single expression with a single call site in the same test class, inline it. Avoid computing expected values with production decision logic.

Do not add overloaded constructors, overloaded methods, factories, or defaulting shortcuts only to make tests shorter or more convenient. If a test needs deterministic or alternate behavior, introduce a meaningful production concept connected to the runtime path or test through observable public behavior. Remove seams and test-only methods that the real runtime flow never exercises.

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
