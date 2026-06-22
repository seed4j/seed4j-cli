package com.seed4j.cli.command.infrastructure.primary;

import static com.seed4j.cli.command.infrastructure.primary.CliFixture.commandLine;
import static com.seed4j.cli.command.infrastructure.primary.CliFixture.setupProjectTestFolder;
import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.IntegrationTest;
import com.seed4j.cli.command.domain.RuntimeDisplay;
import com.seed4j.module.application.Seed4JModulesApplicationService;
import com.seed4j.module.infrastructure.secondary.git.GitTestUtil;
import com.seed4j.project.application.ProjectsApplicationService;
import com.seed4j.project.domain.ProjectPath;
import com.seed4j.project.domain.history.ProjectHistory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
@IntegrationTest
class Seed4JCommandsFactoryTest {

  private static final String PROJECT_NAME = "projectName";
  private static final String BASE_NAME = "baseName";
  private static final String END_OF_LINE = "endOfLine";
  private static final String INDENT_SIZE = "indentSize";
  private static final String PACKAGE_NAME = "packageName";

  @Autowired
  private ProjectsApplicationService projects;

  @Autowired
  private Seed4JModulesApplicationService modules;

  @Test
  void shouldShowHelpMessageWhenNoCommand(CapturedOutput output) {
    String[] args = {};

    int exitCode = commandLine(modules, projects).execute(args);

    assertThat(exitCode).isEqualTo(2);
    assertThat(output)
      .contains("Seed4J CLI")
      .contains("-h, --help      Show this help message and exit.")
      .contains("-V, --version   Print version information and exit.")
      .contains("--debug")
      .contains("Enable runtime bootstrap diagnostics (extension mode only)")
      .contains("Commands:");
  }

  @Test
  void shouldAcceptDebugFlagInRootCommand(CapturedOutput output) {
    String[] args = { "--version", "--debug" };

    int exitCode = commandLine(modules, projects).execute(args);

    assertThat(exitCode).isZero();
    assertThat(output).contains("Seed4J CLI v1").contains("Seed4J version: 2");
  }

  @Test
  void shouldListInstallSubcommandWhenShowingExtensionHelp(CapturedOutput output) {
    String[] args = { "extension", "--help" };

    int exitCode = commandLine(modules, projects).execute(args);

    assertThat(exitCode).isZero();
    assertThat(output).contains("Manage runtime extensions").contains("install").contains("Install active runtime extension");
  }

  @Nested
  @DisplayName("completion")
  class Completion {

    @Test
    void shouldPrintBashCompletionScript(CapturedOutput output) {
      String[] args = { "completion", "bash" };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(output)
        .contains("_seed4j_completion()")
        .contains("complete -F _seed4j_completion seed4j")
        .contains("list")
        .contains("apply")
        .contains("extension")
        .contains("completion")
        .contains("bash")
        .contains("init")
        .contains("prettier")
        .contains("--project-path")
        .contains("--commit")
        .contains("--no-commit")
        .contains("--base-name")
        .contains("--project-name")
        .doesNotContain("--complete-values");
    }

    @Test
    void shouldPrintBashCompletionScriptWithModuleDefaultValueCandidates(CapturedOutput output) {
      String[] args = { "completion", "bash" };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(output)
        .contains("Seed4J Sample Application")
        .contains("seed4jSampleApplication")
        .contains("npm")
        .contains("'apply init\t--project-path') printf '%s\\n' '.'");
    }

    @Test
    void shouldPrintBashCompletionScriptWithKnownModuleValueCandidates(CapturedOutput output) {
      String[] args = { "completion", "bash" };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(output)
        .contains("'apply init\t--node-package-manager') printf '%s\\n' 'npm' 'pnpm'")
        .contains("'apply spring-boot\t--spring-configuration-format') printf '%s\\n' 'yaml' 'properties'")
        .contains("'apply init\t--end-of-line') printf '%s\\n' 'lf' 'crlf'");
    }

    @Test
    void shouldPrintBashCompletionScriptWithoutValueCandidatesWhenDisabled(CapturedOutput output) {
      String[] args = { "completion", "bash", "--no-complete-values" };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(output).contains("_seed4j_completion()").doesNotContain("Seed4J Sample Application");
    }

