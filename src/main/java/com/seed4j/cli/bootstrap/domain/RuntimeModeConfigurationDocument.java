package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.LinkedHashMap;
import java.util.Map;

public record RuntimeModeConfigurationDocument(Map<Object, Object> configuration) {
  public RuntimeModeConfigurationDocument {
    Assert.notNull("configuration", configuration);
    configuration = new LinkedHashMap<>(configuration);
  }

  @Override
  public Map<Object, Object> configuration() {
    return new LinkedHashMap<>(configuration);
  }
}
