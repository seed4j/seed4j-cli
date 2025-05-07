package tech.jhipster.lite.cli.command.infrastructure.primary;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;
import tech.jhipster.lite.cli.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;

@Component
class JHLiteCommandRunner implements CommandLineRunner, ExitCodeGenerator {

  private final JHLiteCommand command;
  private final IFactory factory;
  private int exitCode;

  public JHLiteCommandRunner(JHLiteCommand command, IFactory factory) {
    this.command = command;
    this.factory = factory;
  }

  @Override
  @ExcludeFromGeneratedCodeCoverage(reason = "Don not need to test when using picocli framework")
  public void run(String... args) {
    exitCode = new CommandLine(command, factory).execute(args);
  }

  @Override
  @ExcludeFromGeneratedCodeCoverage(reason = "Don not need to test when using picocli framework")
  public int getExitCode() {
    return exitCode;
  }
}
