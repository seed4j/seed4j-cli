# Restore the root `--debug` flag and move the runtime mode policy into the domain

## Purpose and success

PR #511 (merged in `main` as `f503bbf`, "feat(runtime)!: adopt the Seed4J engine") removed the root `--debug` option
together with the runtime extension loader. Users rely on `seed4j --debug <command>` to watch Spring Boot start: debug
logs and the `CONDITIONS EVALUATION REPORT`. After the PR, Spring Boot still prints those diagnostics (Spring Boot itself
turns any `--debug` argument into `debug=true`), but Picocli then rejects the token and the command ends with exit code
`2`. The same PR also left the "only `standard` is a supported runtime mode" business rule inside a secondary adapter and
dropped mandatory reference checks from three bootstrap constructors.

Success means:

1. `seed4j --debug <command>` runs the command normally (same exit code and output as without the flag) and additionally
   shows Spring Boot debug diagnostics, including `CONDITIONS EVALUATION REPORT`.
2. `seed4j --help` lists `--debug` with a description that matches what it does.
3. The decision about which configured runtime mode is supported lives in `bootstrap.domain`; the YAML adapter only reads
   and reports what is configured. User-visible messages and exit codes for invalid or retired modes stay byte-identical.
4. The bootstrap constructors validate mandatory references with `Assert.notNull` again.
5. Documentation and release notes describe the restored flag and no longer claim it was removed.

## Context and limits

Repository root: `/home/renanfranca/projects/seed4j-cli`. Start from an up-to-date `main` (contains `f503bbf`). Read
`AGENTS.md` first; its hexagonal rules, naming rules, testing rules, and validation rules apply to every step.

Relevant current files:

- `src/main/java/com/seed4j/cli/command/infrastructure/primary/Seed4JCommandsFactory.java`: builds the root Picocli
  `CommandSpec`. On the revision before the PR (`9987cf1`) it declared
  `OptionSpec.builder("--debug").description("Enable runtime bootstrap diagnostics (extension mode only)").type(Boolean.class).defaultValue("false")`.
  Inspect it with `git show 9987cf1:src/main/java/com/seed4j/cli/command/infrastructure/primary/Seed4JCommandsFactory.java`.
- `src/main/java/com/seed4j/cli/bootstrap/application/PreSpringBootstrapApplicationService.java`: pre-Spring flow. Calls
  `runtimeConfiguration.requireStandardMode()`, catches `InvalidRuntimeConfigurationException`, prints through
  `BootstrapOutput.runtimeConfigurationError(String)` and returns `1`.
- `src/main/java/com/seed4j/cli/bootstrap/domain/RuntimeConfiguration.java`: port with `void requireStandardMode()`.
- `src/main/java/com/seed4j/cli/bootstrap/infrastructure/secondary/FileSystemRuntimeConfiguration.java`: reads
  `~/.config/seed4j-cli/config.yml` with SnakeYAML, validates structure, and also applies the mode policy (`extension` is
  retired, anything other than `standard` is invalid, trimmed and case-insensitive).
- `src/main/java/com/seed4j/cli/bootstrap/domain/BootstrapOutput.java` and
  `src/main/java/com/seed4j/cli/bootstrap/infrastructure/secondary/SystemErrBootstrapOutput.java`: stderr rendering.
- `src/main/java/com/seed4j/cli/bootstrap/infrastructure/secondary/JavaProcessChildLauncher.java`: child process command.
- `src/main/java/com/seed4j/cli/bootstrap/composition/PreSpringBootstrapConfiguration.java`: manual pre-Spring wiring.
- Tests: `src/test/java/com/seed4j/cli/bootstrap/infrastructure/primary/PreSpringBootstrapPrimaryTest.java` (observable
  pre-Spring behavior through `PrimaryBootstrapFixture`), `src/test/java/com/seed4j/cli/command/infrastructure/primary/Seed4JCommandsFactoryTest.java`
  (root help and version), `src/test/java/com/seed4j/cli/command/infrastructure/primary/Seed4JPackagedJarIT.java`
  (runs the packaged JAR as a real process; executed by Failsafe during `./mvnw verify`, not by `./mvnw test`),
  `BashCompletionScriptGeneratorTest.java` and `CompletionCommandTest.java` (completion output may now include the root
  option).
- Docs: `documentation/release-notes.md`, `documentation/Commands.md` (section `## Options and Parameters`, line ~611),
  `documentation/hexagonal-architecture.md` (section `### Engine bootstrap and project history`, line ~139), `README.md`
  if it lists root options.

Planning observations (read-only, on the merged code, packaged JAR in `target/`):

