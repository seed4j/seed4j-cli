package com.seed4j.cli.bootstrap.application;

import com.seed4j.cli.bootstrap.domain.BootstrapDiagnostics;
import com.seed4j.cli.bootstrap.domain.BootstrapOutput;
import com.seed4j.cli.bootstrap.domain.ChildRuntimeLauncher;
import com.seed4j.cli.bootstrap.domain.LocalCliRunner;
import com.seed4j.cli.bootstrap.domain.PackagedExecutableDetector;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionSelectionRepository;
import com.seed4j.cli.bootstrap.domain.RuntimeModeConfigurationRepository;
import com.seed4j.cli.bootstrap.domain.Seed4JCliArguments;
import com.seed4j.cli.bootstrap.domain.Seed4JCliLauncher;
import com.seed4j.cli.bootstrap.domain.Seed4JCliRuntime;
import com.seed4j.cli.shared.error.domain.Assert;

public class PreSpringBootstrapApplicationService {

  private final Seed4JCliLauncher seed4jCliLauncher;

  public PreSpringBootstrapApplicationService(
    Seed4JCliRuntime seed4jCliRuntime,
    RuntimeModeConfigurationRepository runtimeModeConfigurationRepository,
    RuntimeExtensionSelectionRepository runtimeExtensionSelectionRepository,
    ChildRuntimeLauncher childRuntimeLauncher,
    LocalCliRunner localCliRunner,
    PackagedExecutableDetector packagedExecutableDetector,
    BootstrapDiagnostics bootstrapDiagnostics,
    BootstrapOutput bootstrapOutput
  ) {
    Assert.notNull("seed4jCliRuntime", seed4jCliRuntime);
    Assert.notNull("runtimeModeConfigurationRepository", runtimeModeConfigurationRepository);
    Assert.notNull("runtimeExtensionSelectionRepository", runtimeExtensionSelectionRepository);
    Assert.notNull("childRuntimeLauncher", childRuntimeLauncher);
    Assert.notNull("localCliRunner", localCliRunner);
    Assert.notNull("packagedExecutableDetector", packagedExecutableDetector);
    Assert.notNull("bootstrapDiagnostics", bootstrapDiagnostics);
    Assert.notNull("bootstrapOutput", bootstrapOutput);
    this.seed4jCliLauncher = new Seed4JCliLauncher(
      seed4jCliRuntime,
      runtimeModeConfigurationRepository,
      runtimeExtensionSelectionRepository,
      childRuntimeLauncher,
      localCliRunner,
      packagedExecutableDetector,
      bootstrapDiagnostics,
      bootstrapOutput
    );
  }

  public int exitCodeFor(Seed4JCliArguments arguments) {
    Assert.notNull("arguments", arguments);
    return seed4jCliLauncher.launch(arguments);
  }
}
