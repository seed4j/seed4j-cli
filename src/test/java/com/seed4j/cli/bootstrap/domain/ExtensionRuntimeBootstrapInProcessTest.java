package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.Seed4JCliApp;
import com.seed4j.cli.SystemOutputCaptor;
import com.seed4j.cli.UnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@UnitTest
class ExtensionRuntimeBootstrapInProcessTest {

  private static final String RUNTIME_MODE_PROPERTY = "seed4j.cli.runtime.mode";
  private static final String DISTRIBUTION_ID_PROPERTY = "seed4j.cli.runtime.distribution.id";
  private static final String DISTRIBUTION_VERSION_PROPERTY = "seed4j.cli.runtime.distribution.version";
  private static final String LOADER_PATH_PROPERTY = "loader.path";
  private static final String BASELINE_RUNTIME_MODE = "baseline-mode";
  private static final String CURRENT_CLI_VERSION = "0.0.1-SNAPSHOT";
  private static final String EXTENSION_ONLY_SLUG = "runtime-extension-list-only";

  @Test
  void shouldExecuteVersionCommandInExtensionModeUsingInProcessChildLauncher() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    ExtensionRuntimeFixture.install(userHome);
    Path executableJar = Files.createTempFile("seed4j-cli-", ".jar");
    LocalSpringCliRunner localCliRunner = localCliRunner(userHome);
    InProcessChildProcessLauncher childProcessLauncher = new InProcessChildProcessLauncher(localCliRunner);
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(userHome, executableJar, CURRENT_CLI_VERSION, childProcessLauncher, localCliRunner);
    ScopedSystemProperties baselineProperties = ScopedSystemProperties.capture(
      Set.of(RUNTIME_MODE_PROPERTY, DISTRIBUTION_ID_PROPERTY, DISTRIBUTION_VERSION_PROPERTY, LOADER_PATH_PROPERTY)
    );

    try {
      System.setProperty(RUNTIME_MODE_PROPERTY, BASELINE_RUNTIME_MODE);
      System.clearProperty(DISTRIBUTION_ID_PROPERTY);
      System.clearProperty(DISTRIBUTION_VERSION_PROPERTY);
      System.clearProperty(LOADER_PATH_PROPERTY);

      try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
        int exitCode = launcher.launch(new String[] { "--version" });

        assertThat(exitCode).isZero();
        assertThat(outputCaptor.getOutput())
          .contains("Runtime mode: extension")
          .contains("Distribution ID: company-extension")
          .contains("Distribution version: 1.0.0");
      }

