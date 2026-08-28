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