    @Test
    void shouldRejectRemovedCompleteValuesOption(CapturedOutput output) {
      String[] args = { "completion", "bash", "--complete-values=false" };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isEqualTo(2);
      assertThat(output).contains("Unknown option: '--complete-values=false'");
    }

    @Test
    void shouldShowBashCompletionOptionsInHelp(CapturedOutput output) {
      String[] args = { "completion", "bash", "--help" };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(output)
        .contains("--install")
        .contains("Install Bash completion script")
        .contains("--no-complete-values")
        .contains("Generate Bash completion without option value")
        .contains("candidates")
        .doesNotContain("--complete-values");
    }
  }

  @Nested
  @DisplayName("list")
  class ListModules {

    @Test
    void shouldNotLeakTheExtensionOnlySlugWhenListingModulesInStandardRuntimeMode(CapturedOutput output) {
      String[] args = { "list" };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(output).doesNotContain("runtime-extension-list-only");
    }

    @Test
    void shouldRenderTypedDependenciesWhenModuleHasDependencies(CapturedOutput output) {
      String[] args = { "list" };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(output).containsPattern("(?m)^\\s{2}\\S+\\s{2,}(?:module|feature):\\S+.*\\s{2,}.+$");
    }

    @Test
    void shouldShowDependenciesColumnWithFallbackForListOutput(CapturedOutput output) {
      String[] args = { "list" };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(output)
        .containsPattern("(?m)^\\s{2}Module\\s{2,}Dependencies\\s{2,}Description\\s*$")
        .containsPattern("(?m)^\\s{2}init\\s{2,}-\\s{2,}Init project\\s*$");
    }

    @Test
    void shouldListModules(CapturedOutput output) {
      String[] args = { "list" };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(output)
        .contains("Available seed4j modules")
        .contains("init")
        .contains("Init project")
        .contains("prettier")
        .contains("Format project with prettier");
    }
  }

  @Nested
  @DisplayName("apply")
  class ApplyModule {

    @Test
    void shouldNotApplyWithoutModuleSlugSubcommand(CapturedOutput output) {
      String[] args = { "apply" };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isEqualTo(2);
      assertThat(output).contains("Missing required subcommand").contains("init").contains("prettier");
    }

    @Test
    void shouldEscapeCommandDescriptionInHelpCommand(CapturedOutput output) {
      String[] args = { "apply", "--help" };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(output).doesNotContain(
        "[picocli WARN] Could not format 'Add JaCoCo for code coverage reporting and 100% coverage check' (Underlying error: Conversion = c, Flags =  ). Using raw String: '%n' format strings have not been replaced with newlines. Please ensure to escape '%' characters with another '%'."
      );
    }

    @Test
    void shouldDisplayModuleSlugsInHelpCommand(CapturedOutput output) {
      String[] args = { "apply", "--help" };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(output)
        .contains(
          """
          Apply seed4j specific module
            -h, --help      Show this help message and exit.
            -V, --version   Print version information and exit.
          Commands:
          """
        )
        .contains("init")
        .contains("Init project")
        .contains("prettier")
        .contains("Format project with prettier");
    }

    @Test
    void shouldDisplayModuleSlugsInAlphabeticalOrderInApplyHelpCommand(CapturedOutput output) {
      String[] args = { "apply", "--help" };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(output.toString().indexOf("angular-core"))
        .withFailMessage("Command 'angular-core' should appear before 'gradle-java' in alphabetical order")
        .isLessThan(output.toString().indexOf("gradle-java"));
    }

    @Test
    void shouldExplainCommitOptionInitializesGitAndNoCommitSkipsGit(CapturedOutput output) {
      String[] args = { "apply", "init", "--help" };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(output)
        .contains("Initialize Git if needed and commit generated changes")
        .contains("--no-commit skips Git init and commit");
    }

    @Test
    void shouldApplyInitModuleWithRequiredOptions() throws IOException {
      Path projectPath = setupProjectTestFolder();
      String[] args = {
        "apply",
        "init",
        "--project-path",
        projectPath.toString(),
        "--base-name",
        "seed4jSampleApplication",
        "--project-name",
        "Seed4J Sample Application",
        "--node-package-manager",
        "npm",
      };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(GitTestUtil.getCommits(projectPath)).contains("Apply module: init");
      assertThat(projectPropertyValue(projectPath, PROJECT_NAME)).isEqualTo("Seed4J Sample Application");
      assertThat(projectPropertyValue(projectPath, BASE_NAME)).isEqualTo("seed4jSampleApplication");
    }

