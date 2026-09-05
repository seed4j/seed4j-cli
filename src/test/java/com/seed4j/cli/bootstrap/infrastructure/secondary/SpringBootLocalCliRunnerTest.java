package com.seed4j.cli.bootstrap.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seed4j.cli.Seed4JCliApp;
import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.Seed4JCliArguments;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@UnitTest
class SpringBootLocalCliRunnerTest {

  @Nested
  class Configuration {

    @Test
    void shouldLoadTheExternalConfigFileWhenItExists() throws IOException {
      Path userHomePath = Files.createTempDirectory("seed4j-cli-");
      Path configPath = userHomePath.resolve(".config/seed4j-cli/config.yml");
      Files.createDirectories(configPath.getParent());
      Files.writeString(configPath, "seed4j:\n  runtime:\n    mode: standard\n");
      RunnerFixture fixture = new RunnerFixture(new Seed4JCliHome(userHomePath), null, 0);

      fixture.run("--version");

      assertThat(fixture.operations.propertyEntries).containsExactly(
        "spring.config.location=classpath:/config/,file:%s".formatted(configPath)
      );
    }

    @Test
    void shouldNotLoadTheLegacyExternalConfigFileLocation() throws IOException {
      Path userHomePath = Files.createTempDirectory("seed4j-cli-");
      Path legacyConfigPath = userHomePath.resolve(".config/seed4j-cli.yml");
      Files.createDirectories(legacyConfigPath.getParent());
      Files.writeString(legacyConfigPath, "seed4j:\n  runtime:\n    mode: extension\n");
      RunnerFixture fixture = new RunnerFixture(new Seed4JCliHome(userHomePath), null, 0);

      fixture.run("--version");

      assertThat(fixture.operations.propertyEntries).isEmpty();
    }

    @Test
    void shouldAddSpringMainSourcesWhenRuntimeExtensionStartClassPropertyIsPresent() {
      RunnerFixture fixture = new RunnerFixture(
        new Seed4JCliHome(Path.of("/tmp")),
        "com.mycompany.extension.ExtensionRuntimeApplication",
        0
      );

      fixture.run("--version");

      assertThat(fixture.operations.propertyEntries).containsExactly(
        "spring.main.sources=com.mycompany.extension.ExtensionRuntimeApplication"
      );
    }

    @Test
    void shouldPreserveExternalConfigLocationWhenAlsoAddingSpringMainSources() throws IOException {
      Path userHomePath = Files.createTempDirectory("seed4j-cli-");
      Path configPath = userHomePath.resolve(".config/seed4j-cli/config.yml");
      Files.createDirectories(configPath.getParent());
      Files.writeString(configPath, "seed4j:\n  runtime:\n    mode: extension\n");
      RunnerFixture fixture = new RunnerFixture(new Seed4JCliHome(userHomePath), "com.mycompany.extension.ExtensionRuntimeApplication", 0);

      fixture.run("--version");

      assertThat(fixture.operations.propertyEntries).containsExactly(
        "spring.config.location=classpath:/config/,file:%s".formatted(configPath),
        "spring.main.sources=com.mycompany.extension.ExtensionRuntimeApplication"
      );
    }

    @Test
    void shouldIgnoreBlankRuntimeExtensionStartClassProperty() {
      RunnerFixture fixture = new RunnerFixture(new Seed4JCliHome(Path.of("/tmp")), "   ", 0);

      fixture.run("--version");

      assertThat(fixture.operations.propertyEntries).isEmpty();
    }
  }

  @Nested
  class Execution {

    @Test
    void shouldConfigureTheApplicationAsNonWeb() {
      RunnerFixture fixture = new RunnerFixture(new Seed4JCliHome(Path.of("/tmp")), null, 0);

      fixture.run("--version");

      assertThat(fixture.operations.webNoneCalls).isEqualTo(1);
    }

    @Test
    void shouldDisableTheSpringBanner() {
      RunnerFixture fixture = new RunnerFixture(new Seed4JCliHome(Path.of("/tmp")), null, 0);

      fixture.run("--version");

      assertThat(fixture.operations.bannerModeOffCalls).isEqualTo(1);
    }

