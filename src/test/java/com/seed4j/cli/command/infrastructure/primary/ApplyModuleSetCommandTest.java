package com.seed4j.cli.command.infrastructure.primary;

import static com.seed4j.cli.command.infrastructure.primary.CliFixture.commandLine;
import static com.seed4j.cli.command.infrastructure.primary.CliFixture.setupEmptyProjectTestFolder;
import static com.seed4j.cli.command.infrastructure.primary.CliFixture.setupProjectTestFolder;
import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.IntegrationTest;
import com.seed4j.cli.command.application.ModuleSetExecutionApplicationService;
import com.seed4j.cli.command.application.ModuleSetPlanningApplicationService;
import com.seed4j.cli.command.application.RuntimeDisplayApplicationService;
import com.seed4j.cli.command.domain.RuntimeDisplay;
import com.seed4j.cli.command.domain.moduleset.ModuleSetCatalog;
import com.seed4j.cli.command.domain.moduleset.ModuleSetGitState;
import com.seed4j.cli.command.domain.moduleset.ModuleSetHistoryParameters;
import com.seed4j.cli.command.domain.moduleset.ModuleSetIntegerParameterValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModule;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanningHistory;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanningHistoryReader;
import com.seed4j.cli.command.domain.moduleset.ModuleSetProjectPathStatus;
import com.seed4j.cli.command.domain.moduleset.ModuleSetProjectPathValidator;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDefaultValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDefinition;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDescription;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyKey;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyRequirement;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyType;
import com.seed4j.cli.command.domain.moduleset.ModuleSetSlug;
import com.seed4j.cli.command.domain.moduleset.ModuleSetStringParameterValue;
import com.seed4j.cli.command.infrastructure.secondary.JGitModuleSetGitStateReader;
import com.seed4j.cli.command.infrastructure.secondary.NioModuleSetProjectPathValidator;
import com.seed4j.cli.command.infrastructure.secondary.ProjectsModuleSetPlanningHistoryReader;
import com.seed4j.cli.command.infrastructure.secondary.Seed4JModuleSetCatalog;
import com.seed4j.module.application.Seed4JModulesApplicationService;
import com.seed4j.module.infrastructure.secondary.git.GitTestUtil;
import com.seed4j.project.application.ProjectsApplicationService;
import com.seed4j.project.domain.ProjectPath;
import com.seed4j.project.domain.history.ProjectAction;
import com.seed4j.project.domain.history.ProjectActionToAppend;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import picocli.CommandLine;

@ExtendWith(OutputCaptureExtension.class)
@IntegrationTest
@DisplayName("apply-set")
class ApplyModuleSetCommandTest {

  @Autowired
  private ProjectsApplicationService projects;

  @Autowired
  private Seed4JModulesApplicationService modules;

  @Autowired
  private Seed4JCommandsFactory commandsFactory;

  @Nested
  class CommandContract {

    @Test
    void shouldRegisterExecutableModuleSetCommand(CapturedOutput output) {
      String[] args = { "--help" };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(output).contains("apply-set").contains("Apply a validated set of Seed4J modules sequentially");
    }

    @Test
    void shouldExposePlanningAndCommitOptions(CapturedOutput output) {
      String[] args = { "apply-set", "--help" };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(output)
        .contains("<module-slug>...")
        .contains("--project-path")
        .contains("--plan")
        .contains("--project-name")
        .contains("--package-name")
        .contains("--[no-]commit");
    }

    @Test
    void shouldKeepEarlierModuleSetCommandExecutableAfterBuildingAnotherCommandTree(CapturedOutput output) {
      ModuleSetPlanningApplicationService planning = planningService(
        new Seed4JModuleSetCatalog(modules),
        new ProjectsModuleSetPlanningHistoryReader(projects)
      );
      ApplyModuleSetCommand applyModuleSetCommand = applyModuleSetCommand(planning);
      Seed4JCommandsFactory factory = new Seed4JCommandsFactory(
        List.of(applyModuleSetCommand),
        new Seed4JVersionProvider("1", "2", new RuntimeDisplayApplicationService(RuntimeDisplay::standard))
      );
      CommandLine earlierCommandLine = new CommandLine(factory.buildCommandSpec());
      factory.buildCommandSpec();
      String[] args = {
        "apply-set",
        "init",
        "--project-name",
        "Sample application",
        "--base-name",
        "sampleApplication",
        "--node-package-manager",
        "npm",
        "--plan",
      };

      int exitCode = earlierCommandLine.execute(args);

      assertThat(exitCode).isZero();
      assertThat(output).contains("Status: VALID").contains("No changes were applied.");
    }

    @Test
    void shouldRequireAtLeastOneModuleSetSlug(CapturedOutput output) {
      String[] args = { "apply-set", "--plan" };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isEqualTo(2);
      assertThat(output.getErr()).contains("Missing required parameter: '<module-slug>...'");
    }

    @Test
    void shouldTreatHiddenModuleSetSlugAsUnknown(CapturedOutput output) {
      String[] args = { "apply-set", "runtime-extension-list-only", "--plan" };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isEqualTo(2);
      assertThat(output.getErr()).contains("Unknown requested modules: runtime-extension-list-only");
    }
  }

  @Nested
  class PlanValidation {

    @Test
    void shouldReportDuplicateAndUnknownModuleSetSlugsTogether(CapturedOutput output) {
      String[] args = { "apply-set", "init", "unknown-module", "init", "another-unknown", "--plan" };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isEqualTo(2);
      assertThat(output.getErr())
        .contains("Duplicate requested modules: init")
        .contains("Unknown requested modules: another-unknown, unknown-module")
        .contains("No changes were applied.");
    }

    @Test
    void shouldRejectDuplicateModuleSetSlugBeforePlanning(CapturedOutput output) {
      String[] args = { "apply-set", "init", "init", "--plan" };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isEqualTo(2);
      assertThat(output.getErr())
        .contains("Duplicate requested modules: init")
        .contains(
          """
          Execution order:

          Dependency validation:
          """
        )
        .contains("Status: INVALID")
        .contains("No changes were applied.");
    }

