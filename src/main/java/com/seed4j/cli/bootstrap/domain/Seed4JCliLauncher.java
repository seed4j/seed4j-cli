package com.seed4j.cli.bootstrap.domain;

import java.nio.file.Path;

public class Seed4JCliLauncher {

  private final Path executableJar;
  private final RuntimeModeConfigurationRepository runtimeModeConfigurationRepository;
  private final RuntimeExtensionSelectionRepository runtimeExtensionSelectionRepository;
  private final ChildRuntimeLauncher childRuntimeLauncher;
  private final LocalCliRunner localCliRunner;
  private final PackagedExecutableDetector packagedExecutableDetector;
  private final BootstrapDiagnostics bootstrapDiagnostics;
  private final BootstrapOutput bootstrapOutput;
  private final boolean childMode;

  Seed4JCliLauncher(
    Path executableJar,
    RuntimeModeConfigurationRepository runtimeModeConfigurationRepository,
    RuntimeExtensionSelectionRepository runtimeExtensionSelectionRepository,
    ChildRuntimeLauncher childRuntimeLauncher,
    LocalCliRunner localCliRunner,
    PackagedExecutableDetector packagedExecutableDetector,
    BootstrapDiagnostics bootstrapDiagnostics,
    BootstrapOutput bootstrapOutput,
    boolean childMode
  ) {
    this.executableJar = executableJar;
    this.runtimeModeConfigurationRepository = runtimeModeConfigurationRepository;
    this.runtimeExtensionSelectionRepository = runtimeExtensionSelectionRepository;
    this.childRuntimeLauncher = childRuntimeLauncher;
    this.localCliRunner = localCliRunner;
    this.packagedExecutableDetector = packagedExecutableDetector;
    this.bootstrapDiagnostics = bootstrapDiagnostics;
    this.bootstrapOutput = bootstrapOutput;
    this.childMode = childMode;
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

      return childRuntimeLauncher.launch(childRuntimeLaunchRequest(runtimeSelection, arguments));
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

  private ChildRuntimeLaunchRequest childRuntimeLaunchRequest(RuntimeSelection runtimeSelection, Seed4JCliArguments arguments) {
    return new ChildRuntimeLaunchRequest(executableJar, runtimeSelection, arguments, arguments.contains("--debug"));
  }

  private RuntimeSelection runtimeSelection() {
    if (runtimeModeConfigurationRepository.readMode() == RuntimeMode.STANDARD) {
      return RuntimeSelection.standard();
    }

    return runtimeExtensionSelectionRepository.activeRuntimeSelection();
  }
}