- `java -Duser.home=<empty dir> -jar target/seed4j-cli-0.0.0-SNAPSHOT.jar --debug list` printed Spring Boot `DEBUG`
  lines and `CONDITIONS EVALUATION REPORT`, then `Unknown option: '--debug'` with usage, exit `2`.
- `... list --debug` also printed `Unknown option: '--debug'`, exit `2`. Before the PR the option was declared only on the
  root, so this placement was already rejected.

Limits:

- Do not reintroduce `BootstrapDiagnostics`, `BootstrapDebugMode`, `LogbackBootstrapDiagnostics`, or any extension-mode
  code. The flag relies on Spring Boot's native `--debug` handling; the CLI only declares the option.
- Do not change `Seed4JCliHomeConfiguration` (see Decisions).
- Do not move the launch orchestration (child, packaged, fallback) out of `PreSpringBootstrapApplicationService`.
- Keep all existing exit codes and stderr messages for runtime configuration errors unchanged.

## Decisions

1. **`--debug` uses Spring Boot's native debug mode and stays root-only.** The CLI declares the option so Picocli accepts
   it; it adds no logging properties. Alternatives considered: adding `logging.level.com.seed4j=DEBUG` in
   `SpringBootLocalCliRunner` (not requested, the user wants the Spring Boot execution view), or an inherited option on
   every subcommand (user chose root-only to keep the pre-PR contract). Consequence: `seed4j --debug list` works,
   `seed4j list --debug` keeps failing with exit `2`, and the help text must describe Spring Boot diagnostics instead of
   extension mode.
2. **Keep `Seed4JCliHomeConfiguration` in `bootstrap.infrastructure.secondary`.** `AGENTS.md` states: "Read `user.home`
   only in Spring configuration or pre-Spring adapters, then pass the resulting domain type inward", and lists
   configuration loading as a secondary concern. The bean existed before the PR in `RuntimeExtensionSpringConfiguration`;
   the PR only extracted it. Moving the `@Value("${user.home}")` into `JavaSeed4JCliHomeReader` (a primary component)
   was considered and rejected because it would contradict that rule.
3. **Split reading from policy for the runtime mode.** The secondary adapter keeps file access and YAML structure errors
   (unreadable file, malformed YAML, root not a map, `seed4j` not a map, `mode` not a string) and keeps throwing
   `InvalidRuntimeConfigurationException` for them. The supported-mode policy moves into the domain and returns
   structured facts; the stderr renderer formats the message. This follows `AGENTS.md`: domain validation results carry
   structured facts, primary or rendering adapters format interface-specific diagnostics.

## Milestones

### Milestone 1: restore the root `--debug` flag

Behavior: `seed4j --debug <command>` behaves like `<command>` plus Spring Boot diagnostics; root help documents it.

Edits:

- `Seed4JCommandsFactory.java`: add back the root option `--debug`, `type(Boolean.class)`, `defaultValue("false")`, no
  inherited scope, with description `Show Spring Boot startup diagnostics (debug logs and auto-configuration report).`
- `Seed4JCommandsFactoryTest.java`, test first:
  - extend the root help test to assert `--debug` and the exact description text (keep the existing `-h, --help` and
    `-V, --version` assertions; if Picocli changes column alignment, update the expected strings to the new real output
    and note it in Progress);
  - add `shouldAcceptDebugFlagInRootCommand`: `--debug --version` returns `0` and prints `Seed4J CLI v1` and
    `Seed4J version: 2` (same shape as the pre-PR test at `9987cf1`).
- `Seed4JPackagedJarIT.java`, add one behavior test using the existing `cli(...)` helper and empty effective home:
  - Given no config; When `cli("list")` and `cli("--debug", "list")`; Then both exit `0`, both outputs contain the
    `init` module line (`(?m)^  init\s`), the `--debug` run contains `CONDITIONS EVALUATION REPORT` and the plain run
    does not. Confirm during implementation which stream (`out` or `err`) carries the Spring console log and assert on
    that stream; record it in Progress.
  - When `cli("list", "--debug")`; Then exit `2` and `err` contains `Unknown option: '--debug'` (documents root-only).
- Completion tests: if root completion candidates now include `--debug`, update expectations to the real output.

Validation:

- `./mvnw test -Dtest='Seed4JCommandsFactoryTest,BashCompletionScriptGeneratorTest,CompletionCommandTest'` passes.
- The IT is verified in the final gate (`./mvnw clean verify`).

Acceptance: root help shows the flag and description; `--debug --version` exits `0` with normal version text; packaged
`--debug list` exits `0`, lists modules, and shows the report; packaged `list` without the flag has no report.

