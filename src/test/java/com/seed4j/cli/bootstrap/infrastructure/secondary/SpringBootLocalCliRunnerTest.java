package com.seed4j.cli.bootstrap.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.Seed4JCliArguments;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

@UnitTest
class SpringBootLocalCliRunnerTest {

  @Test
  void shouldLoadTheExternalConfigFileWhenItExists() throws IOException {
    Path userHomePath = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHomePath.resolve(".config/seed4j-cli/config.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(configPath, "seed4j:\n  runtime:\n    mode: standard\n");
    RecordingSpringApplicationBuilderOperations operations = new RecordingSpringApplicationBuilderOperations();

    runner(operations, new Seed4JCliHome(userHomePath), null).run(arguments("--version"));

    assertThat(operations.propertyEntries).containsExactly("spring.config.location=classpath:/config/,file:%s".formatted(configPath));
  }

  private static SpringBootLocalCliRunner runner(
    RecordingSpringApplicationBuilderOperations operations,
    Seed4JCliHome cliHome,
    String runtimeExtensionStartClass
  ) {
    return new SpringBootLocalCliRunner(() -> operations, new ZeroExitCodeResolver(), cliHome, () -> runtimeExtensionStartClass);
  }

  private static Seed4JCliArguments arguments(String... values) {
    return new Seed4JCliArguments(values);
  }

  private static final class RecordingSpringApplicationBuilderOperations implements SpringApplicationBuilderOperations {

    private final List<String> propertyEntries = new ArrayList<>();

    @Override
    public SpringApplicationBuilderOperations bannerModeOff() {
      return this;
    }

    @Override
    public SpringApplicationBuilderOperations webNone() {
      return this;
    }

    @Override
    public SpringApplicationBuilderOperations lazyInitialization(boolean lazyInitialization) {
      return this;
    }

    @Override
    public SpringApplicationBuilderOperations properties(String properties) {
      propertyEntries.add(properties);
      return this;
    }

    @Override
    public SpringApplicationContextAdapter run(String[] args) {
      return new SpringApplicationContextAdapter(null);
    }
  }

  private static final class ZeroExitCodeResolver extends SpringBootExitCodeResolver {

    @Override
    int resolve(SpringApplicationContextAdapter context) {
      return 0;
    }
  }

  @Test
  void shouldNotLoadTheLegacyExternalConfigFileLocation() throws IOException {
    Path userHomePath = Files.createTempDirectory("seed4j-cli-");
    Path legacyConfigPath = userHomePath.resolve(".config/seed4j-cli.yml");
    Files.createDirectories(legacyConfigPath.getParent());
    Files.writeString(legacyConfigPath, "seed4j:\n  runtime:\n    mode: extension\n");
    RecordingSpringApplicationBuilderOperations operations = new RecordingSpringApplicationBuilderOperations();

    runner(operations, new Seed4JCliHome(userHomePath), null).run(arguments("--version"));

    assertThat(operations.propertyEntries).isEmpty();
  }

  @Test
  void shouldAddSpringMainSourcesWhenRuntimeExtensionStartClassPropertyIsPresent() {
    RecordingSpringApplicationBuilderOperations operations = new RecordingSpringApplicationBuilderOperations();

    runner(operations, new Seed4JCliHome(Path.of("/tmp")), "com.mycompany.extension.ExtensionRuntimeApplication").run(
      arguments("--version")
    );

    assertThat(operations.propertyEntries).containsExactly("spring.main.sources=com.mycompany.extension.ExtensionRuntimeApplication");
  }

  @Test
  void shouldPreserveExternalConfigLocationWhenAlsoAddingSpringMainSources() throws IOException {
    Path userHomePath = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHomePath.resolve(".config/seed4j-cli/config.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(configPath, "seed4j:\n  runtime:\n    mode: extension\n");
    RecordingSpringApplicationBuilderOperations operations = new RecordingSpringApplicationBuilderOperations();

    runner(operations, new Seed4JCliHome(userHomePath), "com.mycompany.extension.ExtensionRuntimeApplication").run(arguments("--version"));

    assertThat(operations.propertyEntries).containsExactly(
      "spring.config.location=classpath:/config/,file:%s".formatted(configPath),
      "spring.main.sources=com.mycompany.extension.ExtensionRuntimeApplication"
    );
  }

  @Test
  void shouldIgnoreBlankRuntimeExtensionStartClassProperty() {
    RecordingSpringApplicationBuilderOperations operations = new RecordingSpringApplicationBuilderOperations();

    runner(operations, new Seed4JCliHome(Path.of("/tmp")), "   ").run(arguments("--version"));

    assertThat(operations.propertyEntries).isEmpty();
  }
}