    @ParameterizedTest
    @MethodSource("invalidProjectPaths")
    void shouldReportInvalidProjectPathWithoutApplyingModules(ModuleSetProjectPathStatus pathStatus, String diagnostic) {
      ModuleSetSlug module = new ModuleSetSlug("module");
      ModuleSetCatalog catalog = catalog(List.of(new ModuleSetModule(module, List.of(), List.of(), Optional.empty())), List.of(module));
      ModuleSetProjectPathValidator projectPathValidator = projectPath -> pathStatus;
      ModuleSetPlanningApplicationService planning = new ModuleSetPlanningApplicationService(
        catalog,
        projectPath -> new ModuleSetPlanningHistory(Set.of(), new ModuleSetHistoryParameters(Map.of(), List.of())),
        projectPathValidator,
        projectPath -> ModuleSetGitState.NO_WORKTREE
      );
      List<ModuleSetSlug> invokedModules = new ArrayList<>();
      ApplyModuleSetCommand command = new ApplyModuleSetCommand(
        planning,
        new ModuleSetExecutionApplicationService(application -> invokedModules.add(application.slug()))
      );
      StringWriter stdout = new StringWriter();
      StringWriter stderr = new StringWriter();
      CommandLine commandLine = new CommandLine(command.spec());
      commandLine.setOut(new PrintWriter(stdout));
      commandLine.setErr(new PrintWriter(stderr));

      int exitCode = commandLine.execute("module");

      assertThat(exitCode).isEqualTo(2);
      assertThat(invokedModules).isEmpty();
      assertThat(stdout.toString()).isEmpty();
      assertThat(stderr).hasToString(
        """
        Preflight: INVALID
        Plan for module set

        Project path: .

        Requested modules:
          1. module

        Execution order:
          1. module

        Dependency validation:
          ✓ No dependencies.

        Resolved parameters:
          (none)

        Commit mode: one commit per succeeded module

        Validation problems:
          ○ %s

        Status: INVALID
        No changes were applied.
        """.formatted(diagnostic)
      );
    }

    private static Stream<Arguments> invalidProjectPaths() {
      return Stream.of(
        Arguments.of(ModuleSetProjectPathStatus.NOT_DIRECTORY, "Project path exists but is not a directory"),
        Arguments.of(ModuleSetProjectPathStatus.NOT_ACCESSIBLE, "Project path is not traversable and writable"),
        Arguments.of(
          ModuleSetProjectPathStatus.NOT_APPARENTLY_CREATABLE,
          "Project path does not have a traversable, writable directory ancestor"
        )
      );
    }

    @Test
    void shouldRejectSameSizeExecutionOrderContainingAnotherModule() {
      ModuleSetSlug first = new ModuleSetSlug("first");
      ModuleSetSlug second = new ModuleSetSlug("second");
      ModuleSetSlug replacement = new ModuleSetSlug("replacement");
      ModuleSetCatalog catalog = new ModuleSetCatalog() {
        @Override
        public List<ModuleSetModule> modules() {
          return List.of(
            new ModuleSetModule(first, List.of(), List.of(), Optional.empty()),
            new ModuleSetModule(second, List.of(), List.of(), Optional.empty()),
            new ModuleSetModule(replacement, List.of(), List.of(), Optional.empty())
          );
        }

        @Override
        public List<ModuleSetSlug> sort(List<ModuleSetSlug> requestedModules) {
          return List.of(first, replacement);
        }
      };
      ModuleSetPlanningApplicationService planning = planningService(catalog, projectPath -> {
        throw new AssertionError("History must not be read for an unsafe execution order");
      });
      List<ModuleSetSlug> invokedModules = new ArrayList<>();
      ApplyModuleSetCommand command = new ApplyModuleSetCommand(
        planning,
        new ModuleSetExecutionApplicationService(application -> invokedModules.add(application.slug()))
      );
      StringWriter stdout = new StringWriter();
      StringWriter stderr = new StringWriter();
      CommandLine commandLine = new CommandLine(command.spec());
      commandLine.setOut(new PrintWriter(stdout));
      commandLine.setErr(new PrintWriter(stderr));

      int exitCode = commandLine.execute("first", "second", "--plan");

      assertThat(exitCode).isEqualTo(2);
      assertThat(invokedModules).isEmpty();
      assertThat(stdout.toString()).isEmpty();
      assertThat(stderr).hasToString(
        """
        Preflight: INVALID
        Plan for module set

        Project path: .

        Requested modules:
          1. first
          2. second

        Execution order:
          1. first
          2. replacement

        Dependency validation:
          ✓ No dependencies.

        Resolved parameters:
          (none)

        Commit mode: one commit per succeeded module

        Validation problems:
          ○ Calculated execution order does not contain exactly the requested modules: requested first, second; calculated first, replacement

        Status: INVALID
        No changes were applied.
        """
      );
    }

    @Test
    void shouldSeparateRequestedModuleOrderFromLandscapeExecutionOrder(CapturedOutput output) {
      String[] args = {
        "apply-set",
        "maven-java",
        "init",
        "--project-name",
        "Sample application",
        "--base-name",
        "sampleApplication",
        "--node-package-manager",
        "npm",
        "--package-name",
        "com.mycompany.sample",
        "--plan",
      };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(output)
        .contains(
          """
          Requested modules:
            1. maven-java
            2. init

          Execution order:
            1. init (reapplied)
            2. maven-java (reapplied)
          """
        )
        .contains("Dependency validation:")
        .contains("Resolved parameters:")
        .contains("Status: VALID")
        .contains("No changes were applied.");
    }

    @Test
    void shouldRenderDependenciesAndMissingParametersInInvalidModuleSetPlan(CapturedOutput output) throws IOException {
      Path projectPath = setupProjectTestFolder();
      String[] args = { "apply-set", "angular-core", "--project-path", projectPath.toString(), "--plan" };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isEqualTo(2);
      assertThat(output.getErr())
        .contains(
          """
          Requested modules:
            1. angular-core

          Execution order:
            1. angular-core
          """
        )
        .contains("Dependency validation:")
        .contains("○ module:init - missing; required by: angular-core")
        .contains("Resolved parameters:")
        .contains("Missing required parameters:")
        .contains("○ packageName (--package-name)")
        .contains("Status: INVALID")
        .contains("No changes were applied.");
    }

