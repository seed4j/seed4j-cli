package com.seed4j.cli.command.infrastructure.primary;

import static com.seed4j.cli.command.infrastructure.primary.CliFixture.commandLine;
import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.IntegrationTest;
import com.seed4j.module.application.Seed4JModulesApplicationService;
import com.seed4j.project.application.ProjectsApplicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import picocli.CommandLine;

@ExtendWith(OutputCaptureExtension.class)
@IntegrationTest
class CompletionCommandTest {

  @Autowired
  private ProjectsApplicationService projects;

  @Autowired
  private Seed4JModulesApplicationService modules;

  @Autowired
  private Seed4JCommandsFactory commandsFactory;

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
      .containsPattern("(?m)^    'apply-set'\\) printf '%s' '[^']*--commit[^']*--no-commit[^']*--plan[^']*' ;;$")
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
      .contains("'apply init\t--project-path') printf '%s\\n' '.'")
      .contains("'apply-set\t--node-package-manager') printf '%s\\n' 'npm' 'pnpm'");
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
  void shouldKeepEarlierBashCompletionOptionsAfterBuildingAnotherCommandTree(CapturedOutput output) {
    CommandLine earlierCommandLine = new CommandLine(commandsFactory.buildCommandSpec());
    commandsFactory.buildCommandSpec();
    String[] args = { "completion", "bash", "--no-complete-values" };

    int exitCode = earlierCommandLine.execute(args);

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