    @Test
    void shouldPlanInitModuleWithExplicitAndDefaultParameterSources(CapturedOutput output) throws IOException {
      Path projectPath = setupProjectTestFolder();
      String[] args = {
        "apply",
        "init",
        "--project-path",
        projectPath.toString(),
        "--base-name",
        "seed4jSampleApplication",
        "--project-name",
        "Seed4J Sample Application",
        "--node-package-manager",
        "pnpm",
        "--plan",
      };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(GitTestUtil.getCommits(projectPath)).isEmpty();
      assertThat(projects.getHistory(new ProjectPath(projectPath.toString())).actions()).isEmpty();
      assertThat(output)
        .contains(
          """
          Plan for module: init
          Project path: %s

          Resolved parameters:

          projectName: Seed4J Sample Application
            Source: explicit CLI input
            CLI option: --project-name

          baseName: seed4jSampleApplication
            Source: explicit CLI input
            CLI option: --base-name

          nodePackageManager: pnpm
            Source: explicit CLI input
            CLI option: --node-package-manager
          """.formatted(projectPath)
        )
        .contains(
          """
          endOfLine: lf
            Source: default
            CLI option: --end-of-line
          """
        )
        .contains("No changes were applied.")
        .doesNotContain("Missing required parameters:");
    }

    @Test
    void shouldPlanInitModuleWithHistorySourcesAndExplicitOverrides(CapturedOutput output) throws IOException {
      Path projectPath = setupProjectTestFolder();
      String[] applyArgs = {
        "apply",
        "init",
        "--project-path",
        projectPath.toString(),
        "--base-name",
        "seed4jSampleApplication",
        "--project-name",
        "Seed4J Sample Application",
        "--node-package-manager",
        "npm",
      };
      int applyExitCode = commandLine(modules, projects).execute(applyArgs);
      assertThat(applyExitCode).isZero();
      int historyActionsBeforePlan = projects.getHistory(new ProjectPath(projectPath.toString())).actions().size();
      String commitsBeforePlan = GitTestUtil.getCommits(projectPath);
      String[] planArgs = { "apply", "init", "--project-path", projectPath.toString(), "--base-name", "explicitOverride", "--plan" };

      int exitCode = commandLine(modules, projects).execute(planArgs);

      assertThat(exitCode).isZero();
      assertThat(projects.getHistory(new ProjectPath(projectPath.toString())).actions()).hasSize(historyActionsBeforePlan);
      assertThat(GitTestUtil.getCommits(projectPath)).isEqualTo(commitsBeforePlan);
      assertThat(output)
        .contains(
          """
          projectName: Seed4J Sample Application
            Source: project history
            CLI option: --project-name
            Note: already selected by project history; omit this option to keep it.

          baseName: explicitOverride
            Source: explicit CLI input
            CLI option: --base-name
          """
        )
        .contains(
          """
          nodePackageManager: npm
            Source: project history
            CLI option: --node-package-manager
            Note: already selected by project history; omit this option to keep it.
          """
        )
        .contains("No changes were applied.")
        .doesNotContain("Missing required parameters:");
    }

    @Test
    void shouldNotApplyInitModuleMissingRequiredOptions(CapturedOutput output) throws IOException {
      Path projectPath = setupProjectTestFolder();
      String[] args = { "apply", "init", "--project-path", projectPath.toString() };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isEqualTo(2);
      assertThat(GitTestUtil.getCommits(projectPath)).isEmpty();
      assertThat(output)
        .contains("Missing required")
        .contains("'--base-name=<basename*>'")
        .contains("'--project-name=<projectname*>'")
        .contains("Project short name (only letters and numbers) (required)")
        .contains("Project full name (required)");
    }

