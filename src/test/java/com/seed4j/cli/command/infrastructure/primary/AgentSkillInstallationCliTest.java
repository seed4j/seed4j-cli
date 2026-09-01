package com.seed4j.cli.command.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.Seed4JCliApp;
import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.Seed4JCliArguments;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import com.seed4j.cli.bootstrap.infrastructure.secondary.SpringBootLocalCliRunner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@UnitTest
class AgentSkillInstallationCliTest {

  @Test
  void shouldInstallTheSkillUnderTheProcessWorkingDirectory(@TempDir Path temporaryDirectory) throws IOException, InterruptedException {
    IsolatedCliInvocation invocation = invokeSkillInstall(temporaryDirectory);

    assertThat(invocation.result().finished()).isTrue();
    assertThat(invocation.result().exitCode()).withFailMessage(invocation.result().output()).isZero();
    assertThat(invocation.result().output()).contains("Installed Seed4J CLI skill at %s.".formatted(invocation.destination()));
    try (Stream<Path> installedFiles = Files.walk(invocation.destination())) {
      assertThat(installedFiles.filter(Files::isRegularFile).map(invocation.destination()::relativize)).containsExactlyInAnyOrder(
        Path.of("SKILL.md"),
        Path.of("references/applying-modules.md"),
        Path.of("references/module-set-planning.md")
      );
    }
    assertThat(invocation.userHome().resolve(".agents")).doesNotExist();
  }

  private static IsolatedCliInvocation invokeSkillInstall(Path temporaryDirectory) throws IOException, InterruptedException {
    Path workingDirectory = temporaryDirectory.resolve("chosen-project");
    Path userHome = temporaryDirectory.resolve("home");
    Files.createDirectories(workingDirectory);
    Files.createDirectories(userHome);
    Path destination = workingDirectory.resolve(".agents/skills/seed4j-cli").toAbsolutePath().normalize();
    CliRunResult result = runSkillInstall(workingDirectory, userHome);
    return new IsolatedCliInvocation(userHome, destination, result);
  }

  private static CliRunResult runSkillInstall(Path workingDirectory, Path userHome) throws IOException, InterruptedException {
    ProcessBuilder processBuilder = new ProcessBuilder(
      javaExecutable().toString(),
      "-Duser.home=" + userHome,
      "-cp",
      System.getProperty("java.class.path"),
      TestCliLauncher.class.getName(),
      "skill",
      "install"
    )
      .directory(workingDirectory.toFile())
      .redirectErrorStream(true);

    Process process = processBuilder.start();
    boolean finished = process.waitFor(60, TimeUnit.SECONDS);
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    if (!finished) {
      process.destroyForcibly();
      return new CliRunResult(false, -1, output);
    }
    return new CliRunResult(true, process.exitValue(), output);
  }

  private static Path javaExecutable() {
    return Path.of(System.getProperty("java.home"), "bin", "java");
  }

  public static final class TestCliLauncher {

    public static void main(String[] arguments) {
      Seed4JCliHome cliHome = new Seed4JCliHome(Path.of(System.getProperty("user.home")));
      int exitCode = new SpringBootLocalCliRunner(Seed4JCliApp.class, cliHome).run(new Seed4JCliArguments(arguments));
      System.exit(exitCode);
    }
  }

  private record CliRunResult(boolean finished, int exitCode, String output) {}

  private record IsolatedCliInvocation(Path userHome, Path destination, CliRunResult result) {}
}
