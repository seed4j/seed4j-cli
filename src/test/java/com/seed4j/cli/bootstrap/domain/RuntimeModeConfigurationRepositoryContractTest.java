package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class RuntimeModeConfigurationRepositoryContractTest {

  @Test
  void shouldDeclarePrepareModeChangeAsAbstractContractMethod() throws NoSuchMethodException {
    boolean prepareModeChangeUsesDefaultImplementation = RuntimeModeConfigurationRepository.class.getMethod(
      "prepareModeChange",
      RuntimeMode.class
    ).isDefault();

    assertThat(prepareModeChangeUsesDefaultImplementation).isFalse();
  }
}
