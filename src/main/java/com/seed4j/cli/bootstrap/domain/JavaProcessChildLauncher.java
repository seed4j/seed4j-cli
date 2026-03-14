package com.seed4j.cli.bootstrap.domain;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JavaProcessChildLauncher implements ChildProcessLauncher {

  @FunctionalInterface
  public interface ProcessExecutor {
    int execute(List<String> command);
  }

  private final Path javaExecutable;
  private final ProcessExecutor processExecutor;

  public JavaProcessChildLauncher(Path javaExecutable, ProcessExecutor processExecutor) {
    this.javaExecutable = javaExecutable;
    this.processExecutor = processExecutor;
  }

  @Override
  public int launch(JavaChildProcessRequest request) {
    List<String> command = new ArrayList<>();
    command.add(javaExecutable.toString());
    request
      .systemProperties()
      .entrySet()
      .stream()
      .sorted(Map.Entry.comparingByKey())
      .forEach(property -> command.add("-D" + property.getKey() + "=" + property.getValue()));
    command.add("-cp");
    command.add(request.executableJar().toString());
    command.add(request.mainClass());
    command.addAll(request.arguments());
    return processExecutor.execute(List.copyOf(command));
  }
}
