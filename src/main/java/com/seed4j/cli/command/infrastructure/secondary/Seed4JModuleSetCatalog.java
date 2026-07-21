package com.seed4j.cli.command.infrastructure.secondary;

import com.seed4j.cli.command.domain.moduleset.ModuleSetCatalog;
import com.seed4j.cli.command.domain.moduleset.ModuleSetDependency;
import com.seed4j.cli.command.domain.moduleset.ModuleSetDependencyType;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModule;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDefaultValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDefinition;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDescription;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyKey;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyRequirement;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyType;
import com.seed4j.cli.command.domain.moduleset.ModuleSetSlug;
import com.seed4j.cli.shared.error.domain.Assert;
import com.seed4j.module.domain.Seed4JModuleSlug;
import com.seed4j.module.domain.Seed4JSlug;
import com.seed4j.module.domain.resource.Seed4JModulePropertyDefinition;
import java.util.List;

public class Seed4JModuleSetCatalog implements ModuleSetCatalog {

  private final Seed4JModulesResourcesReader resourcesReader;
  private final Seed4JLandscapeModuleSorter landscapeSorter;

  public Seed4JModuleSetCatalog(Seed4JModulesResourcesReader resourcesReader, Seed4JLandscapeModuleSorter landscapeSorter) {
    Assert.notNull("resourcesReader", resourcesReader);
    Assert.notNull("landscapeSorter", landscapeSorter);
    this.resourcesReader = resourcesReader;
    this.landscapeSorter = landscapeSorter;
  }

  @Override
  public List<ModuleSetModule> modules() {
    return resourcesReader
      .resources()
      .stream()
      .map(resource ->
        new ModuleSetModule(
          new ModuleSetSlug(resource.slug().get()),
          resource
            .organization()
            .dependencies()
            .stream()
            .map(dependency -> new ModuleSetDependency(ModuleSetDependencyType.valueOf(dependency.type().name()), dependency.slug().get()))
            .toList(),
          resource.propertiesDefinition().stream().map(Seed4JModuleSetCatalog::property).toList(),
          resource.organization().feature().map(Seed4JSlug::get)
        )
      )
      .toList();
  }

  private static ModuleSetPropertyDefinition property(Seed4JModulePropertyDefinition definition) {
    return new ModuleSetPropertyDefinition(
      new ModuleSetPropertyKey(definition.key().get()),
      ModuleSetPropertyType.valueOf(definition.type().name()),
      definition.isMandatory() ? ModuleSetPropertyRequirement.REQUIRED : ModuleSetPropertyRequirement.OPTIONAL,
      definition.description().map(description -> new ModuleSetPropertyDescription(description.get())),
      definition.defaultValue().map(defaultValue -> new ModuleSetPropertyDefaultValue(defaultValue.get())),
      definition
        .defaultValue()
        .map(defaultValue -> List.of(defaultValue.get()))
        .orElseGet(List::of)
    );
  }

  @Override
  public List<ModuleSetSlug> sort(List<ModuleSetSlug> requestedModules) {
    List<Seed4JModuleSlug> slugs = requestedModules
      .stream()
      .map(slug -> new Seed4JModuleSlug(slug.value()))
      .toList();
    return landscapeSorter
      .sort(slugs)
      .stream()
      .map(slug -> new ModuleSetSlug(slug.get()))
      .toList();
  }
}
