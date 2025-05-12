package tech.jhipster.lite.cli.command.infrastructure.primary;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import tech.jhipster.lite.cli.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;

@Component
class JHLiteCommandRunner implements CommandLineRunner, ExitCodeGenerator {

  private final JHLiteCommand command;
  private int exitCode;

  public JHLiteCommandRunner(JHLiteCommand command) {
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
