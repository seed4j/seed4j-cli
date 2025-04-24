package tech.jhipster.lite.cli.command;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import tech.jhipster.lite.cli.UnitTest;
import tech.jhipster.lite.cli.shared.error.domain.MissingMandatoryValueException;

@UnitTest
class JHipsterCommandTest {

  @ParameterizedTest
  @NullAndEmptySource
  void shouldNotBuildWithInvalidCommand(String command) {
    assertThatThrownBy(() -> new JHipsterCommand(new Command(command))).isInstanceOf(MissingMandatoryValueException.class);
  }
}
