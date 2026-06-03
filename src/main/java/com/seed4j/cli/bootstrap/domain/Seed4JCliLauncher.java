package com.seed4j.cli.bootstrap.domain;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class Seed4JCliLauncher {

  private static final String PROPERTIES_LAUNCHER_MAIN_CLASS = "org.springframework.boot.loader.launch.PropertiesLauncher";
  private static final String RUNTIME_EXTENSION_START_CLASS_PROPERTY = "seed4j.cli.runtime.extension.start-class";

  private final Seed4JCliHome cliHome;
  private final Path executableJar;
  private final RuntimeModeConfigurationRepository runtimeModeConfigurationRepository;
  private final RuntimeExtensionSelectionRepository runtimeExtensionSelectionRepository;
  private final ChildProcessLauncher childProcessLauncher;
  private final LocalCliRunner localCliRunner;
  private final PackagedExecutableDetector packagedExecutableDetector;
  private final BootstrapDiagnostics bootstrapDiagnostics;
  private final BootstrapOutput bootstrapOutput;
  private final boolean childMode;
  private final RuntimeExtensionLoaderPathResolver runtimeExtensionLoaderPathResolver;
  private final RuntimeExtensionOverlayCache runtimeExtensionOverlayCache;
  private final RuntimeExtensionStartClassResolver runtimeExtensionStartClassResolver;

  Seed4JCliLauncher(
    Seed4JCliHome cliHome,
    Path executableJar,
    RuntimeModeConfigurationRepository runtimeModeConfigurationRepository,
    RuntimeExtensionSelectionRepository runtimeExtensionSelectionRepository,
    ChildProcessLauncher childProcessLauncher,
    LocalCliRunner localCliRunner,
    boolean childMode
  ) {
    this(
      cliHome,
      executableJar,
      runtimeModeConfigurationRepository,
      runtimeExtensionSelectionRepository,
      childProcessLauncher,
      localCliRunner,
      executablePath -> executablePath.getFileName().toString().endsWith(".jar"),
      () -> {},
      new BootstrapOutput() {
        @Override
        public void standardModeFallback() {}

        @Override
        public void runtimeConfigurationError(String message) {}
      },
      extensionJarPath -> {
        throw new InvalidRuntimeConfigurationException("Extension mode requires runtime extension start-class resolution.");
      },
      extensionJarPath -> {
        throw new InvalidRuntimeConfigurationException("Extension mode requires runtime extension overlay cache materialization.");
      },
      (overlayClassesPath, extensionJarPath, executableJarPath) -> {
        throw new InvalidRuntimeConfigurationException("Extension mode requires runtime extension loader path resolution.");
      },
      childMode
    );
  }

  Seed4JCliLauncher(
    Seed4JCliHome cliHome,
    Path executableJar,
    RuntimeModeConfigurationRepository runtimeModeConfigurationRepository,
    RuntimeExtensionSelectionRepository runtimeExtensionSelectionRepository,
    ChildProcessLauncher childProcessLauncher,
    LocalCliRunner localCliRunner,
    PackagedExecutableDetector packagedExecutableDetector,
    BootstrapDiagnostics bootstrapDiagnostics,
    BootstrapOutput bootstrapOutput,
    RuntimeExtensionStartClassResolver runtimeExtensionStartClassResolver,
    RuntimeExtensionOverlayCache runtimeExtensionOverlayCache,
    RuntimeExtensionLoaderPathResolver runtimeExtensionLoaderPathResolver,
    boolean childMode
  ) {
    this.cliHome = cliHome;
    this.executableJar = executableJar;
    this.runtimeModeConfigurationRepository = runtimeModeConfigurationRepository;
    this.runtimeExtensionSelectionRepository = runtimeExtensionSelectionRepository;
    this.childProcessLauncher = childProcessLauncher;
    this.localCliRunner = localCliRunner;
    this.packagedExecutableDetector = packagedExecutableDetector;
    this.bootstrapDiagnostics = bootstrapDiagnostics;
    this.bootstrapOutput = bootstrapOutput;
    this.childMode = childMode;
    this.runtimeExtensionLoaderPathResolver = runtimeExtensionLoaderPathResolver;
    this.runtimeExtensionOverlayCache = runtimeExtensionOverlayCache;
    this.runtimeExtensionStartClassResolver = runtimeExtensionStartClassResolver;
  }

  public int launch(Seed4JCliArguments arguments) {
    if (childMode) {
      return localCliRunner.run(arguments);
    }

    try {
      RuntimeSelection runtimeSelection = runtimeSelection();
      failWhenExtensionModeRunsOutsideARegularJar(runtimeSelection);
      if (shouldRunLocally(runtimeSelection)) {
        bootstrapOutput.standardModeFallback();
        return localCliRunner.run(arguments);
      }

      if (runtimeSelection.mode() == RuntimeMode.EXTENSION && arguments.contains("--debug")) {
        bootstrapDiagnostics.enableDebugLogging();
      }

      return childProcessLauncher.launch(javaChildProcessRequest(runtimeSelection, arguments));
    } catch (InvalidRuntimeConfigurationException runtimeConfigurationException) {
      bootstrapOutput.runtimeConfigurationError(runtimeConfigurationException.getMessage());
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
    return !packagedExecutableDetector.packagedExecutable(executableJar);
  }

  private JavaChildProcessRequest javaChildProcessRequest(RuntimeSelection runtimeSelection, Seed4JCliArguments arguments) {
    Map<String, String> systemProperties = new LinkedHashMap<>();
    systemProperties.put("seed4j.cli.runtime.child", "true");
    systemProperties.put("seed4j.cli.runtime.mode", runtimeSelection.mode().name().toLowerCase());
    runtimeSelection
      .distributionId()
      .ifPresent(distributionId -> systemProperties.put("seed4j.cli.runtime.distribution.id", distributionId.id()));
    runtimeSelection
      .distributionVersion()
      .ifPresent(distributionVersion -> systemProperties.put("seed4j.cli.runtime.distribution.version", distributionVersion.version()));
    runtimeSelection
      .extensionJarPath()
      .ifPresent(extensionJarPath -> {
        Path rawExtensionJarPath = extensionJarPath.path();
        String extensionStartClass = runtimeExtensionStartClassResolver.resolve(rawExtensionJarPath);
        systemProperties.put(RUNTIME_EXTENSION_START_CLASS_PROPERTY, extensionStartClass);
        Path overlayClassesPath = runtimeExtensionOverlayCache.materialize(rawExtensionJarPath);
        systemProperties.put(
          "loader.path",
          runtimeExtensionLoaderPathResolver.resolve(overlayClassesPath, rawExtensionJarPath, executableJar)
        );
      });
    if (runtimeSelection.mode() == RuntimeMode.EXTENSION) {
      systemProperties.put("logging.config", "classpath:seed4j-cli-logback-spring.xml");
      if (arguments.contains("--debug")) {
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
      arguments.asList(),
      runtimeSelection
    );
  }

  private RuntimeSelection runtimeSelection() {
    if (runtimeModeConfigurationRepository.readMode() == RuntimeMode.STANDARD) {
      return RuntimeSelection.standard();
    }

    return runtimeExtensionSelectionRepository.activeRuntimeSelection();
  }
}
