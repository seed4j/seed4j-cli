package com.seed4j.cli.command.infrastructure.primary;

import static com.seed4j.cli.command.infrastructure.primary.CliFixture.commandLine;
import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.IntegrationTest;
import com.seed4j.cli.command.application.RuntimeDisplayApplicationService;
import com.seed4j.cli.command.domain.RuntimeDisplay;
import com.seed4j.module.application.Seed4JModulesApplicationService;
import com.seed4j.project.application.ProjectsApplicationService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import picocli.CommandLine;

@ExtendWith(OutputCaptureExtension.class)
@IntegrationTest
class Seed4JCommandsFactoryTest {

  @Autowired
  private ProjectsApplicationService projects;

  @Autowired
  private Seed4JModulesApplicationService modules;

  @Autowired
  private Seed4JCommandsFactory commandsFactory;

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
  void shouldShowHelpWithoutReadingRuntimeDisplay(CapturedOutput output) {
    RuntimeDisplayApplicationService unavailableRuntime = new RuntimeDisplayApplicationService(() -> {
      throw new AssertionError("Runtime display must only be read for --version");
    });
    Seed4JCommandsFactory factory = new Seed4JCommandsFactory(List.of(), new Seed4JVersionProvider("1", "2", unavailableRuntime));
    String[] args = { "--help" };

    int exitCode = new CommandLine(factory.buildCommandSpec()).execute(args);

    assertThat(exitCode).isZero();
    assertThat(output).contains("Seed4J CLI").contains("--version");
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