    @Test
    void shouldPlanInitModuleMissingRequiredOptions(CapturedOutput output) throws IOException {
      Path projectPath = setupProjectTestFolder();
      String[] args = { "apply", "init", "--project-path", projectPath.toString(), "--plan" };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(GitTestUtil.getCommits(projectPath)).isEmpty();
      assertThat(projects.getHistory(new ProjectPath(projectPath.toString())).actions()).isEmpty();
      assertThat(output)
        .contains(
          """
          Plan for module: init
          Project path: %s

          Resolved parameters:
          """.formatted(projectPath)
        )
        .contains(
          """
          Missing required parameters:

          projectName:
            CLI option: --project-name
            Note: pass this option or apply a module that records it in project history.

          baseName:
            CLI option: --base-name
            Note: pass this option or apply a module that records it in project history.
          """
        )
        .contains("No changes were applied.");
    }

    @Test
    void shouldPlanModuleWithoutResolvedParameters(CapturedOutput output) throws IOException {
      Path projectPath = setupProjectTestFolder();
      String[] args = { "apply", "checkstyle", "--project-path", projectPath.toString(), "--plan" };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(GitTestUtil.getCommits(projectPath)).isEmpty();
      assertThat(projects.getHistory(new ProjectPath(projectPath.toString())).actions()).isEmpty();
      assertThat(output)
        .contains(
          """
          Plan for module: checkstyle
          Project path: %s

          Resolved parameters:

          No changes were applied.
          """.formatted(projectPath)
        )
        .doesNotContain("Missing required parameters:");
    }

    @Test
    void shouldApplyInitModuleWithCommitDefaultValue() throws IOException {
      Path projectPath = setupProjectTestFolder();
      String[] args = {
        "apply",
        "init",
        "--project-path",
        projectPath.toString(),
        "--base-name",
        "seed4jSampleApplication",
        "--project-name",
        "Seed4J Sample Application",
        "--node-package-manager",
        "npm",
      };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(GitTestUtil.getCommits(projectPath)).contains("Apply module: init");
    }

    private Object projectPropertyValue(Path projectPath, String propertyKey) {
      ProjectHistory history = projects.getHistory(new ProjectPath(projectPath.toString()));
      return history.latestProperties().parameters().getOrDefault(propertyKey, null);
    }

    @Test
    void shouldApplyInitModuleWithCommit() throws IOException {
      Path projectPath = setupProjectTestFolder();
      String[] args = {
        "apply",
        "init",
        "--project-path",
        projectPath.toString(),
        "--base-name",
        "seed4jSampleApplication",
        "--project-name",
        "Seed4J Sample Application",
        "--node-package-manager",
        "npm",
        "--commit",
      };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(GitTestUtil.getCommits(projectPath)).contains("Apply module: init");
    }

    @Test
    void shouldApplyInitModuleWithoutCommit() throws IOException {
      Path projectPath = setupProjectTestFolder();
      String[] args = {
        "apply",
        "init",
        "--project-path",
        projectPath.toString(),
        "--base-name",
        "seed4jSampleApplication",
        "--project-name",
        "Seed4J Sample Application",
        "--node-package-manager",
        "npm",
        "--no-commit",
      };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(GitTestUtil.getCommits(projectPath)).isEmpty();
    }

    @Test
    void shouldNotApplyModuleWithInvalidBaseName() throws IOException {
      Path projectPath = setupProjectTestFolder();
      String[] args = {
        "apply",
        "init",
        "--project-path",
        projectPath.toString(),
        "--base-name",
        "my.New@pp",
        "--project-name",
        "Seed4J Sample Application",
        "--node-package-manager",
        "npm",
      };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isEqualTo(1);
    }

    @Test
    void shouldApplyInitModuleWithIndentation() throws IOException {
      Path projectPath = setupProjectTestFolder();
      String[] args = {
        "apply",
        "init",
        "--project-path",
        projectPath.toString(),
        "--base-name",
        "seed4JSampleApplication",
        "--project-name",
        "Seed4J Sample Application",
        "--node-package-manager",
        "npm",
        "--indent-size",
        "4",
      };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(projectPropertyValue(projectPath, INDENT_SIZE)).isEqualTo(4);
    }

