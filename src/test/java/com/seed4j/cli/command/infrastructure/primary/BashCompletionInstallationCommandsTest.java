package com.seed4j.cli.command.infrastructure.primary;

import static com.seed4j.cli.command.infrastructure.primary.CliFixture.commandLine;
import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.IntegrationTest;
import com.seed4j.cli.command.application.BashCompletionInstallApplicationService;
import com.seed4j.cli.command.domain.BashCompletionInstallationPath;
import com.seed4j.cli.command.domain.BashCompletionInstallationResult;
import com.seed4j.cli.command.domain.BashCompletionScript;
import com.seed4j.module.application.Seed4JModulesApplicationService;
import com.seed4j.project.application.ProjectsApplicationService;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

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

  private static final class BashCompletionInstallerStub implements com.seed4j.cli.command.domain.BashCompletionInstaller {

    private BashCompletionScript installedScript;

    @Override
    public BashCompletionInstallationResult install(BashCompletionScript script) {
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
  }
}
