package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.ChildRuntimeLaunchRequest;
import com.seed4j.cli.bootstrap.domain.ChildRuntimeLauncher;
import com.seed4j.cli.bootstrap.domain.RuntimeMode;
import com.seed4j.cli.bootstrap.domain.RuntimeSelection;
import com.seed4j.cli.bootstrap.domain.Seed4JCliExecutablePath;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JavaProcessChildLauncher implements ChildRuntimeLauncher {

  private static final String PROPERTIES_LAUNCHER_MAIN_CLASS = "org.springframework.boot.loader.launch.PropertiesLauncher";
  private static final String RUNTIME_EXTENSION_START_CLASS_PROPERTY = "seed4j.cli.runtime.extension.start-class";

  private final Path javaExecutable;
  private final ChildProcessCommandExecutor processExecutor;
  private final RuntimeExtensionStartClassResolver runtimeExtensionStartClassResolver;
  private final RuntimeExtensionOverlayCache runtimeExtensionOverlayCache;
  private final RuntimeExtensionLoaderPathResolver runtimeExtensionLoaderPathResolver;

  public JavaProcessChildLauncher(
    Path javaExecutable,
    ChildProcessCommandExecutor processExecutor,
    RuntimeExtensionStartClassResolver runtimeExtensionStartClassResolver,
    RuntimeExtensionOverlayCache runtimeExtensionOverlayCache,
    RuntimeExtensionLoaderPathResolver runtimeExtensionLoaderPathResolver
  ) {
    this.javaExecutable = javaExecutable;
    this.processExecutor = processExecutor;
    this.runtimeExtensionStartClassResolver = runtimeExtensionStartClassResolver;
    this.runtimeExtensionOverlayCache = runtimeExtensionOverlayCache;
    this.runtimeExtensionLoaderPathResolver = runtimeExtensionLoaderPathResolver;
  }

  @Override
  public int launch(ChildRuntimeLaunchRequest request) {
    JavaChildProcessRequest javaRequest = javaChildProcessRequest(request);
    List<String> command = new ArrayList<>();
    command.add(javaExecutable.toString());
    javaRequest
      .systemProperties()
      .entrySet()
      .stream()
      .sorted(Map.Entry.comparingByKey())
      .forEach(property -> command.add("-D" + property.getKey() + "=" + property.getValue()));
    command.add("-cp");
    command.add(javaRequest.executableJar().path().toString());
    command.add(javaRequest.mainClass());
    command.addAll(javaRequest.arguments());
    return processExecutor.execute(List.copyOf(command));
  }

  private JavaChildProcessRequest javaChildProcessRequest(ChildRuntimeLaunchRequest request) {
    RuntimeSelection runtimeSelection = request.runtimeSelection();
    Map<String, String> systemProperties = new LinkedHashMap<>();
    systemProperties.put("seed4j.cli.runtime.child", "true");
    systemProperties.put("seed4j.cli.runtime.mode", runtimeSelection.mode().name().toLowerCase());
    runtimeSelection
      .distributionId()
      .ifPresent(distributionId -> systemProperties.put("seed4j.cli.runtime.distribution.id", distributionId.id()));
    runtimeSelection
      .distributionVersion()
      .ifPresent(distributionVersion -> systemProperties.put("seed4j.cli.runtime.distribution.version", distributionVersion.version()));
    runtimeSelection.extensionJarPath().ifPresent(extensionJarPath -> {
      Path rawExtensionJarPath = extensionJarPath.path();
      String extensionStartClass = runtimeExtensionStartClassResolver.resolve(rawExtensionJarPath);
      systemProperties.put(RUNTIME_EXTENSION_START_CLASS_PROPERTY, extensionStartClass);
      Path overlayClassesPath = runtimeExtensionOverlayCache.materialize(rawExtensionJarPath);
      systemProperties.put(
        "loader.path",
        runtimeExtensionLoaderPathResolver.resolve(overlayClassesPath, rawExtensionJarPath, request.executableJar().path())
      );
    });
    if (runtimeSelection.mode() == RuntimeMode.EXTENSION) {
      systemProperties.put("logging.config", "classpath:seed4j-cli-logback-spring.xml");
      if (request.debug().enabled()) {
        systemProperties.put("logging.level.com.seed4j.cli.bootstrap.domain", "DEBUG");
      } else {
        systemProperties.put("logging.level.root", "ERROR");
      }
      systemProperties.put("spring.main.log-startup-info", "false");
    }

    return new JavaChildProcessRequest(
      request.executableJar(),
      PROPERTIES_LAUNCHER_MAIN_CLASS,
      Map.copyOf(systemProperties),
      request.arguments().asList()
    );
  }

  private record JavaChildProcessRequest(
    Seed4JCliExecutablePath executableJar,
    String mainClass,
    Map<String, String> systemProperties,
    List<String> arguments
  ) {}
}