    @Test
    void shouldPlanValidModuleSetWithoutCreatingNonexistentProjectPath(CapturedOutput output) throws IOException {
      Path parent = java.nio.file.Files.createTempDirectory("seed4j-cli-apply-set-");
      Path projectPath = parent.resolve("nonexistent-project");
      String[] args = {
        "apply-set",
        "init",
        "--project-path",
        projectPath.toString(),
        "--project-name",
        "Sample application",
        "--base-name",
        "sampleApplication",
        "--node-package-manager",
        "npm",
        "--plan",
      };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(projectPath).doesNotExist();
      assertThat(output).contains("Status: VALID").contains("No changes were applied.");
    }

    @Test
    void shouldLeaveExistingEmptyProjectDirectoryUnchangedWhenPlanningModuleSet(CapturedOutput output) throws IOException {
      Path projectPath = java.nio.file.Files.createTempDirectory("seed4j-cli-apply-set-empty-");
      String[] args = {
        "apply-set",
        "init",
        "--project-path",
        projectPath.toString(),
        "--project-name",
        "Sample application",
        "--base-name",
        "sampleApplication",
        "--node-package-manager",
        "npm",
        "--plan",
      };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      try (java.util.stream.Stream<Path> entries = java.nio.file.Files.list(projectPath)) {
        assertThat(entries).isEmpty();
      }
      assertThat(projectPath.resolve(".seed4j")).doesNotExist();
      assertThat(projectPath.resolve(".git")).doesNotExist();
      assertThat(output).contains("Status: VALID").contains("No changes were applied.");
    }

    @Test
    void shouldReportOpaqueHistoryReadFailureBeforeAnyMutation() {
      ModuleSetSlug module = new ModuleSetSlug("module");
      ModuleSetCatalog catalog = catalog(List.of(new ModuleSetModule(module, List.of(), List.of(), Optional.empty())), List.of(module));
      ModuleSetPlanningApplicationService planning = planningService(catalog, projectPath -> {
        throw new IllegalStateException("sensitive history failure");
      });
      List<ModuleSetSlug> invokedModules = new ArrayList<>();
      ApplyModuleSetCommand command = new ApplyModuleSetCommand(
        planning,
        new ModuleSetExecutionApplicationService(application -> invokedModules.add(application.slug()))
      );
      StringWriter stdout = new StringWriter();
      StringWriter stderr = new StringWriter();
      CommandLine commandLine = new CommandLine(command.spec());
      commandLine.setOut(new PrintWriter(stdout));
      commandLine.setErr(new PrintWriter(stderr));

      int exitCode = commandLine.execute("module");

      assertThat(exitCode).isEqualTo(1);
      assertThat(invokedModules).isEmpty();
      assertThat(stdout.toString()).isEmpty();
      assertThat(stderr).hasToString("ERROR: Unable to complete module set preflight.\nNo changes were applied.\n");
    }

    @Test
    void shouldWriteInvalidPreflightOnlyToStderrWithoutMutation() throws IOException {
      Path projectPath = setupEmptyProjectTestFolder();
      List<Path> pathsBefore;
      try (Stream<Path> paths = java.nio.file.Files.walk(projectPath)) {
        pathsBefore = paths.map(projectPath::relativize).sorted().toList();
      }
      ModuleSetPlanningApplicationService planning = new ModuleSetPlanningApplicationService(
        new Seed4JModuleSetCatalog(modules),
        new ProjectsModuleSetPlanningHistoryReader(projects),
        new NioModuleSetProjectPathValidator(),
        new JGitModuleSetGitStateReader()
      );
      List<ModuleSetSlug> invokedModules = new ArrayList<>();
      ApplyModuleSetCommand command = new ApplyModuleSetCommand(
        planning,
        new ModuleSetExecutionApplicationService(application -> invokedModules.add(application.slug()))
      );
      StringWriter stdout = new StringWriter();
      StringWriter stderr = new StringWriter();
      CommandLine commandLine = new CommandLine(command.spec());
      commandLine.setOut(new PrintWriter(stdout));
      commandLine.setErr(new PrintWriter(stderr));
      String[] args = { "maven-java", "--project-path", projectPath.toString(), "--package-name", "com.mycompany.sample" };

      int exitCode = commandLine.execute(args);

      List<Path> pathsAfter;
      try (Stream<Path> paths = java.nio.file.Files.walk(projectPath)) {
        pathsAfter = paths.map(projectPath::relativize).sorted().toList();
      }
      assertThat(exitCode).isEqualTo(2);
      assertThat(invokedModules).isEmpty();
      assertThat(stdout.toString()).isEmpty();
      assertThat(stderr.toString())
        .startsWith("Preflight: INVALID")
        .contains("module:init - missing; required by: maven-java")
        .contains("Status: INVALID")
        .endsWith("No changes were applied.\n");
      assertThat(pathsAfter).containsExactlyElementsOf(pathsBefore);
      assertThat(projects.getHistory(new ProjectPath(projectPath.toString())).actions()).isEmpty();
      assertThat(projectPath.resolve(".git")).doesNotExist();
    }
  }

  @Nested
  class ParameterResolution {

    @Test
    void shouldResolveSharedPropertiesOnceAndSatisfyDependenciesFromRequestedSet(CapturedOutput output) throws IOException {
      Path projectPath = setupProjectTestFolder();
      String[] args = {
        "apply-set",
        "angular-core",
        "prettier",
        "init",
        "--project-path",
        projectPath.toString(),
        "--project-name",
        "Sample application",
        "--base-name",
        "sampleApplication",
        "--node-package-manager",
        "npm",
        "--package-name",
        "com.mycompany.sample",
        "--plan",
      };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(output)
        .contains(
          """
          Execution order:
            1. init
            2. prettier
            3. angular-core
          """
        )
        .contains("module:init - satisfied by requested module: init")
        .contains("module:prettier - satisfied by requested module: prettier")
        .containsOnlyOnce("✓ projectName:")
        .containsOnlyOnce("✓ baseName:")
        .containsOnlyOnce("✓ nodePackageManager:")
        .contains("Status: VALID");
      assertThat(projects.getHistory(new ProjectPath(projectPath.toString())).actions()).isEmpty();
      assertThat(GitTestUtil.getCommits(projectPath)).isEmpty();
    }