    @Test
    void shouldApplyInitModuleWithEndOfLine() throws IOException {
      Path projectPath = setupProjectTestFolder();
      String[] args = {
        "apply",
        "init",
        "--project-path",
        projectPath.toString(),
        "--base-name",
        "seed4jSampleApplication",
        "--project-name",
        "Seed4J Sample Application",
        "--node-package-manager",
        "npm",
        "--end-of-line",
        "lf",
      };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(projectPropertyValue(projectPath, END_OF_LINE)).isEqualTo("lf");
    }

    @Test
    void shouldReuseParametersFromPreviousModuleApplications() throws IOException {
      Path projectPath = setupProjectTestFolder();
      String[] initModuleArgs = {
        "apply",
        "init",
        "--project-path",
        projectPath.toString(),
        "--base-name",
        "seed4jSampleApplication",
        "--project-name",
        "Seed4J Sample Application",
        "--node-package-manager",
        "npm",
      };
      int initModuleExitCode = commandLine(modules, projects).execute(initModuleArgs);
      assertThat(initModuleExitCode).isZero();
      String[] mavenJavaModuleArgs = {
        "apply",
        "maven-java",
        "--project-path",
        projectPath.toString(),
        "--package-name",
        "com.my.company",
      };

      int mavenJavaModuleExitCode = commandLine(modules, projects).execute(mavenJavaModuleArgs);

      assertThat(mavenJavaModuleExitCode).isZero();
      assertThat(projectPropertyValue(projectPath, PROJECT_NAME)).isEqualTo("Seed4J Sample Application");
      assertThat(projectPropertyValue(projectPath, BASE_NAME)).isEqualTo("seed4jSampleApplication");
      assertThat(projectPropertyValue(projectPath, PACKAGE_NAME)).isEqualTo("com.my.company");
    }

    @Test
    void shouldRenderVersionOutputUsingProjectBuildMetadata(CapturedOutput output) {
      String[] args = { "--version" };
      RuntimeDisplay runtimeDisplay = RuntimeDisplay.extension(
        Optional.of(new com.seed4j.cli.command.domain.RuntimeDistributionId("company-extension")),
        Optional.of(new com.seed4j.cli.command.domain.RuntimeDistributionVersion("1.0.0"))
      );

      int exitCode = commandLine(modules, projects, runtimeDisplay, "9.9.9", "8.8.8").execute(args);

      assertThat(exitCode).isZero();
      assertThat(output).contains("Seed4J CLI v9.9.9").contains("Seed4J version: 8.8.8");
    }

    @Test
    void shouldUseSafeFallbackWhenNoVersionMetadataIsAvailable(CapturedOutput output) {
      String[] args = { "--version" };
      RuntimeDisplay runtimeDisplay = RuntimeDisplay.standard();

      int exitCode = commandLine(modules, projects, runtimeDisplay, "", "").execute(args);

      assertThat(exitCode).isZero();
      assertThat(output)
        .contains("Seed4J CLI vunknown")
        .contains("Seed4J version: unknown")
        .contains("Runtime mode: standard")
        .doesNotContain("vnull")
        .doesNotContain("version: null")
        .doesNotContain("Distribution ID")
        .doesNotContain("Distribution version");
    }

    @Test
    void shouldShowVersion(CapturedOutput output) {
      String[] args = { "--version" };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(output)
        .contains("Seed4J CLI v1")
        .contains("Seed4J version: 2")
        .contains("Runtime mode: standard")
        .doesNotContain("Distribution ID")
        .doesNotContain("Distribution version");
    }

    @Test
    void shouldShowRuntimeModeAndDistributionInVersionOutput(CapturedOutput output) {
      String[] args = { "--version" };
      RuntimeDisplay runtimeDisplay = RuntimeDisplay.extension(
        Optional.of(new com.seed4j.cli.command.domain.RuntimeDistributionId("company-extension")),
        Optional.of(new com.seed4j.cli.command.domain.RuntimeDistributionVersion("1.0.0"))
      );

      int exitCode = commandLine(modules, projects, runtimeDisplay).execute(args);

      assertThat(exitCode).isZero();
      assertThat(output)
        .contains("Seed4J CLI v1")
        .contains("Seed4J version: 2")
        .contains("Runtime mode: extension")
        .contains("Distribution ID: company-extension")
        .contains("Distribution version: 1.0.0");
    }
  }
}
