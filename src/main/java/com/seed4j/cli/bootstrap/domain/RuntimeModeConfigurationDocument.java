package com.seed4j.cli.bootstrap.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record RuntimeModeConfigurationDocument(Map<Object, Object> configuration) {
  public RuntimeModeConfigurationDocument {
    Objects.requireNonNull(configuration, "configuration");
    configuration = new LinkedHashMap<>(configuration);
  }

  @Override //TODO Me explique porque o override?
  public Map<Object, Object> configuration() {
    return new LinkedHashMap<>(configuration);
  }
}
