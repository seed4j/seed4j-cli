package com.seed4j.cli.bootstrap.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

@UnitTest
class SpringBootLocalCliRunnerTest {

  @Test
  void shouldConfigureTheApplicationAsNonWeb() {
    RecordingSpringApplicationBuilderOperations recordingSpringApplicationBuilderOperations =
      new RecordingSpringApplicationBuilderOperations();
    SpringBootLocalCliRunner runner = new SpringBootLocalCliRunner(
      recordingSpringApplicationBuilderOperations,
      new RecordingSpringBootExitCodeResolver(),
      Path.of("/tmp"),
      () -> null
    );

    runner.run(new String[] { "--version" });

    assertThat(recordingSpringApplicationBuilderOperations.webNoneCalls()).isEqualTo(1);
  }

  @Test
  void shouldDisableTheSpringBanner() {
    RecordingSpringApplicationBuilderOperations recordingSpringApplicationBuilderOperations =
      new RecordingSpringApplicationBuilderOperations();
    SpringBootLocalCliRunner runner = new SpringBootLocalCliRunner(
      recordingSpringApplicationBuilderOperations,
      new RecordingSpringBootExitCodeResolver(),
      Path.of("/tmp"),
      () -> null
    );

    runner.run(new String[] { "--version" });

    assertThat(recordingSpringApplicationBuilderOperations.bannerModeOffCalls()).isEqualTo(1);
  }