    @Test
    void shouldListVisibleFeatureCandidatesWithoutSelectingProviderImplicitly(CapturedOutput output) throws IOException {
      Path projectPath = java.nio.file.Files.createTempDirectory("seed4j-cli-apply-set-feature-");
      String[] args = { "apply-set", "seed4j-extension", "--project-path", projectPath.toString(), "--plan" };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isEqualTo(2);
      assertThat(output.getErr())
        .contains("○ feature:java-build-tool - missing; select one explicitly from: gradle-java, maven-java")
        .contains(
          """
          Execution order:
            1. seed4j-extension
          """
        )
        .contains("Status: INVALID")
        .contains("No changes were applied.");
      try (java.util.stream.Stream<Path> entries = java.nio.file.Files.list(projectPath)) {
        assertThat(entries).isEmpty();
      }
    }

    @Test
    void shouldRejectKnownPropertyOptionNotUsedBySelectedModules(CapturedOutput output) throws IOException {
      Path projectPath = java.nio.file.Files.createTempDirectory("seed4j-cli-apply-set-unused-option-");
      String[] args = {
        "apply-set",
        "init",
        "--project-path",
        projectPath.toString(),
        "--project-name",
        "Sample application",
        "--base-name",
        "sampleApplication",
        "--node-package-manager",
        "npm",
        "--package-name",
        "com.mycompany.sample",
        "--plan",
      };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isEqualTo(2);
      assertThat(output.getErr())
        .contains("Options not used by requested modules: --package-name")
        .contains("Status: INVALID")
        .contains("No changes were applied.");
      try (java.util.stream.Stream<Path> entries = java.nio.file.Files.list(projectPath)) {
        assertThat(entries).isEmpty();
      }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void shouldPlanBooleanPropertyUsingPicocliTypedValue(boolean featureEnabled, CapturedOutput output) {
      ModuleSetSlug moduleSlug = new ModuleSetSlug("boolean-module");
      ModuleSetPropertyDefinition property = new ModuleSetPropertyDefinition(
        new ModuleSetPropertyKey("featureEnabled"),
        ModuleSetPropertyType.BOOLEAN,
        ModuleSetPropertyRequirement.OPTIONAL,
        Optional.empty(),
        Optional.empty(),
        List.of()
      );
      ModuleSetCatalog catalog = catalog(
        List.of(new ModuleSetModule(moduleSlug, List.of(), List.of(property), Optional.empty())),
        List.of(moduleSlug)
      );
      String[] args = { "boolean-module", "--feature-enabled=" + featureEnabled, "--plan" };

      int exitCode = moduleSetCommandLine(
        catalog,
        new ModuleSetPlanningHistory(Set.of(), new ModuleSetHistoryParameters(Map.of(), List.of()))
      ).execute(args);

      assertThat(exitCode).isZero();
      assertThat(output)
        .contains("✓ featureEnabled: " + featureEnabled)
        .contains("Source: explicit CLI input")
        .contains("Status: VALID")
        .contains("No changes were applied.");
    }

    @Test
    void shouldRejectInvalidIntegerPropertyBeforePlanning(CapturedOutput output) {
      ModuleSetSlug moduleSlug = new ModuleSetSlug("integer-module");
      ModuleSetPropertyDefinition property = new ModuleSetPropertyDefinition(
        new ModuleSetPropertyKey("count"),
        ModuleSetPropertyType.INTEGER,
        ModuleSetPropertyRequirement.REQUIRED,
        Optional.empty(),
        Optional.empty(),
        List.of()
      );
      ModuleSetCatalog catalog = catalog(
        List.of(new ModuleSetModule(moduleSlug, List.of(), List.of(property), Optional.empty())),
        List.of(moduleSlug)
      );
      String[] args = { "integer-module", "--count", "not-a-number", "--plan" };

      int exitCode = moduleSetCommandLine(
        catalog,
        new ModuleSetPlanningHistory(Set.of(), new ModuleSetHistoryParameters(Map.of(), List.of()))
      ).execute(args);

      assertThat(exitCode).isEqualTo(2);
      assertThat(output.getErr())
        .contains("Invalid value for option '--count': 'not-a-number' is not an int")
        .doesNotContain("Plan for module set");
    }

    @Test
    void shouldRejectStringHistoryForIntegerProperty(CapturedOutput output) {
      ModuleSetSlug moduleSlug = new ModuleSetSlug("integer-module");
      ModuleSetPropertyKey indentSize = new ModuleSetPropertyKey("indentSize");
      ModuleSetPropertyDefinition property = new ModuleSetPropertyDefinition(
        indentSize,
        ModuleSetPropertyType.INTEGER,
        ModuleSetPropertyRequirement.OPTIONAL,
        Optional.empty(),
        Optional.of(new ModuleSetPropertyDefaultValue(new ModuleSetIntegerParameterValue(4), "4")),
        List.of()
      );
      ModuleSetCatalog catalog = catalog(
        List.of(new ModuleSetModule(moduleSlug, List.of(), List.of(property), Optional.empty())),
        List.of(moduleSlug)
      );
      ModuleSetPlanningHistory history = new ModuleSetPlanningHistory(
        Set.of(),
        new ModuleSetHistoryParameters(Map.of(indentSize, new ModuleSetStringParameterValue("2")), List.of())
      );
      String[] args = { "integer-module", "--plan" };

      int exitCode = moduleSetCommandLine(catalog, history).execute(args);

      assertThat(exitCode).isEqualTo(2);
      assertThat(output.getErr())
        .contains(
          "Project history parameter type mismatch: indentSize expects INTEGER but history contains STRING; pass --indent-size to override the stored value"
        )
        .doesNotContain("✓ indentSize:")
        .contains("Status: INVALID")
        .contains("No changes were applied.");
    }

    @Test
    void shouldPreferExplicitIntegerOverStringHistory(CapturedOutput output) {
      ModuleSetSlug moduleSlug = new ModuleSetSlug("integer-module");
      ModuleSetPropertyKey indentSize = new ModuleSetPropertyKey("indentSize");
      ModuleSetPropertyDefinition property = new ModuleSetPropertyDefinition(
        indentSize,
        ModuleSetPropertyType.INTEGER,
        ModuleSetPropertyRequirement.OPTIONAL,
        Optional.empty(),
        Optional.empty(),
        List.of()
      );
      ModuleSetCatalog catalog = catalog(
        List.of(new ModuleSetModule(moduleSlug, List.of(), List.of(property), Optional.empty())),
        List.of(moduleSlug)
      );
      ModuleSetPlanningHistory history = new ModuleSetPlanningHistory(
        Set.of(),
        new ModuleSetHistoryParameters(Map.of(indentSize, new ModuleSetStringParameterValue("2")), List.of())
      );
      String[] args = { "integer-module", "--indent-size", "3", "--plan" };

      int exitCode = moduleSetCommandLine(catalog, history).execute(args);

      assertThat(exitCode).isZero();
      assertThat(output)
        .contains("✓ indentSize: 3")
        .contains("Source: explicit CLI input")
        .doesNotContain("Project history parameter type mismatch")
        .contains("Status: VALID")
        .contains("No changes were applied.");
    }

    @Test
    void shouldIgnoreIncompatibleHistoryForUnselectedProperty(CapturedOutput output) {
      ModuleSetSlug selected = new ModuleSetSlug("selected-module");
      ModuleSetSlug unselected = new ModuleSetSlug("unselected-module");
      ModuleSetPropertyKey indentSize = new ModuleSetPropertyKey("indentSize");
      ModuleSetPropertyDefinition property = new ModuleSetPropertyDefinition(
        indentSize,
        ModuleSetPropertyType.INTEGER,
        ModuleSetPropertyRequirement.OPTIONAL,
        Optional.empty(),
        Optional.empty(),
        List.of()
      );
      ModuleSetCatalog catalog = catalog(
        List.of(
          new ModuleSetModule(selected, List.of(), List.of(), Optional.empty()),
          new ModuleSetModule(unselected, List.of(), List.of(property), Optional.empty())
        ),
        List.of(selected, unselected)
      );
      ModuleSetPlanningHistory history = new ModuleSetPlanningHistory(
        Set.of(),
        new ModuleSetHistoryParameters(Map.of(indentSize, new ModuleSetStringParameterValue("2")), List.of())
      );
      String[] args = { "selected-module", "--plan" };

      int exitCode = moduleSetCommandLine(catalog, history).execute(args);

      assertThat(exitCode).isZero();
      assertThat(output)
        .doesNotContain("Project history parameter type mismatch")
        .contains("Status: VALID")
        .contains("No changes were applied.");
    }

    @ParameterizedTest
    @MethodSource("unsupportedHistoryValues")
    @NullSource
    void shouldRejectRelevantUnsupportedHistoryType(Object unsupportedHistoryValue, CapturedOutput output) throws IOException {
      ModuleSetSlug moduleSlug = new ModuleSetSlug("integer-module");
      ModuleSetPropertyKey indentSize = new ModuleSetPropertyKey("indentSize");
      ModuleSetPropertyDefinition property = new ModuleSetPropertyDefinition(
        indentSize,
        ModuleSetPropertyType.INTEGER,
        ModuleSetPropertyRequirement.OPTIONAL,
        Optional.empty(),
        Optional.empty(),
        List.of()
      );
      ModuleSetCatalog catalog = catalog(
        List.of(new ModuleSetModule(moduleSlug, List.of(), List.of(property), Optional.empty())),
        List.of(moduleSlug)
      );
      Path projectPath = java.nio.file.Files.createTempDirectory("seed4j-cli-apply-set-unsupported-history-");
      Map<String, Object> parameters = new HashMap<>();
      parameters.put(indentSize.value(), unsupportedHistoryValue);
      projects.append(
        new ProjectActionToAppend(
          new ProjectPath(projectPath.toString()),
          ProjectAction.builder().module("history-fixture").date(Instant.EPOCH).parameters(parameters)
        )
      );
      ModuleSetPlanningApplicationService planning = planningService(catalog, new ProjectsModuleSetPlanningHistoryReader(projects));
      String[] args = { "integer-module", "--project-path", projectPath.toString(), "--plan" };

      int exitCode = new CommandLine(applyModuleSetCommand(planning).spec()).execute(args);

      assertThat(exitCode).isEqualTo(2);
      assertThat(output.getErr())
        .contains(
          "Project history parameter type mismatch: indentSize expects INTEGER but history contains an unsupported value type; pass --indent-size to override the stored value"
        )
        .doesNotContain("✓ indentSize:")
        .contains("Status: INVALID")
        .contains("No changes were applied.");
    }

    private static Stream<Object> unsupportedHistoryValues() {
      return Stream.of(List.of(2));
    }

    @Test
    void shouldResolveBooleanHistoryAtSeed4JBoundary(CapturedOutput output) throws IOException {
      ModuleSetSlug moduleSlug = new ModuleSetSlug("boolean-module");
      ModuleSetPropertyKey featureEnabled = new ModuleSetPropertyKey("featureEnabled");
      ModuleSetPropertyDefinition property = new ModuleSetPropertyDefinition(
        featureEnabled,
        ModuleSetPropertyType.BOOLEAN,
        ModuleSetPropertyRequirement.OPTIONAL,
        Optional.empty(),
        Optional.empty(),
        List.of()
      );
      ModuleSetCatalog catalog = catalog(
        List.of(new ModuleSetModule(moduleSlug, List.of(), List.of(property), Optional.empty())),
        List.of(moduleSlug)
      );
      Path projectPath = java.nio.file.Files.createTempDirectory("seed4j-cli-apply-set-boolean-history-");
      projects.append(
        new ProjectActionToAppend(
          new ProjectPath(projectPath.toString()),
          ProjectAction.builder().module("history-fixture").date(Instant.EPOCH).parameters(Map.of(featureEnabled.value(), true))
        )
      );
      ModuleSetPlanningApplicationService planning = planningService(catalog, new ProjectsModuleSetPlanningHistoryReader(projects));
      String[] args = { "boolean-module", "--project-path", projectPath.toString(), "--plan" };

      int exitCode = new CommandLine(applyModuleSetCommand(planning).spec()).execute(args);

      assertThat(exitCode).isZero();
      assertThat(output)
        .contains("✓ featureEnabled: true")
        .contains("Source: project history")
        .contains("Status: VALID")
        .contains("No changes were applied.");
    }

    @Test
    void shouldRejectBooleanHistoryForIntegerPropertyAtSeed4JBoundary(CapturedOutput output) throws IOException {
      ModuleSetSlug moduleSlug = new ModuleSetSlug("integer-module");
      ModuleSetPropertyKey indentSize = new ModuleSetPropertyKey("indentSize");
      ModuleSetPropertyDefinition property = new ModuleSetPropertyDefinition(
        indentSize,
        ModuleSetPropertyType.INTEGER,
        ModuleSetPropertyRequirement.OPTIONAL,
        Optional.empty(),
        Optional.empty(),
        List.of()
      );
      ModuleSetCatalog catalog = catalog(
        List.of(new ModuleSetModule(moduleSlug, List.of(), List.of(property), Optional.empty())),
        List.of(moduleSlug)
      );
      Path projectPath = java.nio.file.Files.createTempDirectory("seed4j-cli-apply-set-boolean-mismatch-");
      projects.append(
        new ProjectActionToAppend(
          new ProjectPath(projectPath.toString()),
          ProjectAction.builder().module("history-fixture").date(Instant.EPOCH).parameters(Map.of(indentSize.value(), true))
        )
      );
      ModuleSetPlanningApplicationService planning = planningService(catalog, new ProjectsModuleSetPlanningHistoryReader(projects));
      String[] args = { "integer-module", "--project-path", projectPath.toString(), "--plan" };

      int exitCode = new CommandLine(applyModuleSetCommand(planning).spec()).execute(args);

      assertThat(exitCode).isEqualTo(2);
      assertThat(output.getErr())
        .contains(
          "Project history parameter type mismatch: indentSize expects INTEGER but history contains BOOLEAN; pass --indent-size to override the stored value"
        )
        .doesNotContain("✓ indentSize:")
        .contains("Status: INVALID")
        .contains("No changes were applied.");
    }

    @Test
    void shouldAggregateHistoryTypeMismatchesAlphabetically(CapturedOutput output) {
      ModuleSetSlug moduleSlug = new ModuleSetSlug("typed-module");
      ModuleSetPropertyKey zetaCount = new ModuleSetPropertyKey("zetaCount");
      ModuleSetPropertyKey alphaEnabled = new ModuleSetPropertyKey("alphaEnabled");
      ModuleSetPropertyDefinition zetaProperty = new ModuleSetPropertyDefinition(
        zetaCount,
        ModuleSetPropertyType.INTEGER,
        ModuleSetPropertyRequirement.OPTIONAL,
        Optional.empty(),
        Optional.empty(),
        List.of()
      );
      ModuleSetPropertyDefinition alphaProperty = new ModuleSetPropertyDefinition(
        alphaEnabled,
        ModuleSetPropertyType.BOOLEAN,
        ModuleSetPropertyRequirement.OPTIONAL,
        Optional.empty(),
        Optional.empty(),
        List.of()
      );
      ModuleSetCatalog catalog = catalog(
        List.of(new ModuleSetModule(moduleSlug, List.of(), List.of(zetaProperty, alphaProperty), Optional.empty())),
        List.of(moduleSlug)
      );
      ModuleSetPlanningHistory history = new ModuleSetPlanningHistory(
        Set.of(),
        new ModuleSetHistoryParameters(
          Map.of(zetaCount, new ModuleSetStringParameterValue("2"), alphaEnabled, new ModuleSetIntegerParameterValue(1)),
          List.of()
        )
      );
      String[] args = { "typed-module", "--plan" };

      int exitCode = moduleSetCommandLine(catalog, history).execute(args);

      assertThat(exitCode).isEqualTo(2);
      assertThat(output.getErr())
        .containsOnlyOnce("Validation problems:")
        .containsSubsequence(
          "Project history parameter type mismatch: alphaEnabled expects BOOLEAN but history contains INTEGER; pass --alpha-enabled to override the stored value",
          "Project history parameter type mismatch: zetaCount expects INTEGER but history contains STRING; pass --zeta-count to override the stored value"
        )
        .contains("Status: INVALID")
        .contains("No changes were applied.");
    }

    @Test
    void shouldRenderInformationalDefaultInDetailedModuleSetPlan(CapturedOutput output) {
      ModuleSetSlug moduleSlug = new ModuleSetSlug("default-only-module");
      ModuleSetPropertyDefinition property = new ModuleSetPropertyDefinition(
        new ModuleSetPropertyKey("informationalValue"),
        ModuleSetPropertyType.STRING,
        ModuleSetPropertyRequirement.OPTIONAL,
        Optional.empty(),
        Optional.of(new ModuleSetPropertyDefaultValue(new ModuleSetStringParameterValue("informational-default"), "informational-default")),
        List.of()
      );
      ModuleSetCatalog catalog = catalog(
        List.of(new ModuleSetModule(moduleSlug, List.of(), List.of(property), Optional.empty())),
        List.of(moduleSlug)
      );
      String[] args = { "default-only-module", "--plan" };

      int exitCode = moduleSetCommandLine(
        catalog,
        new ModuleSetPlanningHistory(Set.of(), new ModuleSetHistoryParameters(Map.of(), List.of()))
      ).execute(args);

      assertThat(exitCode).isZero();
      assertThat(output.getOut())
        .contains(
          """
          Resolved parameters:
            ✓ informationalValue: informational-default
              Source: default (informational)
              CLI option: --informational-value

          Commit mode: one commit per succeeded module
          """
        )
        .contains("Status: VALID")
        .endsWith("No changes were applied.\n");
    }

    @Test
    void shouldRenderAllSharedPropertyConflicts(CapturedOutput output) {
      ModuleSetSlug first = new ModuleSetSlug("first-module");
      ModuleSetSlug second = new ModuleSetSlug("second-module");
      ModuleSetPropertyKey key = new ModuleSetPropertyKey("shared");
      ModuleSetPropertyDefinition firstDefinition = new ModuleSetPropertyDefinition(
        key,
        ModuleSetPropertyType.STRING,
        ModuleSetPropertyRequirement.OPTIONAL,
        Optional.of(new ModuleSetPropertyDescription("First description")),
        Optional.of(new ModuleSetPropertyDefaultValue(new ModuleSetStringParameterValue("first-default"), "first-default")),
        List.of()
      );
      ModuleSetPropertyDefinition secondDefinition = new ModuleSetPropertyDefinition(
        key,
        ModuleSetPropertyType.STRING,
        ModuleSetPropertyRequirement.OPTIONAL,
        Optional.of(new ModuleSetPropertyDescription("Second description")),
        Optional.of(new ModuleSetPropertyDefaultValue(new ModuleSetStringParameterValue("second-default"), "second-default")),
        List.of()
      );
      ModuleSetCatalog catalog = catalog(
        List.of(
          new ModuleSetModule(first, List.of(), List.of(firstDefinition), Optional.empty()),
          new ModuleSetModule(second, List.of(), List.of(secondDefinition), Optional.empty())
        ),
        List.of(first, second)
      );
      String[] args = { "first-module", "second-module", "--plan" };

      int exitCode = moduleSetCommandLine(
        catalog,
        new ModuleSetPlanningHistory(Set.of(), new ModuleSetHistoryParameters(Map.of(), List.of()))
      ).execute(args);

      assertThat(exitCode).isEqualTo(2);
      assertThat(output.getErr())
        .contains("Property conflicts: shared: conflicting defaults (first-default, second-default)")
        .contains("shared: conflicting descriptions (First description, Second description)")
        .contains("Status: INVALID")
        .contains("No changes were applied.");
    }
  }