### Milestone 2: move the supported runtime mode policy into the domain

Behavior: unchanged for users. Same exit code `1`, same stderr text, nothing launched, configuration untouched.

First, characterize the exact current messages in `PreSpringBootstrapPrimaryTest.java` (add before refactoring and run
them green on the current code):

- `mode: extension` → exit `1`, output contains exactly
  `Loading runtime extension JARs is no longer supported. Set seed4j.runtime.mode: standard in ~/.config/seed4j-cli/config.yml.`
- `mode: corporate` → exit `1`, output contains exactly
  `Invalid ~/.config/seed4j-cli/config.yml: Invalid seed4j.runtime.mode 'corporate'. Valid value: standard.`
- `mode: "  Standard  "` → accepted, child runtime launched, exit `0` (trim and case-insensitivity are part of the
  current contract in `FileSystemRuntimeConfiguration.validateMode`).
- `mode: EXTENSION` → exit `1` with the retired-extension message.

Skip any of these that an existing test already asserts with the same precision.

Edits:

- `bootstrap/domain/ConfiguredRuntimeMode.java` (new record): the runtime mode value read from user configuration,
  wrapping `Optional<String>`; factories for "not configured" and "configured value"; method `RuntimeModeSupport support()`
  that trims and compares case-insensitively: absent or `standard` → `SUPPORTED`, `extension` → `RETIRED_EXTENSION`,
  anything else → `UNRECOGNIZED`. Keep the raw value available for rendering.
- `bootstrap/domain/RuntimeModeSupport.java` (new enum): `SUPPORTED`, `RETIRED_EXTENSION`, `UNRECOGNIZED`, with the
  predicate `unsupported()`.
- Replace port `RuntimeConfiguration` by `bootstrap/domain/RuntimeModeConfigurationReader.java` with
  `ConfiguredRuntimeMode configuredMode()`. Delete `RuntimeConfiguration.java`.
- Rename `FileSystemRuntimeConfiguration` to `FileSystemRuntimeModeConfigurationReader`, implementing the new port. It
  returns the configured mode and keeps throwing `InvalidRuntimeConfigurationException` only for read and structure
  errors, with the same messages as today. Remove the `extension` and `standard` checks from it.
- `BootstrapOutput`: add `void unsupportedRuntimeMode(ConfiguredRuntimeMode mode)`. `SystemErrBootstrapOutput` renders
  the two exact messages above from `mode.support()` and the raw value.
- `PreSpringBootstrapApplicationService`: read the configured mode; if `support().unsupported()`, call
  `bootstrapOutput.unsupportedRuntimeMode(mode)` and return `1`; keep the existing `InvalidRuntimeConfigurationException`
  catch for read and structure errors.
- `PreSpringBootstrapConfiguration`: wire the renamed adapter.
- Domain behavior for `ConfiguredRuntimeMode.support()` is covered through the primary scenarios above; add a small
  domain test only if a branch stays uncovered.

Validation:

- `./mvnw test -Dtest='PreSpringBootstrapPrimaryTest,PreSpringBootstrapCompositionTest'` passes, including the new
  characterization tests, before and after the refactor.
- `./mvnw test -Dtest=HexagonalArchTest` passes.

Acceptance: `grep -n '"extension"\|"standard"' src/main/java/com/seed4j/cli/bootstrap/infrastructure/secondary/FileSystemRuntimeModeConfigurationReader.java`
returns nothing; the policy lives in `ConfiguredRuntimeMode`; all characterization tests stay green with unchanged
expected strings.

### Milestone 3: restore mandatory reference checks

Edits: add `Assert.notNull("<parameter name>", value)` for every constructor parameter in
`PreSpringBootstrapApplicationService`, `JavaProcessChildLauncher`, and `FileSystemRuntimeModeConfigurationReader`,
following the style used in `SpringBootLocalCliRunner`. Do not add tests that only assert null checks.

Validation: `./mvnw test -Dtest='PreSpringBootstrap*Test,JavaProcessChildLauncherTest'` passes.

### Milestone 4: documentation

- `documentation/release-notes.md`: replace "Loading runtime extension JARs and the associated `--debug` option have been
  removed from both channels." with a statement that runtime extension JAR loading was removed and that `seed4j --debug
<command>` remains available and shows Spring Boot startup diagnostics.
- `documentation/Commands.md`, section `## Options and Parameters`: add `--debug` as a root option, placed before the
  command (`seed4j --debug list`), described as Spring Boot debug logs plus the auto-configuration report; state that it
  is not accepted after the subcommand.
