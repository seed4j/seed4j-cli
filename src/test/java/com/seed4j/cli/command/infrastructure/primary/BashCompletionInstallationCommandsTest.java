package com.seed4j.cli.command.infrastructure.primary;

import static com.seed4j.cli.command.infrastructure.primary.CliFixture.commandLine;
import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.IntegrationTest;
import com.seed4j.cli.command.application.BashCompletionInstallApplicationService;
import com.seed4j.cli.command.domain.BashCompletionInstallationException;
import com.seed4j.cli.command.domain.BashCompletionInstallationPath;
import com.seed4j.cli.command.domain.BashCompletionInstallationResult;
import com.seed4j.cli.command.domain.BashCompletionScript;
import com.seed4j.module.application.Seed4JModulesApplicationService;
import com.seed4j.project.application.ProjectsApplicationService;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import picocli.CommandLine.ExitCode;

@ExtendWith(OutputCaptureExtension.class)
@IntegrationTest
class BashCompletionInstallationCommandsTest {

  @Autowired
  private ProjectsApplicationService projects;

  @Autowired
  private Seed4JModulesApplicationService modules;

  @Test
  void shouldInstallBashCompletionScriptAndPrintSourceInstruction(CapturedOutput output) {
    BashCompletionInstallerStub installer = new BashCompletionInstallerStub();
    BashCompletionInstallApplicationService installApplicationService = new BashCompletionInstallApplicationService(installer);

    int exitCode = commandLine(modules, projects, installApplicationService).execute("completion", "bash", "--install");

    assertThat(exitCode).isZero();
    assertThat(installer.installedScript().content()).contains("_seed4j_completion()").contains("complete -F _seed4j_completion seed4j");
    assertThat(output)
      .contains("Installed Bash completion script to ~/.local/share/bash-completion/completions/seed4j")
      .contains("source ~/.local/share/bash-completion/completions/seed4j");
  }

  @Test
  void shouldReportBashCompletionInstallationFailureWithoutSuccessInstruction(CapturedOutput output) {
    BashCompletionInstallerStub installer = new BashCompletionInstallerStub();
    installer.failInstallation();
    BashCompletionInstallApplicationService installApplicationService = new BashCompletionInstallApplicationService(installer);

    int exitCode = commandLine(modules, projects, installApplicationService).execute("completion", "bash", "--install");

    assertThat(exitCode).isEqualTo(ExitCode.SOFTWARE);
    assertThat(output.getErr()).contains("Could not install Bash completion script.");
    assertThat(output.getOut())
      .doesNotContain("Installed Bash completion script to ~/.local/share/bash-completion/completions/seed4j")
      .doesNotContain("source ~/.local/share/bash-completion/completions/seed4j");
  }

  private static final class BashCompletionInstallerStub implements com.seed4j.cli.command.domain.BashCompletionInstaller {

    private BashCompletionScript installedScript;
    private boolean failInstallation;

    @Override
    public BashCompletionInstallationResult install(BashCompletionScript script) {
      if (failInstallation) {
        throw new BashCompletionInstallationException("Could not install Bash completion script.", new IOException("disk denied"));
      }
      installedScript = script;

      return new BashCompletionInstallationResult(
        new BashCompletionInstallationPath(
          Path.of(System.getProperty("user.home")).resolve(".local/share/bash-completion/completions/seed4j")
        )
      );
    }

    private BashCompletionScript installedScript() {
      return installedScript;
    }

    private void failInstallation() {
      failInstallation = true;
    }
  }
}
