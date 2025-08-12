package com.seed4j.cli.command.infrastructure.primary;

import static com.seed4j.cli.command.infrastructure.primary.CliFixture.commandLine;
import static com.seed4j.cli.command.infrastructure.primary.CliFixture.setupProjectTestFolder;
import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.IntegrationTest;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tech.jhipster.lite.module.application.JHipsterModulesApplicationService;
import tech.jhipster.lite.module.infrastructure.secondary.git.GitTestUtil;
import tech.jhipster.lite.project.application.ProjectsApplicationService;
import tech.jhipster.lite.project.domain.ProjectPath;
import tech.jhipster.lite.project.domain.history.ProjectHistory;

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
  private JHipsterModulesApplicationService modules;

  @Test
  void shouldShowHelpMessageWhenNoCommand(CapturedOutput output) {
    String[] args = {};

    int exitCode = commandLine(modules, projects).execute(args);

    assertThat(exitCode).isEqualTo(2);
    assertThat(output).contains(
        """
        JHipster Lite CLI
          -h, --help      Show this help message and exit.
          -V, --version   Print version information and exit.

        Commands:
        """
      );
  }

  @Nested
  @DisplayName("list")
  class ListModules {

    @Test
    void shouldListModules(CapturedOutput output) {
      String[] args = { "list" };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(output)
        .contains("Available jhipster-lite modules")
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
          Apply jhipster-lite specific module
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
    void shouldApplyInitModuleWithRequiredOptions() throws IOException {
      Path projectPath = setupProjectTestFolder();
      String[] args = {
        "apply",
        "init",
        "--project-path",
        projectPath.toString(),
        "--base-name",
        "jhipsterSampleApplication",
        "--project-name",
        "JHipster Sample Application",
        "--node-package-manager",
        "npm",
      };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(GitTestUtil.getCommits(projectPath)).contains("Apply module: init");
      assertThat(projectPropertyValue(projectPath, PROJECT_NAME)).isEqualTo("JHipster Sample Application");
      assertThat(projectPropertyValue(projectPath, BASE_NAME)).isEqualTo("jhipsterSampleApplication");
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
    void shouldApplyInitModuleWithCommitDefaultValue() throws IOException {
      Path projectPath = setupProjectTestFolder();
      String[] args = {
        "apply",
        "init",
        "--project-path",
        projectPath.toString(),
        "--base-name",
        "jhipsterSampleApplication",
        "--project-name",
        "JHipster Sample Application",
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
        "jhipsterSampleApplication",
        "--project-name",
        "JHipster Sample Application",
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
        "jhipsterSampleApplication",
        "--project-name",
        "JHipster Sample Application",
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
        "JHipster Sample Application",
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
        "jhipsterSampleApplication",
        "--project-name",
        "JHipster Sample Application",
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
        "jhipsterSampleApplication",
        "--project-name",
        "JHipster Sample Application",
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
        "jhipsterSampleApplication",
        "--project-name",
        "JHipster Sample Application",
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
      assertThat(projectPropertyValue(projectPath, PROJECT_NAME)).isEqualTo("JHipster Sample Application");
      assertThat(projectPropertyValue(projectPath, BASE_NAME)).isEqualTo("jhipsterSampleApplication");
      assertThat(projectPropertyValue(projectPath, PACKAGE_NAME)).isEqualTo("com.my.company");
    }

    @Test
    void shouldShowVersion(CapturedOutput output) {
      String[] args = { "--version" };

      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(output).contains("JHipster Lite CLI v1").contains("JHipster Lite version: 2");
    }
  }
}
