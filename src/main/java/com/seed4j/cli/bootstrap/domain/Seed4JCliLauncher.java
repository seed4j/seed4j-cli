package com.seed4j.cli.bootstrap.domain;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.seed4j.cli.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.LoggerFactory;

public class Seed4JCliLauncher {

  private static final String PROPERTIES_LAUNCHER_MAIN_CLASS = "org.springframework.boot.loader.launch.PropertiesLauncher";
  private static final String BOOTSTRAP_LOGGER_NAME = "com.seed4j.cli.bootstrap.domain";
  private static final String SEED4J_VERSION_PROPERTY = "seed4j.cli.seed4j.version";
  private static final String RUNTIME_EXTENSION_START_CLASS_PROPERTY = "seed4j.cli.runtime.extension.start-class";

  private final Path userHome;
  private final Path executableJar;
  private final String currentSeed4JVersion;
  private final RuntimeModeConfigurationRepository runtimeModeConfigurationRepository;
  private final ChildProcessLauncher childProcessLauncher;
  private final LocalCliRunner localCliRunner;
  private final RuntimeExtensionLoaderPathResolver runtimeExtensionLoaderPathResolver;
  private final RuntimeExtensionOverlayCache runtimeExtensionOverlayCache;
  private final RuntimeExtensionStartClassResolver runtimeExtensionStartClassResolver;

  Seed4JCliLauncher(
    Path userHome,
    Path executableJar,
    String currentSeed4JVersion,
    RuntimeModeConfigurationRepository runtimeModeConfigurationRepository,
    ChildProcessLauncher childProcessLauncher,
    LocalCliRunner localCliRunner
  ) {
    this.userHome = userHome;
    this.executableJar = executableJar;
    this.currentSeed4JVersion = currentSeed4JVersion;
    this.runtimeModeConfigurationRepository = runtimeModeConfigurationRepository;
    this.childProcessLauncher = childProcessLauncher;
    this.localCliRunner = localCliRunner;
    this.runtimeExtensionLoaderPathResolver = new RuntimeExtensionLoaderPathResolver();
    this.runtimeExtensionOverlayCache = new RuntimeExtensionOverlayCache(userHome);
    this.runtimeExtensionStartClassResolver = new RuntimeExtensionStartClassResolver();
  }

  public int launch(String[] args) {
    return launch(args, false);
  }

  public int launch(String[] args, boolean childMode) {
    if (childMode) {
      return localCliRunner.run(args);
    }

    try {
      RuntimeSelection runtimeSelection = runtimeSelection();
      failWhenExtensionModeRunsOutsideARegularJar(runtimeSelection);
      if (shouldRunLocally(runtimeSelection)) {
        System.err.println("Standard mode is not running from a packaged CLI JAR. Falling back to local execution.");
        return localCliRunner.run(args);
      }

      if (runtimeSelection.mode() == RuntimeMode.EXTENSION && debugModeRequested(args)) {
        enableBootstrapDebugLoggingInParentProcess();
      }

      return childProcessLauncher.launch(javaChildProcessRequest(runtimeSelection, args));
    } catch (InvalidRuntimeConfigurationException runtimeConfigurationException) {
      System.err.println(runtimeConfigurationException.getMessage());
      return 1;
    }
  }

  private boolean shouldRunLocally(RuntimeSelection runtimeSelection) {
    return runtimeSelection.mode() == RuntimeMode.STANDARD && notRunningFromARegularJar();
  }

  private void failWhenExtensionModeRunsOutsideARegularJar(RuntimeSelection runtimeSelection) {
    if (runtimeSelection.mode() == RuntimeMode.EXTENSION && notRunningFromARegularJar()) {
      throw new InvalidRuntimeConfigurationException("Extension mode requires running the packaged CLI JAR.");
    }
  }

  private boolean notRunningFromARegularJar() {
    return !Files.isRegularFile(executableJar) || !executableJar.getFileName().toString().endsWith(".jar");
  }

  private JavaChildProcessRequest javaChildProcessRequest(RuntimeSelection runtimeSelection, String[] args) {
    Map<String, String> systemProperties = new LinkedHashMap<>();
    systemProperties.put("seed4j.cli.runtime.child", "true");
    systemProperties.put("seed4j.cli.runtime.mode", runtimeSelection.mode().name().toLowerCase());
    systemProperties.put(SEED4J_VERSION_PROPERTY, currentSeed4JVersion);
    runtimeSelection
      .distributionId()
      .ifPresent(distributionId -> systemProperties.put("seed4j.cli.runtime.distribution.id", distributionId));
    runtimeSelection
      .distributionVersion()
      .ifPresent(distributionVersion -> systemProperties.put("seed4j.cli.runtime.distribution.version", distributionVersion));
    runtimeSelection
      .extensionJarPath()
      .ifPresent(extensionJarPath -> {
        String extensionStartClass = runtimeExtensionStartClassResolver.resolve(extensionJarPath);
        systemProperties.put(RUNTIME_EXTENSION_START_CLASS_PROPERTY, extensionStartClass);
        Path overlayClassesPath = runtimeExtensionOverlayCache.materialize(extensionJarPath);
        systemProperties.put(
          "loader.path",
          runtimeExtensionLoaderPathResolver.resolve(overlayClassesPath, extensionJarPath, executableJar)
        );
      });
    if (runtimeSelection.mode() == RuntimeMode.EXTENSION) {
      systemProperties.put("logging.config", "classpath:seed4j-cli-logback-spring.xml");
      if (debugModeRequested(args)) {
        systemProperties.put("logging.level.com.seed4j.cli.bootstrap.domain", "DEBUG");
      } else {
        systemProperties.put("logging.level.root", "ERROR");
      }
      systemProperties.put("spring.main.log-startup-info", "false");
    }

    return new JavaChildProcessRequest(
      executableJar,
      PROPERTIES_LAUNCHER_MAIN_CLASS,
      Map.copyOf(systemProperties),
      List.copyOf(Arrays.asList(args)),
      runtimeSelection
    );
  }

  private RuntimeSelection runtimeSelection() {
    if (runtimeModeConfigurationRepository.readMode() == RuntimeMode.STANDARD) {
      return new RuntimeSelection(RuntimeMode.STANDARD, Optional.empty(), Optional.empty(), Optional.empty());
    }

    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      RuntimeExtensionConfiguration.withDefaultPaths(userHome)
    );

    return RuntimeSelection.resolve(runtimeConfiguration);
  }

  private static boolean debugModeRequested(String[] args) {
    return Arrays.stream(args).anyMatch("--debug"::equals);
  }

  @ExcludeFromGeneratedCodeCoverage(
    reason = "Non-Logback guard branch depends on the runtime SLF4J binding and this method is best-effort diagnostics"
  )
  private static void enableBootstrapDebugLoggingInParentProcess() {
    if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext loggerContext)) {
      return;
    }

    Logger logger = loggerContext.getLogger(BOOTSTRAP_LOGGER_NAME);
    logger.setLevel(Level.DEBUG);
  }
}
