package com.seed4j.cli.command.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.command.application.AgentSkillInstallApplicationService;
import com.seed4j.cli.command.application.RuntimeDisplayApplicationService;
import com.seed4j.cli.command.domain.AgentSkillInstallationException;
import com.seed4j.cli.command.domain.AgentSkillInstallationPath;
import com.seed4j.cli.command.domain.AgentSkillInstallationResult;
import com.seed4j.cli.command.domain.AgentSkillInstallationScope;
import com.seed4j.cli.command.domain.AgentSkillInstallationStatus;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import picocli.CommandLine;

@ExtendWith(OutputCaptureExtension.class)
@UnitTest
class AgentSkillInstallationCommandsTest {

  @Test
  void shouldInstallSkillLocallyByDefault(@TempDir Path workingDirectory, CapturedOutput output) {
    Path installationPath = workingDirectory.resolve(".agents/skills/seed4j-cli").toAbsolutePath().normalize();
    AtomicReference<AgentSkillInstallationScope> requestedScope = new AtomicReference<>();
    AgentSkillInstallApplicationService applicationService = new AgentSkillInstallApplicationService(scope -> {
      requestedScope.set(scope);
      return new AgentSkillInstallationResult(AgentSkillInstallationStatus.INSTALLED, new AgentSkillInstallationPath(installationPath));
    });
    CommandLine commandLine = commandLine(applicationService);

    int exitCode = commandLine.execute("skill", "install");

    assertThat(exitCode).isZero();
    assertThat(requestedScope.get()).isEqualTo(AgentSkillInstallationScope.LOCAL);
    assertThat(output.getOut()).isEqualTo("Installed Seed4J CLI skill at %s.%n".formatted(installationPath));
    assertThat(output.getErr()).isEmpty();
  }

  @Test
  void shouldReportAnIdenticalReinstallationAsUpdated(@TempDir Path workingDirectory, CapturedOutput output) {
    Path installationPath = workingDirectory.resolve(".agents/skills/seed4j-cli").toAbsolutePath().normalize();
    AgentSkillInstallApplicationService applicationService = new AgentSkillInstallApplicationService(scope ->
      new AgentSkillInstallationResult(AgentSkillInstallationStatus.UPDATED, new AgentSkillInstallationPath(installationPath))
    );
    CommandLine commandLine = commandLine(applicationService);

    int exitCode = commandLine.execute("skill", "install");

    assertThat(exitCode).isZero();
    assertThat(output.getOut()).isEqualTo("Updated Seed4J CLI skill at %s.%n".formatted(installationPath));
    assertThat(output.getOut()).doesNotContain("unchanged");
    assertThat(output.getErr()).isEmpty();
  }

  @Test
  void shouldMapTheGlobalOptionToTheExplicitUserScope(@TempDir Path userHome, CapturedOutput output) {
    Path installationPath = userHome.resolve(".agents/skills/seed4j-cli").toAbsolutePath().normalize();
    AtomicReference<AgentSkillInstallationScope> requestedScope = new AtomicReference<>();
    AgentSkillInstallApplicationService applicationService = new AgentSkillInstallApplicationService(scope -> {
      requestedScope.set(scope);
      return new AgentSkillInstallationResult(AgentSkillInstallationStatus.INSTALLED, new AgentSkillInstallationPath(installationPath));
    });
    CommandLine commandLine = commandLine(applicationService);

    int exitCode = commandLine.execute("skill", "install", "--global");

    assertThat(exitCode).isZero();
    assertThat(requestedScope.get()).isEqualTo(AgentSkillInstallationScope.GLOBAL);
    assertThat(output.getOut()).isEqualTo("Installed Seed4J CLI skill at %s.%n".formatted(installationPath));
    assertThat(output.getErr()).isEmpty();
  }

  @Test
  void shouldReportInstallationFailureWithoutPrintingSuccess(CapturedOutput output) {
    AgentSkillInstallApplicationService applicationService = new AgentSkillInstallApplicationService(scope -> {
      throw new AgentSkillInstallationException("Could not stage the bundled Seed4J CLI skill.", new java.io.IOException());
    });
    CommandLine commandLine = commandLine(applicationService);

    int exitCode = commandLine.execute("skill", "install");

    assertThat(exitCode).isEqualTo(1);
    assertThat(output.getOut()).isEmpty();
    assertThat(output.getErr()).isEqualTo("Could not stage the bundled Seed4J CLI skill.%n".formatted());
  }

  @Test
  void shouldDescribeLocalDefaultAndExplicitGlobalAlternativeInHelp(CapturedOutput output) {
    AgentSkillInstallApplicationService applicationService = new AgentSkillInstallApplicationService(scope -> {
      throw new AssertionError("Help must not attempt installation");
    });
    CommandLine commandLine = commandLine(applicationService);

    int exitCode = commandLine.execute("skill", "install", "--help");

    assertThat(exitCode).isZero();
    assertThat(output.getOut())
      .contains("Install the bundled Seed4J CLI agent skill locally by default")
      .contains("--global")
      .contains("Install for the current user instead of the local working")
      .contains("directory");
    assertThat(output.getErr()).isEmpty();
  }

  private static CommandLine commandLine(AgentSkillInstallApplicationService applicationService) {
    SkillCommand skillCommand = new SkillCommand(new SkillInstallCommand(applicationService));
    Seed4JVersionProvider versionProvider = new Seed4JVersionProvider(
      "1",
      "2",
      new RuntimeDisplayApplicationService(com.seed4j.cli.command.domain.RuntimeDisplay::standard)
    );
    Seed4JCommandsFactory factory = new Seed4JCommandsFactory(List.of(skillCommand), versionProvider);
    return new CommandLine(factory.buildCommandSpec());
  }
}
