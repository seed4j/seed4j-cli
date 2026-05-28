package com.seed4j.cli.bootstrap.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapEntryPoint;
import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringLauncherAssembler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class PreSpringBootstrapApplicationServiceTest {

  @Test
  void shouldDelegateLaunchArgumentsAndChildModeToLauncherAndReturnTheLauncherExitCode() {
    RecordingLauncher launcher = new RecordingLauncher(37);
    PreSpringBootstrapApplicationService service = new PreSpringBootstrapApplicationService(launcher, true);

    int exitCode = service.launch(new String[] { "--version" });

    assertThat(exitCode).isEqualTo(37);
    assertThat(launcher.arguments()).containsExactly("--version");
    assertThat(launcher.childMode()).isTrue();
  }

  @Test
  void shouldBeBuildableByThePreSpringAssemblerWithoutEmbeddingBootstrapFlowDecisionsInTheAssembler() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(
      configPath,
      """
      seed4j:
        runtime:
          mode: standard
      """
    );
    PreSpringLauncherAssembler assembler = new PreSpringLauncherAssembler();
    PreSpringBootstrapEntryPoint entryPoint = assembler.assemble(userHome, Path.of("seed4j-cli.jar"), "2.2.0", true);

    int exitCode = entryPoint.launch(new String[] { "--version" });

    assertThat(exitCode).isZero();
  }

  private static final class RecordingLauncher implements PreSpringBootstrapApplicationService.Launcher {

    private final int exitCode;
    private String[] arguments;
    private Boolean childMode;

    private RecordingLauncher(int exitCode) {
      this.exitCode = exitCode;
    }

    @Override
    public int launch(String[] args, boolean childMode) {
      this.arguments = args;
      this.childMode = childMode;
      return exitCode;
    }

    String[] arguments() {
      return arguments;
    }

    Boolean childMode() {
      return childMode;
    }
  }
}
