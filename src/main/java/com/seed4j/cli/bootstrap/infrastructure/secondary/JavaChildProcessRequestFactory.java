package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.ChildRuntimeLaunchRequest;
import com.seed4j.cli.bootstrap.domain.RuntimeMode;
import com.seed4j.cli.bootstrap.domain.RuntimeSelection;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

class JavaChildProcessRequestFactory {

  private static final String PROPERTIES_LAUNCHER_MAIN_CLASS = "org.springframework.boot.loader.launch.PropertiesLauncher";
  private static final String RUNTIME_EXTENSION_START_CLASS_PROPERTY = "seed4j.cli.runtime.extension.start-class";

  private final RuntimeExtensionStartClassResolver runtimeExtensionStartClassResolver;
  private final RuntimeExtensionOverlayCache runtimeExtensionOverlayCache;
  private final RuntimeExtensionLoaderPathResolver runtimeExtensionLoaderPathResolver;

  JavaChildProcessRequestFactory(
    RuntimeExtensionStartClassResolver runtimeExtensionStartClassResolver,
    RuntimeExtensionOverlayCache runtimeExtensionOverlayCache,
    RuntimeExtensionLoaderPathResolver runtimeExtensionLoaderPathResolver
  ) {
    this.runtimeExtensionStartClassResolver = runtimeExtensionStartClassResolver;
    this.runtimeExtensionOverlayCache = runtimeExtensionOverlayCache;
    this.runtimeExtensionLoaderPathResolver = runtimeExtensionLoaderPathResolver;
  }

  JavaChildProcessRequest create(ChildRuntimeLaunchRequest request) {
    Map<String, String> systemProperties = new LinkedHashMap<>(runtimeProperties(request.runtimeSelection()));
    systemProperties.putAll(extensionArtifactProperties(request));
    systemProperties.putAll(extensionModeProperties(request));

    return new JavaChildProcessRequest(
      request.executableJar(),
      PROPERTIES_LAUNCHER_MAIN_CLASS,
      systemProperties,
      request.arguments().asList()
    );
  }

  private Map<String, String> runtimeProperties(RuntimeSelection runtimeSelection) {
    Map<String, String> properties = new LinkedHashMap<>();
    properties.put("seed4j.cli.runtime.child", "true");
    properties.put("seed4j.cli.runtime.mode", runtimeSelection.mode().name().toLowerCase());
    runtimeSelection
      .distributionId()
      .ifPresent(distributionId -> properties.put("seed4j.cli.runtime.distribution.id", distributionId.id()));
    runtimeSelection
      .distributionVersion()
      .ifPresent(distributionVersion -> properties.put("seed4j.cli.runtime.distribution.version", distributionVersion.version()));

    return properties;
  }

  private Map<String, String> extensionArtifactProperties(ChildRuntimeLaunchRequest request) {
    return request
      .runtimeSelection()
      .extensionJarPath()
      .map(extensionJarPath -> extensionArtifactProperties(request, extensionJarPath.path()))
      .orElseGet(Map::of);
  }

  private Map<String, String> extensionArtifactProperties(ChildRuntimeLaunchRequest request, Path extensionJarPath) {
    String extensionStartClass = runtimeExtensionStartClassResolver.resolve(extensionJarPath);
    Path overlayClassesPath = runtimeExtensionOverlayCache.materialize(extensionJarPath);

    return Map.of(
      RUNTIME_EXTENSION_START_CLASS_PROPERTY,
      extensionStartClass,
      "loader.path",
      runtimeExtensionLoaderPathResolver.resolve(overlayClassesPath, extensionJarPath, request.executableJar().path())
    );
  }

  private Map<String, String> extensionModeProperties(ChildRuntimeLaunchRequest request) {
    if (request.runtimeSelection().mode() != RuntimeMode.EXTENSION) {
      return Map.of();
    }

    if (request.debug().enabled()) {
      return extensionModeProperties("logging.level.com.seed4j.cli.bootstrap.domain", "DEBUG");
    }

    return extensionModeProperties("logging.level.root", "ERROR");
  }

  private Map<String, String> extensionModeProperties(String loggingLevelProperty, String loggingLevel) {
    return Map.of(
      "logging.config",
      "classpath:seed4j-cli-logback-spring.xml",
      loggingLevelProperty,
      loggingLevel,
      "spring.main.log-startup-info",
      "false"
    );
  }
}
