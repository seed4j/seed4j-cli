package com.seed4j.cli.command.infrastructure.secondary;

import com.seed4j.cli.command.domain.moduleset.ModuleSetBooleanParameterValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetCatalog;
import com.seed4j.cli.command.domain.moduleset.ModuleSetDependency;
import com.seed4j.cli.command.domain.moduleset.ModuleSetDependencyType;
import com.seed4j.cli.command.domain.moduleset.ModuleSetIntegerParameterValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModule;
import com.seed4j.cli.command.domain.moduleset.ModuleSetParameterValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDefaultValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDefinition;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDescription;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyKey;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyRequirement;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyType;
import com.seed4j.cli.command.domain.moduleset.ModuleSetSlug;
import com.seed4j.cli.command.domain.moduleset.ModuleSetStringParameterValue;
import com.seed4j.cli.shared.error.domain.Assert;
import com.seed4j.module.application.Seed4JModulesApplicationService;
import com.seed4j.module.domain.Seed4JModuleSlug;
import com.seed4j.module.domain.Seed4JSlug;
import com.seed4j.module.domain.landscape.Seed4JLandscapeDependency;
import com.seed4j.module.domain.landscape.Seed4JLandscapeElementType;
import com.seed4j.module.domain.properties.Seed4JPropertyType;
import com.seed4j.module.domain.resource.Seed4JModulePropertyDefinition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class Seed4JModuleSetCatalog implements ModuleSetCatalog {

  private final Seed4JModulesApplicationService modules;

  public Seed4JModuleSetCatalog(Seed4JModulesApplicationService modules) {
    Assert.notNull("modules", modules);
    this.modules = modules;
  }

  @Override
  public List<ModuleSetModule> modules() {
    List<ModuleSetModule> catalogModules = modules
      .resources()
      .stream()
      .map(resource ->
        new ModuleSetModule(
          new ModuleSetSlug(resource.slug().get()),
          resource.organization().dependencies().stream().map(Seed4JModuleSetCatalog::dependency).toList(),
          resource.propertiesDefinition().stream().map(Seed4JModuleSetCatalog::property).toList(),
          resource.organization().feature().map(Seed4JSlug::get)
        )
      )
      .toList();
    validatePropertyTypes(catalogModules);
    return catalogModules;
  }

  private static void validatePropertyTypes(List<ModuleSetModule> modules) {
    Map<ModuleSetPropertyKey, Set<ModuleSetPropertyType>> typesByKey = new TreeMap<>((first, second) ->
      first.value().compareTo(second.value())
    );
    modules
      .stream()
      .flatMap(module -> module.properties().stream())
      .forEach(definition -> typesByKey.computeIfAbsent(definition.key(), ignored -> new TreeSet<>()).add(definition.type()));
    typesByKey.forEach((key, types) -> {
      if (types.size() > 1) {
        throw new IllegalArgumentException(
          "Conflicting module set property types for %s: %s".formatted(
            key.value(),
            types.stream().map(Enum::name).collect(Collectors.joining(", "))
          )
        );
      }
    });
  }

  private static ModuleSetDependency dependency(Seed4JLandscapeDependency dependency) {
    return new ModuleSetDependency(dependencyType(dependency.type()), dependency.slug().get());
  }

  private static ModuleSetDependencyType dependencyType(Seed4JLandscapeElementType type) {
    return switch (type) {
      case MODULE -> ModuleSetDependencyType.MODULE;
      case FEATURE -> ModuleSetDependencyType.FEATURE;
    };
  }

  private static ModuleSetPropertyDefinition property(Seed4JModulePropertyDefinition definition) {
    ModuleSetPropertyType type = propertyType(definition.type());
    return new ModuleSetPropertyDefinition(
      new ModuleSetPropertyKey(definition.key().get()),
      type,
      definition.isMandatory() ? ModuleSetPropertyRequirement.REQUIRED : ModuleSetPropertyRequirement.OPTIONAL,
      definition.description().map(description -> new ModuleSetPropertyDescription(description.get())),
      definition.defaultValue().map(defaultValue -> defaultValue(type, defaultValue.get())),
      definition
        .defaultValue()
        .map(defaultValue -> List.of(defaultValue.get()))
        .orElseGet(List::of)
    );
  }

  private static ModuleSetPropertyDefaultValue defaultValue(ModuleSetPropertyType type, String literal) {
    return new ModuleSetPropertyDefaultValue(parameterValue(type, literal), literal);
  }

  private static ModuleSetParameterValue parameterValue(ModuleSetPropertyType type, String literal) {
    return switch (type) {
      case STRING -> new ModuleSetStringParameterValue(literal);
      case INTEGER -> new ModuleSetIntegerParameterValue(Integer.valueOf(literal));
      case BOOLEAN -> new ModuleSetBooleanParameterValue(booleanValue(literal));
    };
  }

  private static boolean booleanValue(String literal) {
    return switch (literal) {
      case "true" -> true;
      case "false" -> false;
      default -> throw new IllegalArgumentException("Invalid BOOLEAN module set property default: " + literal);
    };
  }

  private static ModuleSetPropertyType propertyType(Seed4JPropertyType type) {
    return switch (type) {
      case BOOLEAN -> ModuleSetPropertyType.BOOLEAN;
      case INTEGER -> ModuleSetPropertyType.INTEGER;
      case STRING -> ModuleSetPropertyType.STRING;
    };
  }

  @Override
  public List<ModuleSetSlug> sort(List<ModuleSetSlug> requestedModules) {
    List<Seed4JModuleSlug> slugs = requestedModules
      .stream()
      .map(slug -> new Seed4JModuleSlug(slug.value()))
      .toList();
    return modules
      .landscape()
      .sort(slugs)
      .stream()
      .map(slug -> new ModuleSetSlug(slug.get()))
      .toList();
  }
}
