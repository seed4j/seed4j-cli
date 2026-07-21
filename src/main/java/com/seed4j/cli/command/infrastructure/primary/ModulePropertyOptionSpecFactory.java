package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDefinition;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDescription;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyType;
import com.seed4j.cli.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;
import com.seed4j.module.domain.nodejs.NodePackageManager;
import com.seed4j.module.domain.properties.Seed4JPropertyDescription;
import com.seed4j.module.domain.properties.Seed4JPropertyKey;
import com.seed4j.module.domain.properties.Seed4JPropertyType;
import com.seed4j.module.domain.properties.SpringConfigurationFormat;
import com.seed4j.module.domain.resource.Seed4JModulePropertyDefinition;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import picocli.CommandLine.Model.OptionSpec;

class ModulePropertyOptionSpecFactory {

  private static final KnownModulePropertyCompletionCandidates KNOWN_COMPLETION_CANDIDATES = new KnownModulePropertyCompletionCandidates();

  OptionSpec moduleOption(Seed4JModulePropertyDefinition property) {
    return option(property, toOptionType(property.type()), completionCandidates(property));
  }

  OptionSpec moduleSetOption(ModuleSetPropertyDefinition definition) {
    List<String> candidates = moduleSetCompletionCandidates(definition);
    return OptionSpec.builder(toDashedFormat(definition.key().value()))
      .description(
        "%s%s%s".formatted(
          definition.description().map(ModuleSetPropertyDescription::value).orElse(""),
          exampleValues(candidates),
          definition.mandatory() ? " (required)" : ""
        )
      )
      .paramLabel("<%s%s>".formatted(definition.key().value().toLowerCase(), definition.mandatory() ? "*" : ""))
      .type(toOptionType(definition.type()))
      .completionCandidates(candidates)
      .build();
  }

  private static List<String> moduleSetCompletionCandidates(ModuleSetPropertyDefinition definition) {
    LinkedHashSet<String> candidates = new LinkedHashSet<>(knownCandidates(definition.key().value()));
    candidates.addAll(definition.completionCandidates());
    return candidates.stream().toList();
  }

  private static List<String> knownCandidates(String key) {
    return switch (key) {
      case "nodePackageManager" -> Arrays.stream(NodePackageManager.values()).map(NodePackageManager::propertyKey).toList();
      case "springConfigurationFormat" -> Arrays.stream(SpringConfigurationFormat.values()).map(SpringConfigurationFormat::get).toList();
      case "endOfLine" -> List.of("lf", "crlf");
      default -> List.of();
    };
  }

  private OptionSpec option(Seed4JModulePropertyDefinition property, Class<?> optionType, List<String> candidates) {
    return OptionSpec.builder(toDashedFormat(property.key()))
      .description(
        "%s%s%s".formatted(
          property.description().map(Seed4JPropertyDescription::get).orElse(""),
          exampleValues(candidates),
          property.isMandatory() ? " (required)" : ""
        )
      )
      .paramLabel("<%s%s>".formatted(property.key().get().toLowerCase(), property.isMandatory() ? "*" : ""))
      .type(optionType)
      .completionCandidates(candidates)
      .build();
  }

  private List<String> completionCandidates(Seed4JModulePropertyDefinition property) {
    return KNOWN_COMPLETION_CANDIDATES.candidates(property);
  }

  private static String exampleValues(List<String> candidates) {
    if (candidates.isEmpty()) {
      return "";
    }
    return " e.g. " + String.join(", ", candidates);
  }

  @ExcludeFromGeneratedCodeCoverage(reason = "There is no Seed4J module using a property with the BOOLEAN type")
  private static Class<?> toOptionType(Seed4JPropertyType type) {
    return switch (type) {
      case BOOLEAN -> boolean.class;
      case INTEGER -> int.class;
      case STRING -> String.class;
    };
  }

  private static Class<?> toOptionType(ModuleSetPropertyType type) {
    return switch (type) {
      case BOOLEAN -> boolean.class;
      case INTEGER -> int.class;
      case STRING -> String.class;
    };
  }

  static String toDashedFormat(Seed4JPropertyKey key) {
    return toDashedFormat(key.get());
  }

  static String toDashedFormat(String key) {
    StringBuilder dashed = new StringBuilder("--");
    for (char c : key.toCharArray()) {
      if (Character.isUpperCase(c)) {
        dashed.append('-').append(Character.toLowerCase(c));
      } else {
        dashed.append(c);
      }
    }
    return dashed.toString();
  }
}