      assertThat(System.getProperty(RUNTIME_MODE_PROPERTY)).isEqualTo(BASELINE_RUNTIME_MODE);
      assertThat(System.getProperty(DISTRIBUTION_ID_PROPERTY)).isNull();
      assertThat(System.getProperty(DISTRIBUTION_VERSION_PROPERTY)).isNull();
      assertThat(System.getProperty(LOADER_PATH_PROPERTY)).isNull();
    } finally {
      baselineProperties.restore();
    }
  }

  @Test
  void shouldListExtensionOnlyModuleWhenRunningInExtensionModeUsingInProcessChildLauncher() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-extension-list-");
    ExtensionRuntimeFixture.installWithListExtensionModule(userHome);
    Path executableJar = Files.createTempFile("seed4j-cli-", ".jar");
    LocalSpringCliRunner localCliRunner = localCliRunner(userHome);
    InProcessChildProcessLauncher childProcessLauncher = new InProcessChildProcessLauncher(localCliRunner);
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(userHome, executableJar, CURRENT_CLI_VERSION, childProcessLauncher, localCliRunner);
    ScopedSystemProperties baselineProperties = ScopedSystemProperties.capture(
      Set.of(RUNTIME_MODE_PROPERTY, DISTRIBUTION_ID_PROPERTY, DISTRIBUTION_VERSION_PROPERTY, LOADER_PATH_PROPERTY)
    );

    try {
      System.setProperty(RUNTIME_MODE_PROPERTY, BASELINE_RUNTIME_MODE);
      System.clearProperty(DISTRIBUTION_ID_PROPERTY);
      System.clearProperty(DISTRIBUTION_VERSION_PROPERTY);
      System.clearProperty(LOADER_PATH_PROPERTY);

      try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
        int exitCode = launcher.launch(new String[] { "list" });

        assertThat(exitCode).isZero();
        assertThat(outputCaptor.getOutput()).contains(EXTENSION_ONLY_SLUG);
      }

      assertThat(System.getProperty(RUNTIME_MODE_PROPERTY)).isEqualTo(BASELINE_RUNTIME_MODE);
      assertThat(System.getProperty(DISTRIBUTION_ID_PROPERTY)).isNull();
      assertThat(System.getProperty(DISTRIBUTION_VERSION_PROPERTY)).isNull();
      assertThat(System.getProperty(LOADER_PATH_PROPERTY)).isNull();
    } finally {
      baselineProperties.restore();
    }
  }

  private static LocalSpringCliRunner localCliRunner(Path userHome) {
    return new LocalSpringCliRunner(
      () -> new SpringApplicationBuilderAdapter(new SpringApplicationBuilder(Seed4JCliApp.class)),
      ExtensionRuntimeBootstrapInProcessTest::resolveExitCode,
      () -> userHome
    );
  }

  private static int resolveExitCode(LocalSpringCliRunner.ApplicationContext context) {
    SpringApplicationContextAdapter springApplicationContext = (SpringApplicationContextAdapter) context;
    return SpringApplication.exit(springApplicationContext.context());
  }

  private static final class InProcessChildProcessLauncher implements ChildProcessLauncher {

    private final LocalCliRunner localCliRunner;

    private InProcessChildProcessLauncher(LocalCliRunner localCliRunner) {
      this.localCliRunner = localCliRunner;
    }

    @Override
    public int launch(JavaChildProcessRequest request) {
      ScopedSystemProperties scopedSystemProperties = ScopedSystemProperties.capture(request.systemProperties().keySet());
      try {
        request.systemProperties().forEach(System::setProperty);
        return localCliRunner.run(request.arguments().toArray(String[]::new));
      } finally {
        scopedSystemProperties.restore();
      }
    }
  }

  private record ScopedSystemProperties(Map<String, Optional<String>> originalValues) {
    private static ScopedSystemProperties capture(Set<String> propertyKeys) {
      Map<String, Optional<String>> capturedValues = new LinkedHashMap<>();
      for (String propertyKey : propertyKeys) {
        capturedValues.put(propertyKey, Optional.ofNullable(System.getProperty(propertyKey)));
      }
      return new ScopedSystemProperties(Map.copyOf(capturedValues));
    }

    private void restore() {
      originalValues.forEach((propertyKey, propertyValue) -> {
        if (propertyValue.isPresent()) {
          System.setProperty(propertyKey, propertyValue.orElseThrow());
        } else {
          System.clearProperty(propertyKey);
        }
      });
    }
  }

  private record SpringApplicationContextAdapter(
    ConfigurableApplicationContext context
  ) implements LocalSpringCliRunner.ApplicationContext {}

  private static final class SpringApplicationBuilderAdapter implements LocalSpringCliRunner.ApplicationBuilder {

    private final SpringApplicationBuilder springApplicationBuilder;

    private SpringApplicationBuilderAdapter(SpringApplicationBuilder springApplicationBuilder) {
      this.springApplicationBuilder = springApplicationBuilder;
    }

    @Override
    public LocalSpringCliRunner.ApplicationBuilder bannerMode(Banner.Mode bannerMode) {
      springApplicationBuilder.bannerMode(bannerMode);
      return this;
    }

    @Override
    public LocalSpringCliRunner.ApplicationBuilder web(WebApplicationType webApplicationType) {
      springApplicationBuilder.web(webApplicationType);
      return this;
    }

    @Override
    public LocalSpringCliRunner.ApplicationBuilder lazyInitialization(boolean lazyInitialization) {
      springApplicationBuilder.lazyInitialization(lazyInitialization);
      return this;
    }

    @Override
    public LocalSpringCliRunner.ApplicationBuilder properties(String properties) {
      springApplicationBuilder.properties(properties);
      return this;
    }

    @Override
    public LocalSpringCliRunner.ApplicationContext run(String[] args) {
      ConfigurableApplicationContext applicationContext = springApplicationBuilder.run(args);
      return new SpringApplicationContextAdapter(applicationContext);
    }
  }
}