- `documentation/hexagonal-architecture.md`, section `### Engine bootstrap and project history`: replace "forwards its
  effective Java home" with "forwards its effective user home (`user.home`)", which is what `JavaProcessChildLauncher`
  passes (`-Duser.home=...`).
- `README.md`: update only if it lists root options.

Validation: `npm run prettier:check` passes; `grep -rn "debug" documentation README.md` shows no claim that the flag was
removed or is extension-only.

## Progress

- [x] Milestone 1: root `--debug` restored with tests. Deviation: the planned `--debug --version` test passed before the
      option existed, because Picocli tolerates unknown options when version help is requested. It was replaced in
      `Seed4JCommandsFactoryTest` by `shouldRunCommandWhenDebugFlagPrecedesIt` (`--debug list` exits `0` and lists
      `init`; red before the change with exit `2`) and `shouldRejectDebugFlagAfterCommand` (`list --debug` exits `2` with
      `Unknown option: '--debug'`). Root help column alignment did not change. Completion tests needed no update. The
      Spring Boot console log, including `CONDITIONS EVALUATION REPORT`, goes to `out`; Picocli usage errors go to `err`.
      `Seed4JPackagedJarIT.shouldShowSpringBootDiagnosticsOnlyWhenDebugFlagPrecedesTheCommand` asserts on those streams.
- [x] Milestone 2: runtime mode policy in the domain (`ConfiguredRuntimeMode`, `RuntimeModeSupport`,
      `RuntimeModeConfigurationReader`, `FileSystemRuntimeModeConfigurationReader`). Characterization test
      `shouldExplainUnsupportedRuntimeModeBeforeLaunchingAnything` (`extension`, `EXTENSION`, `corporate`) and the
      `"  Standard  "` scenario were green before and after the refactor with unchanged expected strings. The acceptance
      `grep` on the reader returns nothing.
- [x] Milestone 3: `Assert.notNull` restored in `PreSpringBootstrapApplicationService`, `JavaProcessChildLauncher`, and
      `FileSystemRuntimeModeConfigurationReader`.
- [x] Milestone 4: documentation reconciled (`release-notes.md`, `Commands.md`, `hexagonal-architecture.md`; the latter
      also names `ConfiguredRuntimeMode` as the owner of the mode decision). `README.md` lists no root options and was
      left unchanged.
- [ ] Final validation: steps 1, 2, 3 and 5 passed; step 4 is blocked (see below).

Observed final validation (2026-11-22):

1. `./mvnw test`: exit `0`, 535 tests, 0 failures.
2. `./mvnw clean verify`: exit `0`; `Seed4JPackagedJarIT` ran 8 tests with 0 failures, including the new test; "All
   coverage checks have been met"; `BUILD SUCCESS`. The log was written outside `target/` because `clean` deletes
   `target/verify.log` while it is being written.
3. `npm run prettier:check`: exit `0`.
4. `habit-hooks`: not run. The command is not installed on this machine and `uv` is unavailable, so the gate could not
   be executed. Delivery (commit and push) is paused until it runs.
5. Packaged JAR with an empty home: `--debug list` exit `0`, lists `init`, contains `CONDITIONS EVALUATION REPORT`;
   `list` exit `0`, lists `init`, no report.

## Validation

Executor obligations before delivery:

- Recheck each expectation against its source separately from checking the tests: the pre-PR option at `9987cf1`
  (`Seed4JCommandsFactory.java`, `Seed4JCommandsFactoryTest.java`), the current messages in
  `FileSystemRuntimeConfiguration.java` on `main`, and the rules in `AGENTS.md`.
- For each acceptance criterion record the command, the observed result, and the asserting test. A green suite alone does
  not establish a criterion. Record any gap or failed check before delivery.

Final validation, in this order (the user authorizes the complete gate through approval of this plan):

1. `./mvnw test` exits `0`.
2. `./mvnw clean verify > target/verify.log 2>&1; echo $?` exits `0`. This runs `Seed4JPackagedJarIT`, Checkstyle, and
   the JaCoCo coverage gates. On failure, report the relevant excerpt from `target/verify.log`.
3. `npm run prettier:check` exits `0`.
4. `habit-hooks` reports no unresolved enforced finding.
5. Manual check on the packaged JAR with an empty home in a temporary directory:
   `java -Duser.home=<tmp> -jar target/seed4j-cli-*.jar --debug list` exits `0`, lists modules, shows
   `CONDITIONS EVALUATION REPORT`; without `--debug` the report is absent.

Expected evidence (planned, not yet observed): all five commands exit `0`; new tests named in Milestones 1 and 2 appear
in the Surefire and Failsafe reports.