  @Nested
  class Execution {

    @Test
    void shouldExecuteValidModuleSetWithInSetDependencyAndOneCommitPerModule(CapturedOutput output) throws IOException {
      Path projectPath = setupEmptyProjectTestFolder();
      String[] args = {
        "apply-set",
        "init",
        "maven-java",
        "--project-path",
        projectPath.toString(),
        "--project-name",
        "Sample application",
        "--base-name",
        "sampleApplication",
        "--node-package-manager",
        "npm",
        "--package-name",
        "com.mycompany.sample",
      };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(projects.getHistory(new ProjectPath(projectPath.toString())).actions())
        .extracting(action -> action.module().get())
        .containsExactly("init", "maven-java");
      assertThat(GitTestUtil.getCommits(projectPath)).hasLineCount(2);
      assertThat(output.getOut())
        .contains(
          """
          Preflight: VALID
          Execution order:
            1. init
            2. maven-java

          Effective parameters:
            ✓ projectName: Sample application
              Source: explicit CLI input
              CLI option: --project-name
            ✓ baseName: sampleApplication
              Source: explicit CLI input
              CLI option: --base-name
            ✓ nodePackageManager: npm
              Source: explicit CLI input
              CLI option: --node-package-manager
            ✓ packageName: com.mycompany.sample
              Source: explicit CLI input
              CLI option: --package-name

          Commit mode: one commit per succeeded module

          Applying module set:
          """
        )
        .contains("[1/2] init", "[2/2] maven-java")
        .containsOnlyOnce("  init  SUCCEEDED")
        .containsOnlyOnce("  maven-java  SUCCEEDED")
        .contains("Module set status: SUCCEEDED")
        .doesNotContain(
          "Plan for module set",
          "Project path:",
          "Requested modules:",
          "Dependency validation:",
          "default (informational)",
          "Status: VALID"
        );
    }

