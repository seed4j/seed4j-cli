package com.seed4j.cli.bootstrap.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.ChildRuntimeLaunchRequest;
import com.seed4j.cli.bootstrap.domain.JavaExecutablePath;
import com.seed4j.cli.bootstrap.domain.Seed4JCliArguments;
import com.seed4j.cli.bootstrap.domain.Seed4JCliExecutablePath;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class JavaProcessChildLauncherTest {

  @Test
  void shouldPropagateTheExitCodeOfTheJavaProcess() {
    JavaProcessChildLauncher launcher = new JavaProcessChildLauncher(
      new JavaExecutablePath(Path.of(System.getProperty("java.home"), "bin", "java")),
      Seed4JCliHome.from(System.getProperty("user.home")),
      new JavaChildProcessCommandExecutor()
    );
    ChildRuntimeLaunchRequest request = new ChildRuntimeLaunchRequest(
      new Seed4JCliExecutablePath(Path.of("missing-cli.jar")),
      new Seed4JCliArguments(new String[] { "--version" })
    );

    int exitCode = launcher.launch(request);

    assertThat(exitCode).isEqualTo(1);
  }
}
