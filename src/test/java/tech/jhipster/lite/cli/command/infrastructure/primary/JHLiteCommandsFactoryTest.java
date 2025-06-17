package tech.jhipster.lite.cli.command.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.jhipster.lite.TestProjects.newTestFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import picocli.CommandLine;
import tech.jhipster.lite.cli.IntegrationTest;
import tech.jhipster.lite.module.application.JHipsterModulesApplicationService;
import tech.jhipster.lite.module.infrastructure.secondary.git.GitTestUtil;
import tech.jhipster.lite.project.application.ProjectsApplicationService;
import tech.jhipster.lite.project.domain.ProjectPath;
import tech.jhipster.lite.project.domain.history.ProjectHistory;

@ExtendWith(OutputCaptureExtension.class)
@IntegrationTest
class JHLiteCommandsFactoryTest {

  private static final String PROJECT_NAME = "projectName";
  private static final String BASE_NAME = "baseName";
  private static final String END_OF_LINE = "endOfLine";
  private static final String INDENT_SIZE = "indentSize";

  @Autowired
  private ProjectsApplicationService projects;

  @Autowired
  private JHipsterModulesApplicationService modules;

  @Test
  void shouldShowHelpMessageWhenNoCommand(CapturedOutput output) {
    String[] args = {};

    int exitCode = commandLine().execute(args);

    assertThat(exitCode).isEqualTo(2);
    assertThat(output.toString()).contains(
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

      int exitCode = commandLine().execute(args);

      assertThat(exitCode).isZero();
      assertThat(output.toString()).contains("Available jhipster-lite modules");
      assertThat(output.toString()).contains("init").contains("Init project");
      assertThat(output.toString()).contains("prettier").contains("Format project with prettier");
    }
  }

  @Nested
  @DisplayName("apply")
  class ApplyModule {

    @Test
    void shouldNotApplyWithoutModuleSlugSubcommand(CapturedOutput output) {
      String[] args = { "apply" };

      int exitCode = commandLine().execute(args);

      assertThat(exitCode).isEqualTo(2);
      assertThat(output.toString()).contains("Missing required subcommand").contains("init").contains("prettier");
    }

    @Test
    void shouldEscapeCommandDescriptionInHelpCommand(CapturedOutput output) {
      String[] args = { "apply", "--help" };

      int exitCode = commandLine().execute(args);

      assertThat(exitCode).isZero();
      assertThat(output.toString()).doesNotContain(
        "[picocli WARN] Could not format 'Add JaCoCo for code coverage reporting and 100% coverage check' (Underlying error: Conversion = c, Flags =  ). Using raw String: '%n' format strings have not been replaced with newlines. Please ensure to escape '%' characters with another '%'."
      );
    }

    @Test
    void shouldDisplayModuleSlugsInHelpCommand(CapturedOutput output) {
      String[] args = { "apply", "--help" };

      int exitCode = commandLine().execute(args);

      assertThat(exitCode).isZero();
      assertThat(output.toString()).contains(
        """
        Apply jhipster-lite specific module
          -h, --help      Show this help message and exit.
          -V, --version   Print version information and exit.
        Commands:
        """
      );
      assertThat(output.toString()).contains("init").contains("Init project").contains("prettier").contains("Format project with prettier");
    }

    @Test
    void shouldDisplayModuleSlugsInAlphabeticalOrderInApplyHelpCommand(CapturedOutput output) {
      String[] args = { "apply", "--help" };

      int exitCode = commandLine().execute(args);

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
      };

      int exitCode = commandLine().execute(args);

      assertThat(exitCode).isZero();
      assertThat(GitTestUtil.getCommits(projectPath)).contains("Apply module: init");
      assertThat(projectPropertyValue(projectPath, PROJECT_NAME)).isEqualTo("JHipster Sample Application");
      assertThat(projectPropertyValue(projectPath, BASE_NAME)).isEqualTo("jhipsterSampleApplication");
    }

    @Test
    void shouldNotApplyInitModuleMissingRequiredOptions(CapturedOutput output) throws IOException {
      Path projectPath = setupProjectTestFolder();
      String[] args = { "apply", "init", "--project-path", projectPath.toString() };

      int exitCode = commandLine().execute(args);

      assertThat(exitCode).isEqualTo(2);
      assertThat(GitTestUtil.getCommits(projectPath)).isEmpty();
      assertThat(output.toString())
        .contains("Missing required")
        .contains("'--base-name=<basename>'")
        .contains("'--project-name=<projectname>'");
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
      };

      int exitCode = commandLine().execute(args);

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
        "--commit",
      };

      int exitCode = commandLine().execute(args);

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
        "--no-commit",
      };

      int exitCode = commandLine().execute(args);

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
      };

      int exitCode = commandLine().execute(args);

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
        "--indent-size",
        "4",
      };

      int exitCode = commandLine().execute(args);

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
        "--end-of-line",
        "lf",
      };

      int exitCode = commandLine().execute(args);

      assertThat(exitCode).isZero();
      assertThat(projectPropertyValue(projectPath, END_OF_LINE)).isEqualTo("lf");
    }

    private static Path setupProjectTestFolder() throws IOException {
      String projectFolder = newTestFolder();
      Path projectPath = Path.of(projectFolder);
      Files.createDirectories(projectPath);
      loadGitConfig(projectPath);

      return projectPath;
    }

    private static void loadGitConfig(Path project) {
      GitTestUtil.execute(project, "init");
      GitTestUtil.execute(project, "config", "init.defaultBranch", "main");
      GitTestUtil.execute(project, "config", "user.email", "\"test@jhipster.com\"");
      GitTestUtil.execute(project, "config", "user.name", "\"Test\"");
    }
  }

  private CommandLine commandLine() {
    ListModulesCommand listModulesCommand = new ListModulesCommand(modules);
    ApplyModuleCommand applyModuleCommand = new ApplyModuleCommand(modules);

    JHLiteCommandsFactory jhliteCommandsFactory = new JHLiteCommandsFactory(listModulesCommand, applyModuleCommand);

    return new CommandLine(jhliteCommandsFactory.buildCommandSpec());
  }
}
