package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;

@UnitTest
class LocalSpringCliRunnerTest {

  @Test
  void shouldConfigureTheApplicationAsNonWeb() {
    RecordingApplicationBuilder builder = new RecordingApplicationBuilder();
    LocalSpringCliRunner runner = new LocalSpringCliRunner(() -> builder, context -> 0, () -> Path.of("/tmp"));

    runner.run(new String[] { "--version" });

    assertThat(builder.webApplicationType()).isEqualTo(WebApplicationType.NONE);
  }

  @Test
  void shouldDisableTheSpringBanner() {
    RecordingApplicationBuilder builder = new RecordingApplicationBuilder();
    LocalSpringCliRunner runner = new LocalSpringCliRunner(() -> builder, context -> 0, () -> Path.of("/tmp"));

    runner.run(new String[] { "--version" });

    assertThat(builder.bannerMode()).isEqualTo(Banner.Mode.OFF);
  }

  @Test
  void shouldLoadTheExternalConfigFileWhenItExists() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path configFile = userHome.resolve(".config/seed4j-cli.yml");
    Files.createDirectories(configFile.getParent());
    Files.writeString(configFile, "seed4j:\n  runtime:\n    mode: standard\n");
    RecordingApplicationBuilder builder = new RecordingApplicationBuilder();
    LocalSpringCliRunner runner = new LocalSpringCliRunner(() -> builder, context -> 0, () -> userHome);

    runner.run(new String[] { "--version" });

    assertThat(builder.properties()).isEqualTo("spring.config.location=classpath:/config/,file:%s".formatted(configFile));
  }

  @Test
  void shouldReturnTheSpringExitCode() {
    RecordingApplicationBuilder builder = new RecordingApplicationBuilder();
    LocalSpringCliRunner runner = new LocalSpringCliRunner(() -> builder, context -> 37, () -> Path.of("/tmp"));

    int exitCode = runner.run(new String[] { "--version" });

    assertThat(exitCode).isEqualTo(37);
  }

  @Test
  void shouldEnableLazyInitialization() {
    RecordingApplicationBuilder builder = new RecordingApplicationBuilder();
    LocalSpringCliRunner runner = new LocalSpringCliRunner(() -> builder, context -> 0, () -> Path.of("/tmp"));

    runner.run(new String[] { "--version" });

    assertThat(builder.lazyInitialization()).isTrue();
  }

  private static final class RecordingApplicationBuilder implements LocalSpringCliRunner.ApplicationBuilder {

    private Banner.Mode bannerMode;
    private Boolean lazyInitialization;
    private String properties;
    private WebApplicationType webApplicationType;

    @Override
    public LocalSpringCliRunner.ApplicationBuilder bannerMode(Banner.Mode bannerMode) {
      this.bannerMode = bannerMode;
      return this;
    }

    @Override
    public LocalSpringCliRunner.ApplicationBuilder web(WebApplicationType webApplicationType) {
      this.webApplicationType = webApplicationType;
      return this;
    }

    @Override
    public LocalSpringCliRunner.ApplicationBuilder lazyInitialization(boolean lazyInitialization) {
      this.lazyInitialization = lazyInitialization;
      return this;
    }

    @Override
    public LocalSpringCliRunner.ApplicationBuilder properties(String properties) {
      this.properties = properties;
      return this;
    }

    @Override
    public LocalSpringCliRunner.ApplicationContext run(String[] args) {
      return new LocalSpringCliRunner.ApplicationContext() {};
    }

    WebApplicationType webApplicationType() {
      return webApplicationType;
    }

    Banner.Mode bannerMode() {
      return bannerMode;
    }

    String properties() {
      return properties;
    }

    Boolean lazyInitialization() {
      return lazyInitialization;
    }
  }
}