  @Test
  void shouldLoadTheExternalConfigFileWhenItExists() throws IOException {
    Path userHomePath = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHomePath.resolve(".config/seed4j-cli/config.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(configPath, "seed4j:\n  runtime:\n    mode: standard\n");
    RecordingSpringApplicationBuilderOperations recordingSpringApplicationBuilderOperations =
      new RecordingSpringApplicationBuilderOperations();
    SpringBootLocalCliRunner runner = new SpringBootLocalCliRunner(
      recordingSpringApplicationBuilderOperations,
      new RecordingSpringBootExitCodeResolver(),
      userHomePath,
      () -> null
    );

    runner.run(new String[] { "--version" });

    assertThat(recordingSpringApplicationBuilderOperations.lastProperty()).isEqualTo(
      "spring.config.location=classpath:/config/,file:%s".formatted(configPath)
    );
  }

  @Test
  void shouldNotLoadTheLegacyExternalConfigFileLocation() throws IOException {
    Path userHomePath = Files.createTempDirectory("seed4j-cli-");
    Path legacyConfigPath = userHomePath.resolve(".config/seed4j-cli.yml");
    Files.createDirectories(legacyConfigPath.getParent());
    Files.writeString(legacyConfigPath, "seed4j:\n  runtime:\n    mode: extension\n");
    RecordingSpringApplicationBuilderOperations recordingSpringApplicationBuilderOperations =
      new RecordingSpringApplicationBuilderOperations();
    SpringBootLocalCliRunner runner = new SpringBootLocalCliRunner(
      recordingSpringApplicationBuilderOperations,
      new RecordingSpringBootExitCodeResolver(),
      userHomePath,
      () -> null
    );

    runner.run(new String[] { "--version" });

    assertThat(recordingSpringApplicationBuilderOperations.propertyEntries()).isEmpty();
  }

  @Test
  void shouldReturnTheSpringExitCode() {
    RecordingSpringApplicationBuilderOperations recordingSpringApplicationBuilderOperations =
      new RecordingSpringApplicationBuilderOperations();
    RecordingSpringBootExitCodeResolver recordingSpringBootExitCodeResolver = new RecordingSpringBootExitCodeResolver(37);
    SpringBootLocalCliRunner runner = new SpringBootLocalCliRunner(
      recordingSpringApplicationBuilderOperations,
      recordingSpringBootExitCodeResolver,
      Path.of("/tmp"),
      () -> null
    );

    int exitCode = runner.run(new String[] { "--version" });

    assertThat(exitCode).isEqualTo(37);
    assertThat(recordingSpringBootExitCodeResolver.resolveCalls()).isEqualTo(1);
  }

  @Test
  void shouldEnableLazyInitialization() {
    RecordingSpringApplicationBuilderOperations recordingSpringApplicationBuilderOperations =
      new RecordingSpringApplicationBuilderOperations();
    SpringBootLocalCliRunner runner = new SpringBootLocalCliRunner(
      recordingSpringApplicationBuilderOperations,
      new RecordingSpringBootExitCodeResolver(),
      Path.of("/tmp"),
      () -> null
    );

    runner.run(new String[] { "--version" });

    assertThat(recordingSpringApplicationBuilderOperations.lazyInitialization()).isTrue();
  }

  @Test
  void shouldAddSpringMainSourcesWhenRuntimeExtensionStartClassPropertyIsPresent() {
    RecordingSpringApplicationBuilderOperations recordingSpringApplicationBuilderOperations =
      new RecordingSpringApplicationBuilderOperations();
    SpringBootLocalCliRunner runner = new SpringBootLocalCliRunner(
      recordingSpringApplicationBuilderOperations,
      new RecordingSpringBootExitCodeResolver(),
      Path.of("/tmp"),
      () -> "com.mycompany.extension.ExtensionRuntimeApplication"
    );

    runner.run(new String[] { "--version" });

    assertThat(recordingSpringApplicationBuilderOperations.lastProperty()).isEqualTo(
      "spring.main.sources=com.mycompany.extension.ExtensionRuntimeApplication"
    );
  }

  @Test
  void shouldPreserveExternalConfigLocationWhenAlsoAddingSpringMainSources() throws IOException {
    Path userHomePath = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHomePath.resolve(".config/seed4j-cli/config.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(configPath, "seed4j:\n  runtime:\n    mode: extension\n");
    RecordingSpringApplicationBuilderOperations recordingSpringApplicationBuilderOperations =
      new RecordingSpringApplicationBuilderOperations();
    SpringBootLocalCliRunner runner = new SpringBootLocalCliRunner(
      recordingSpringApplicationBuilderOperations,
      new RecordingSpringBootExitCodeResolver(),
      userHomePath,
      () -> "com.mycompany.extension.ExtensionRuntimeApplication"
    );

    runner.run(new String[] { "--version" });

    assertThat(recordingSpringApplicationBuilderOperations.propertyEntries()).containsExactly(
      "spring.config.location=classpath:/config/,file:%s".formatted(configPath),
      "spring.main.sources=com.mycompany.extension.ExtensionRuntimeApplication"
    );
  }

  @Test
  void shouldIgnoreBlankRuntimeExtensionStartClassProperty() {
    RecordingSpringApplicationBuilderOperations recordingSpringApplicationBuilderOperations =
      new RecordingSpringApplicationBuilderOperations();
    SpringBootLocalCliRunner runner = new SpringBootLocalCliRunner(
      recordingSpringApplicationBuilderOperations,
      new RecordingSpringBootExitCodeResolver(),
      Path.of("/tmp"),
      () -> "   "
    );

    runner.run(new String[] { "--version" });

    assertThat(recordingSpringApplicationBuilderOperations.propertyEntries()).isEmpty();
  }

  private static final class RecordingSpringApplicationBuilderOperations implements SpringApplicationBuilderOperations {

    private int bannerModeOffCalls;
    private int webNoneCalls;
    private Boolean lazyInitialization;
    private String lastProperty;
    private final List<String> propertyEntries = new ArrayList<>();

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
      this.lastProperty = properties;
      this.propertyEntries.add(properties);
      return this;
    }

    @Override
    public SpringApplicationContextAdapter run(String[] args) {
      return new SpringApplicationContextAdapter(null);
    }

    private int webNoneCalls() {
      return webNoneCalls;
    }

    private int bannerModeOffCalls() {
      return bannerModeOffCalls;
    }

    private Boolean lazyInitialization() {
      return lazyInitialization;
    }

    private String lastProperty() {
      return lastProperty;
    }

    private List<String> propertyEntries() {
      return List.copyOf(propertyEntries);
    }
  }

  private static final class RecordingSpringBootExitCodeResolver extends SpringBootExitCodeResolver {

    private final int exitCode;
    private int resolveCalls;

    private RecordingSpringBootExitCodeResolver() {
      this(0);
    }

    private RecordingSpringBootExitCodeResolver(int exitCode) {
      this.exitCode = exitCode;
    }

    @Override
    int resolve(SpringApplicationContextAdapter context) {
      resolveCalls++;
      return exitCode;
    }

    private int resolveCalls() {
      return resolveCalls;
    }
  }
}
