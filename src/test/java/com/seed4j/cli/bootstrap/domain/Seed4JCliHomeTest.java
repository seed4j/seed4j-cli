package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.shared.error.domain.MissingMandatoryValueException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class Seed4JCliHomeTest {

  @Test
  void shouldDeriveDefaultCliPathsFromHomeDirectory() {
    Path homePath = Path.of("/tmp/seed4j-home");
    Seed4JCliHome cliHome = new Seed4JCliHome(homePath);

    assertThat(cliHome.configPath()).isEqualTo(homePath.resolve(".config/seed4j-cli/config.yml"));
    assertThat(cliHome.runtimeCacheDirectory()).isEqualTo(homePath.resolve(".config/seed4j-cli/runtime/cache"));
  }

  @Test
  void shouldCreateCliHomeFromString() {
    Path homePath = Path.of("/tmp/seed4j-home");
    Seed4JCliHome cliHome = Seed4JCliHome.from("/tmp/seed4j-home");

    assertThat(cliHome.configPath()).isEqualTo(homePath.resolve(".config/seed4j-cli/config.yml"));
    assertThat(cliHome.runtimeCacheDirectory()).isEqualTo(homePath.resolve(".config/seed4j-cli/runtime/cache"));
  }

  @Test
  void shouldRejectMissingHomePath() {
    assertThatThrownBy(() -> new Seed4JCliHome(null))
      .isExactlyInstanceOf(MissingMandatoryValueException.class)
      .hasMessageContaining("\"path\"");
  }

  @Test
  void shouldRejectMissingHomePathFromFactory() {
    assertThatThrownBy(() -> Seed4JCliHome.from(null))
      .isExactlyInstanceOf(MissingMandatoryValueException.class)
      .hasMessageContaining("\"path\"");
  }

  @Test
  void shouldRejectBlankHomePathFromFactory() {
    assertThatThrownBy(() -> Seed4JCliHome.from("   "))
      .isExactlyInstanceOf(MissingMandatoryValueException.class)
      .hasMessageContaining("\"path\"");
  }
}
