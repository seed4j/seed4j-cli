package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.module.domain.nodejs.NodePackageManager;
import com.seed4j.module.domain.properties.Seed4JPropertyKey;
import com.seed4j.module.domain.properties.SpringConfigurationFormat;
import com.seed4j.module.domain.resource.Seed4JModulePropertyDefinition;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;

class KnownModulePropertyCompletionCandidates {

  private static final Seed4JPropertyKey NODE_PACKAGE_MANAGER = new Seed4JPropertyKey("nodePackageManager");
  private static final Seed4JPropertyKey SPRING_CONFIGURATION_FORMAT = new Seed4JPropertyKey("springConfigurationFormat");
  private static final Seed4JPropertyKey END_OF_LINE = new Seed4JPropertyKey("endOfLine");

  List<String> candidates(Seed4JModulePropertyDefinition property) {
    SequencedSet<String> candidates = new LinkedHashSet<>(knownCandidates(property));
    property.defaultValue().ifPresent(defaultValue -> candidates.add(defaultValue.get()));

    return candidates.stream().toList();
  }

  private List<String> knownCandidates(Seed4JModulePropertyDefinition property) {
    if (NODE_PACKAGE_MANAGER.equals(property.key())) {
      return Arrays.stream(NodePackageManager.values()).map(NodePackageManager::propertyKey).toList();
    }
    if (SPRING_CONFIGURATION_FORMAT.equals(property.key())) {
      return Arrays.stream(SpringConfigurationFormat.values()).map(SpringConfigurationFormat::get).toList();
    }
    if (END_OF_LINE.equals(property.key())) {
      return List.of("lf", "crlf");
    }

    return List.of();
  }
}
