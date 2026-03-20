package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

@UnitTest
class JavaProcessChildLauncherTest {

  @Test
  void shouldBuildTheJavaCommandForStandardMode() {
    RecordingProcessExecutor processExecutor = new RecordingProcessExecutor();
    JavaProcessChildLauncher launcher = new JavaProcessChildLauncher(Path.of("/opt/jdk/bin/java"), processExecutor);
    RuntimeSelection runtimeSelection = new RuntimeSelection(RuntimeMode.STANDARD, Optional.empty(), Optional.empty(), Optional.empty());
    Map<String, String> systemProperties = new LinkedHashMap<>();
    systemProperties.put("seed4j.cli.runtime.child", "true");
    systemProperties.put("seed4j.cli.runtime.mode", "standard");
    JavaChildProcessRequest request = new JavaChildProcessRequest(
      Path.of("/opt/seed4j/seed4j-cli.jar"),
      "org.springframework.boot.loader.launch.PropertiesLauncher",
      Map.copyOf(systemProperties),
      List.of("--version"),
      runtimeSelection
    );

    int exitCode = launcher.launch(request);

    assertThat(exitCode).isEqualTo(19);
    assertThat(processExecutor.command()).containsExactly(
      "/opt/jdk/bin/java",
      "-Dseed4j.cli.runtime.child=true",
      "-Dseed4j.cli.runtime.mode=standard",
      "-cp",
      "/opt/seed4j/seed4j-cli.jar",
      "org.springframework.boot.loader.launch.PropertiesLauncher",
      "--version"
    );
  }

  private static final class RecordingProcessExecutor implements JavaProcessChildLauncher.ProcessExecutor {

    private List<String> command;

    @Override
    public int execute(List<String> command) {
      this.command = command;
      return 19;
    }

    List<String> command() {
      return command;
    }
  }
}
