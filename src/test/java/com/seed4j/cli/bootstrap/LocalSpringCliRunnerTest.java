package com.seed4j.cli.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
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

  /*
  [TEST] The local runner loads ~/.config/seed4j-cli.yml when it exists
  [TEST] The local runner returns the Spring exit code
  */

  private static final class RecordingApplicationBuilder implements LocalSpringCliRunner.ApplicationBuilder {

    private Banner.Mode bannerMode;
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
      return this;
    }

    @Override
    public LocalSpringCliRunner.ApplicationBuilder properties(String properties) {
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
  }
}