    @Test
    void shouldReturnTheSpringExitCode() {
      int expectedExitCode = 37;
      RunnerFixture fixture = new RunnerFixture(new Seed4JCliHome(Path.of("/tmp")), null, expectedExitCode);

      int exitCode = fixture.run("--version");

      assertThat(exitCode).isEqualTo(expectedExitCode);
      verify(fixture.exitCodeResolver, times(1)).resolve(any(SpringApplicationContextAdapter.class));
    }

    @Test
    void shouldReturnNonZeroWhenCommandFails(@TempDir Path temporaryDirectory) {
      SpringBootLocalCliRunner runner = new SpringBootLocalCliRunner(Seed4JCliApp.class, new Seed4JCliHome(temporaryDirectory));

      int exitCode = runner.run(arguments("unknown-command"));

      assertThat(exitCode).isNotZero();
    }

    @Test
    void shouldEnableLazyInitialization() {
      RunnerFixture fixture = new RunnerFixture(new Seed4JCliHome(Path.of("/tmp")), null, 0);

      fixture.run("--version");

      assertThat(fixture.operations.lazyInitialization).isTrue();
    }

    @Test
    void shouldCreateFreshSpringApplicationBuilderOperationsForEachRun() {
      List<RecordingSpringApplicationBuilderOperations> createdOperations = new ArrayList<>();
      SpringBootLocalCliRunner runner = new SpringBootLocalCliRunner(
        () -> {
          RecordingSpringApplicationBuilderOperations operations = new RecordingSpringApplicationBuilderOperations();
          createdOperations.add(operations);
          return operations;
        },
        mock(SpringBootExitCodeResolver.class),
        new Seed4JCliHome(Path.of("/tmp")),
        () -> null
      );

      runner.run(arguments("--version"));
      runner.run(arguments("list"));

      assertThat(createdOperations).hasSize(2);
      assertThat(createdOperations.get(0).runArguments).containsExactly(List.of("--version"));
      assertThat(createdOperations.get(1).runArguments).containsExactly(List.of("list"));
    }
  }

  private static Seed4JCliArguments arguments(String... values) {
    return new Seed4JCliArguments(values);
  }

  private static final class RunnerFixture {

    private final RecordingSpringApplicationBuilderOperations operations = new RecordingSpringApplicationBuilderOperations();
    private final SpringBootExitCodeResolver exitCodeResolver;
    private final SpringBootLocalCliRunner runner;

    private RunnerFixture(Seed4JCliHome cliHome, String runtimeExtensionStartClass, int exitCode) {
      exitCodeResolver = mock(SpringBootExitCodeResolver.class);
      when(exitCodeResolver.resolve(any(SpringApplicationContextAdapter.class))).thenReturn(exitCode);
      runner = new SpringBootLocalCliRunner(() -> operations, exitCodeResolver, cliHome, () -> runtimeExtensionStartClass);
    }

    private int run(String... values) {
      return runner.run(arguments(values));
    }
  }

  private static final class RecordingSpringApplicationBuilderOperations implements SpringApplicationBuilderOperations {

    private final List<String> propertyEntries = new ArrayList<>();
    private final List<List<String>> runArguments = new ArrayList<>();
    private int bannerModeOffCalls;
    private int webNoneCalls;
    private Boolean lazyInitialization;

    @Override
    public SpringApplicationBuilderOperations bannerModeOff() {
      bannerModeOffCalls++;
      return this;
    }

    @Override
    public SpringApplicationBuilderOperations webNone() {
      webNoneCalls++;
      return this;
    }

    @Override
    public SpringApplicationBuilderOperations lazyInitialization(boolean lazyInitialization) {
      this.lazyInitialization = lazyInitialization;
      return this;
    }

    @Override
    public SpringApplicationBuilderOperations properties(String properties) {
      propertyEntries.add(properties);
      return this;
    }

    @Override
    public SpringApplicationContextAdapter run(String[] args) {
      runArguments.add(List.of(args));
      return new SpringApplicationContextAdapter(null);
    }
  }
}
