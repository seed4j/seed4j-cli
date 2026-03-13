package com.seed4j.cli.command.infrastructure.primary;

import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class StandardRuntimeSystemProperties implements RuntimeSystemProperties {

  @Override
  public Map<String, String> values() {
    return System.getProperties()
      .entrySet()
      .stream()
      .collect(Collectors.toUnmodifiableMap(entry -> entry.getKey().toString(), entry -> entry.getValue().toString()));
  }
}