    @Test
    void shouldExecuteModuleSetWithoutInitializingGitWhenCommitIsDisabled(CapturedOutput output) throws IOException {
      Path projectPath = setupEmptyProjectTestFolder();
      String[] args = {
        "apply-set",
        "init",
        "maven-java",
        "--project-path",
        projectPath.toString(),
        "--project-name",
        "Sample application",
        "--base-name",
        "sampleApplication",
        "--node-package-manager",
        "npm",
        "--package-name",
        "com.mycompany.sample",
        "--no-commit",
      };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(projects.getHistory(new ProjectPath(projectPath.toString())).actions())
        .extracting(action -> action.module().get())
        .containsExactly("init", "maven-java");
      assertThat(projectPath.resolve("pom.xml")).exists();
      assertThat(projectPath.resolve(".git")).doesNotExist();
      assertThat(output.getOut())
        .contains("Commit mode: disabled; Git will not be initialized and no commits will be created")
        .contains("[1/2] init", "[2/2] maven-java")
        .contains("Module set status: SUCCEEDED");
      assertThat(output.getOut().lines().filter("      Commit: disabled"::equals)).hasSize(2);
    }

    @Test
    void shouldKeepRequestedAppliedModuleInPlanWithoutChangingHistoryOrCommits(CapturedOutput output) throws IOException {
      Path projectPath = setupProjectTestFolder();
      String[] applyArgs = {
        "apply",
        "init",
        "--project-path",
        projectPath.toString(),
        "--project-name",
        "Sample application",
        "--base-name",
        "sampleApplication",
        "--node-package-manager",
        "npm",
      };
      int applyExitCode = commandLine(modules, projects).execute(applyArgs);
      assertThat(applyExitCode).isZero();
      int historyActionsBeforePlan = projects.getHistory(new ProjectPath(projectPath.toString())).actions().size();
      String commitsBeforePlan = GitTestUtil.getCommits(projectPath);
      String[] planArgs = { "apply-set", "init", "--project-path", projectPath.toString(), "--plan" };

      int exitCode = commandLine(modules, projects).execute(planArgs);

      assertThat(exitCode).isZero();
      assertThat(projects.getHistory(new ProjectPath(projectPath.toString())).actions()).hasSize(historyActionsBeforePlan);
      assertThat(GitTestUtil.getCommits(projectPath)).isEqualTo(commitsBeforePlan);
      assertThat(output)
        .contains(
          """
          Execution order:
            1. init (reapplied)
          """
        )
        .contains("Status: VALID")
        .contains("No changes were applied.");
    }

