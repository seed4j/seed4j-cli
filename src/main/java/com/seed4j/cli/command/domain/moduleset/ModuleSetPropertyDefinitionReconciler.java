package com.seed4j.cli.command.domain.moduleset;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class ModuleSetPropertyDefinitionReconciler {

  Reconciliation reconcile(List<ModuleSetModule> selectedModules) {
    Map<ModuleSetPropertyKey, List<ModuleSetPropertyDefinition>> definitionsByKey = indexDefinitions(selectedModules);
    List<ModuleSetPropertyDefinition> definitions = new ArrayList<>();
    List<ModuleSetPropertyConflict> conflicts = new ArrayList<>();
    for (List<ModuleSetPropertyDefinition> sharedDefinitions : definitionsByKey.values()) {
      SharedDefinitionReconciliation reconciliation = reconcileSharedDefinitions(sharedDefinitions);
      reconciliation.definition().ifPresent(definitions::add);
      conflicts.addAll(reconciliation.conflicts());
    }
    return new Reconciliation(definitions, conflicts, definitionsByKey.keySet());
  }

  private static Map<ModuleSetPropertyKey, List<ModuleSetPropertyDefinition>> indexDefinitions(List<ModuleSetModule> selectedModules) {
    Map<ModuleSetPropertyKey, List<ModuleSetPropertyDefinition>> definitionsByKey = new LinkedHashMap<>();
    selectedModules
      .stream()
      .flatMap(module -> module.properties().stream())
      .forEach(definition -> definitionsByKey.computeIfAbsent(definition.key(), ignored -> new ArrayList<>()).add(definition));
    return definitionsByKey;
  }

  private static SharedDefinitionReconciliation reconcileSharedDefinitions(List<ModuleSetPropertyDefinition> definitions) {
    ModuleSetPropertyDefinition first = definitions.getFirst();
    List<ModuleSetPropertyType> types = definitions.stream().map(ModuleSetPropertyDefinition::type).distinct().sorted().toList();
    if (types.size() > 1) {
      return new SharedDefinitionReconciliation(Optional.empty(), List.of(new ModuleSetPropertyTypeConflict(first.key(), types)));
    }
    List<ModuleSetPropertyDefaultValue> defaults = definitions
      .stream()
      .flatMap(definition -> definition.defaultValue().stream())
      .distinct()
      .sorted(Comparator.comparing(ModuleSetPropertyDefaultValue::literal))
      .toList();
    List<ModuleSetPropertyDescription> descriptions = definitions
      .stream()
      .flatMap(definition -> definition.description().stream())
      .distinct()
      .sorted(Comparator.comparing(ModuleSetPropertyDescription::value))
      .toList();
    List<ModuleSetPropertyConflict> conflicts = new ArrayList<>();
    if (defaults.size() > 1) {
      conflicts.add(new ModuleSetPropertyDefaultConflict(first.key(), defaults));
    }
    if (descriptions.size() > 1) {
      conflicts.add(new ModuleSetPropertyDescriptionConflict(first.key(), descriptions));
    }
    return new SharedDefinitionReconciliation(
      Optional.of(
        new ModuleSetPropertyDefinition(
          first.key(),
          types.getFirst(),
          definitions.stream().anyMatch(ModuleSetPropertyDefinition::mandatory)
            ? ModuleSetPropertyRequirement.REQUIRED
            : ModuleSetPropertyRequirement.OPTIONAL,
          descriptions.size() == 1 ? Optional.of(descriptions.getFirst()) : Optional.empty(),
          defaults.size() == 1 ? Optional.of(defaults.getFirst()) : Optional.empty(),
          definitions
            .stream()
            .flatMap(definition -> definition.completionCandidates().stream())
            .distinct()
            .toList()
        )
      ),
      conflicts
    );
  }

  record Reconciliation(
    List<ModuleSetPropertyDefinition> definitions,
    List<ModuleSetPropertyConflict> conflicts,
    Set<ModuleSetPropertyKey> knownKeys
  ) {
    Reconciliation {
      definitions = List.copyOf(definitions);
      conflicts = List.copyOf(conflicts);
      knownKeys = Set.copyOf(knownKeys);
    }
  }

  private record SharedDefinitionReconciliation(
    Optional<ModuleSetPropertyDefinition> definition,
    List<ModuleSetPropertyConflict> conflicts
  ) {
    private SharedDefinitionReconciliation {
      conflicts = List.copyOf(conflicts);
    }
  }
}
