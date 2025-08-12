package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
class JHLiteCommandRunner implements CommandLineRunner, ExitCodeGenerator {

  private final Seed4JCommandsFactory command;
  private int exitCode;

  public JHLiteCommandRunner(Seed4JCommandsFactory command) {
    this.command = command;
  }

  @Override
  @ExcludeFromGeneratedCodeCoverage(reason = "Don not need to test when using picocli framework")
  public void run(String... args) {
    CommandLine commandLine = new CommandLine(command.buildCommandSpec());
    exitCode = commandLine.execute(args);
  }

  @Override
  @ExcludeFromGeneratedCodeCoverage(reason = "Don not need to test when using picocli framework")
  public int getExitCode() {
    return exitCode;
  }
}