    @Test
    void shouldReapplyRequestedModuleAlreadyPresentInHistory(CapturedOutput output) throws IOException {
      Path projectPath = setupProjectTestFolder();
      String[] applyArgs = {
        "apply",
        "init",
        "--project-path",
        projectPath.toString(),
        "--project-name",
        "Sample application",
        "--base-name",
        "sampleApplication",
        "--node-package-manager",
        "npm",
      };
      int applyExitCode = commandLine(modules, projects).execute(applyArgs);
      assertThat(applyExitCode).isZero();
      String[] applySetArgs = { "apply-set", "init", "--project-path", projectPath.toString() };

      int exitCode = commandLine(modules, projects).execute(applySetArgs);

      assertThat(exitCode).isZero();
      assertThat(projects.getHistory(new ProjectPath(projectPath.toString())).actions())
        .extracting(action -> action.module().get())
        .containsExactly("init", "init");
      assertThat(GitTestUtil.getCommits(projectPath)).hasLineCount(2);
      assertThat(output.getOut())
        .contains("1. init (reapplied)")
        .contains("[1/1] init (reapplied)")
        .contains("  init  SUCCEEDED  reapplied")
        .contains("Module set status: SUCCEEDED");
    }

    @Test
    void shouldWarnAndContinueWhenCommitEnabledWorktreeIsDirty(CapturedOutput output) throws IOException {
      Path projectPath = setupProjectTestFolder();
      java.nio.file.Files.writeString(projectPath.resolve("existing-change.txt"), "keep me");
      String[] args = {
        "apply-set",
        "init",
        "--project-path",
        projectPath.toString(),
        "--project-name",
        "Sample application",
        "--base-name",
        "sampleApplication",
        "--node-package-manager",
        "npm",
      };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(output.getErr())
        .contains(
          "WARNING: Git worktree "
            + projectPath
            + " is dirty; module commits can include or be affected by pre-existing changes. Execution will continue automatically."
        )
        .doesNotContain("ERROR:");
      assertThat(output.getOut()).contains("[1/1] init").contains("Module set status: SUCCEEDED");
    }

