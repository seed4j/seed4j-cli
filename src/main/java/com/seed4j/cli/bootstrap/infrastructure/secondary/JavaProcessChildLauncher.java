package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.ChildRuntimeLaunchRequest;
import com.seed4j.cli.bootstrap.domain.ChildRuntimeLauncher;
import com.seed4j.cli.bootstrap.domain.JavaExecutablePath;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import java.util.ArrayList;
import java.util.List;

public class JavaProcessChildLauncher implements ChildRuntimeLauncher {

  private final JavaExecutablePath javaExecutable;
  private final Seed4JCliHome cliHome;
  private final ChildProcessCommandExecutor processExecutor;

  public JavaProcessChildLauncher(JavaExecutablePath javaExecutable, Seed4JCliHome cliHome, ChildProcessCommandExecutor processExecutor) {
    this.javaExecutable = javaExecutable;
    this.cliHome = cliHome;
    this.processExecutor = processExecutor;
  }

  @Override
  public int launch(ChildRuntimeLaunchRequest request) {
    List<String> command = new ArrayList<>(
      List.of(
        javaExecutable.path().toString(),
        "-D" + CurrentProcessPreSpringRuntimeEnvironmentReader.CHILD_MODE_PROPERTY + "=true",
        "-Duser.home=" + cliHome.path(),
        "-jar",
        request.executableJar().path().toString()
      )
    );
    command.addAll(request.arguments().asList());
    return processExecutor.execute(command);
  }
}
