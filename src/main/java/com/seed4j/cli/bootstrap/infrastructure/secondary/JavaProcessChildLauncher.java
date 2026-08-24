package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.ChildRuntimeLaunchRequest;
import com.seed4j.cli.bootstrap.domain.ChildRuntimeLauncher;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JavaProcessChildLauncher implements ChildRuntimeLauncher {

  private final Path javaExecutable;
  private final ChildProcessCommandExecutor processExecutor;
  private final JavaChildProcessRequestFactory requestFactory;

  public JavaProcessChildLauncher(
    Path javaExecutable,
    ChildProcessCommandExecutor processExecutor,
    RuntimeExtensionStartClassResolver runtimeExtensionStartClassResolver,
    RuntimeExtensionOverlayCache runtimeExtensionOverlayCache,
    RuntimeExtensionLoaderPathResolver runtimeExtensionLoaderPathResolver
  ) {
    this.javaExecutable = javaExecutable;
    this.processExecutor = processExecutor;
    requestFactory = new JavaChildProcessRequestFactory(
      runtimeExtensionStartClassResolver,
      runtimeExtensionOverlayCache,
      runtimeExtensionLoaderPathResolver
    );
  }

  @Override
  public int launch(ChildRuntimeLaunchRequest request) {
    return processExecutor.execute(command(requestFactory.create(request)));
  }

  private List<String> command(JavaChildProcessRequest request) {
    List<String> command = new ArrayList<>();
    command.add(javaExecutable.toString());
    command.addAll(systemPropertyArguments(request));
    command.add("-cp");
    command.add(request.executableJar().path().toString());
    command.add(request.mainClass());
    command.addAll(request.arguments());

    return List.copyOf(command);
  }

  private List<String> systemPropertyArguments(JavaChildProcessRequest request) {
    return request
      .systemProperties()
      .entrySet()
      .stream()
      .sorted(Map.Entry.comparingByKey())
      .map(property -> "-D" + property.getKey() + "=" + property.getValue())
      .toList();
  }
}
