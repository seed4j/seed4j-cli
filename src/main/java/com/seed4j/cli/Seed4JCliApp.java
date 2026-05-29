package com.seed4j.cli;

import com.seed4j.Seed4JApp;
import com.seed4j.cli.bootstrap.application.PreSpringRuntimeEnvironmentProvider;
import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringLauncherAssembler;
import com.seed4j.cli.bootstrap.infrastructure.secondary.CurrentProcessPreSpringRuntimeEnvironmentProvider;
import com.seed4j.cli.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackageClasses = { Seed4JApp.class, Seed4JCliApp.class })
@ExcludeFromGeneratedCodeCoverage(reason = "Not testing logs")
public class Seed4JCliApp {

  interface BootstrapExitCodeResolver {
    int exitCodeFor(String[] args);
  }

  static void main(String[] args) {
    System.exit(productionExitCode(args, productionBootstrapExitCodeResolver()));
  }

  static int productionExitCode(String[] args, BootstrapExitCodeResolver bootstrapExitCodeResolver) {
    return bootstrapExitCodeResolver.exitCodeFor(args);
  }

  static BootstrapExitCodeResolver productionBootstrapExitCodeResolver() {
    return productionBootstrapExitCodeResolver(new CurrentProcessPreSpringRuntimeEnvironmentProvider(), new PreSpringLauncherAssembler());
  }

  static BootstrapExitCodeResolver productionBootstrapExitCodeResolver(
    PreSpringRuntimeEnvironmentProvider preSpringRuntimeEnvironmentProvider,
    PreSpringLauncherAssembler preSpringLauncherAssembler
  ) {
    return args -> preSpringLauncherAssembler.exitCodeFor(preSpringRuntimeEnvironmentProvider.current(), args);
  }
}
