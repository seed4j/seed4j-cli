package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.bootstrap.RuntimeSelection;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class StandardRuntimeSelectionProvider implements RuntimeSelectionProvider {

  @Override
  public RuntimeSelection runtimeSelection() {
    Map<String, String> systemProperties = System.getProperties()
      .entrySet()
      .stream()
      .collect(java.util.stream.Collectors.toUnmodifiableMap(entry -> entry.getKey().toString(), entry -> entry.getValue().toString()));

    return new SystemPropertyRuntimeSelectionProvider(systemProperties).runtimeSelection();
  }
}