    @Test
    void shouldReportPartialFailureAndSkipModulesAfterFirstThrownApplication(CapturedOutput output) {
      ModuleSetSlug first = new ModuleSetSlug("first-module");
      ModuleSetSlug second = new ModuleSetSlug("second-module");
      ModuleSetSlug third = new ModuleSetSlug("third-module");
      ModuleSetCatalog catalog = catalog(
        List.of(
          new ModuleSetModule(first, List.of(), List.of(), Optional.empty()),
          new ModuleSetModule(second, List.of(), List.of(), Optional.empty()),
          new ModuleSetModule(third, List.of(), List.of(), Optional.empty())
        ),
        List.of(first, second, third)
      );
      ModuleSetPlanningApplicationService planning = planningService(catalog, projectPath ->
        new ModuleSetPlanningHistory(Set.of(), new ModuleSetHistoryParameters(Map.of(), List.of()))
      );
      List<ModuleSetSlug> invokedModules = new ArrayList<>();
      ModuleSetExecutionApplicationService failingExecution = new ModuleSetExecutionApplicationService(application -> {
        invokedModules.add(application.slug());
        if (application.slug().equals(second)) {
          throw new IllegalStateException("internal failure details");
        }
      });
      ApplyModuleSetCommand command = new ApplyModuleSetCommand(planning, failingExecution);
      String[] args = { "first-module", "second-module", "third-module" };

      int exitCode = new CommandLine(command.spec()).execute(args);

      assertThat(exitCode).isEqualTo(1);
      assertThat(invokedModules).containsExactly(first, second);
      assertThat(output.getOut())
        .contains("[1/3] first-module", "[2/3] second-module", "[3/3] third-module")
        .contains("  first-module  SUCCEEDED", "  second-module  FAILED", "  third-module  SKIPPED")
        .contains("Module set status: PARTIAL_FAILURE");
      assertThat(output.getErr())
        .contains("ERROR: second-module failed: unable to complete module application.")
        .contains("The failed module may have changed files, history, Git, dispatched events, or downstream event effects.")
        .contains(
          "Next action: inspect the working tree, project history, Git log, and relevant event effects before deciding whether to retry."
        )
        .doesNotContain("internal failure details", "IllegalStateException");
    }
  }

  private CommandLine moduleSetCommandLine(ModuleSetCatalog catalog, ModuleSetPlanningHistory history) {
    ModuleSetPlanningApplicationService planning = planningService(catalog, projectPath -> history);
    return new CommandLine(applyModuleSetCommand(planning).spec());
  }

  private ApplyModuleSetCommand applyModuleSetCommand(ModuleSetPlanningApplicationService planning) {
    return new ApplyModuleSetCommand(planning, new ModuleSetExecutionApplicationService(application -> {}));
  }

  private ModuleSetPlanningApplicationService planningService(ModuleSetCatalog catalog, ModuleSetPlanningHistoryReader historyReader) {
    return new ModuleSetPlanningApplicationService(
      catalog,
      historyReader,
      projectPath -> ModuleSetProjectPathStatus.VALID,
      projectPath -> ModuleSetGitState.NO_WORKTREE
    );
  }

  private ModuleSetCatalog catalog(List<ModuleSetModule> catalogModules, List<ModuleSetSlug> executionOrder) {
    return new ModuleSetCatalog() {
      @Override
      public List<ModuleSetModule> modules() {
        return catalogModules;
      }

      @Override
      public List<ModuleSetSlug> sort(List<ModuleSetSlug> requestedModules) {
        return executionOrder.stream().filter(requestedModules::contains).toList();
      }
    };
  }
}
