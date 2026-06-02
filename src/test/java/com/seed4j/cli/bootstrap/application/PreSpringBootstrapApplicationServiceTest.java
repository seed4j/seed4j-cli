package com.seed4j.cli.bootstrap.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.PreSpringRuntimeEnvironment;
import com.seed4j.cli.bootstrap.domain.PreSpringRuntimeEnvironmentReader;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class PreSpringBootstrapApplicationServiceTest {

  @Test
  void shouldReadRuntimeEnvironmentFromReaderAndDelegateArgsChildModeAndFactoryInputs() {
    RecordingPreSpringLauncher recordingPreSpringLauncher = new RecordingPreSpringLauncher(37);
    RecordingPreSpringLauncherFactory recordingPreSpringLauncherFactory = new RecordingPreSpringLauncherFactory(recordingPreSpringLauncher);
    PreSpringRuntimeEnvironment runtimeEnvironment = new PreSpringRuntimeEnvironment(
      Path.of("/home/user"),
      Path.of("/tmp/seed4j-cli.jar"),
      true,
      Path.of("/tmp/jdk/bin/java")
    );
    RecordingPreSpringRuntimeEnvironmentReader recordingPreSpringRuntimeEnvironmentReader = new RecordingPreSpringRuntimeEnvironmentReader(
      runtimeEnvironment
    );
    PreSpringBootstrapApplicationService service = new PreSpringBootstrapApplicationService(
      recordingPreSpringLauncherFactory,
      recordingPreSpringRuntimeEnvironmentReader
    );
    PreSpringBootstrapCommand command = new PreSpringBootstrapCommand(new String[] { "--version" });

    int exitCode = service.exitCodeFor(command);

    assertThat(exitCode).isEqualTo(37);
    assertThat(recordingPreSpringRuntimeEnvironmentReader.currentCalls()).isEqualTo(1);
    assertThat(recordingPreSpringLauncherFactory.runtimeEnvironment()).isEqualTo(runtimeEnvironment);
    assertThat(recordingPreSpringLauncher.arguments()).containsExactly("--version");
    assertThat(recordingPreSpringLauncher.childMode()).isTrue();
  }

  private static final class RecordingPreSpringLauncher implements PreSpringLauncher {

    private final int exitCode;
    private String[] arguments;
    private Boolean childMode;

    private RecordingPreSpringLauncher(int exitCode) {
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

  private static final class RecordingPreSpringLauncherFactory implements PreSpringLauncherFactory {

    private final RecordingPreSpringLauncher preSpringLauncher;
    private PreSpringRuntimeEnvironment runtimeEnvironment;

    private RecordingPreSpringLauncherFactory(RecordingPreSpringLauncher preSpringLauncher) {
      this.preSpringLauncher = preSpringLauncher;
    }

    @Override
    public PreSpringLauncher create(PreSpringRuntimeEnvironment runtimeEnvironment) {
      this.runtimeEnvironment = runtimeEnvironment;
      return preSpringLauncher;
    }

    PreSpringRuntimeEnvironment runtimeEnvironment() {
      return runtimeEnvironment;
    }
  }

  private static final class RecordingPreSpringRuntimeEnvironmentReader implements PreSpringRuntimeEnvironmentReader {

    private final PreSpringRuntimeEnvironment runtimeEnvironment;
    private int currentCalls;

    private RecordingPreSpringRuntimeEnvironmentReader(PreSpringRuntimeEnvironment runtimeEnvironment) {
      this.runtimeEnvironment = runtimeEnvironment;
    }

    @Override
    public PreSpringRuntimeEnvironment current() {
      currentCalls++;
      return runtimeEnvironment;
    }

    int currentCalls() {
      return currentCalls;
    }
  }
}
