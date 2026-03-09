package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.module.application.Seed4JModulesApplicationService;
import com.seed4j.module.domain.resource.Seed4JModuleResource;
import com.seed4j.module.domain.resource.Seed4JModulesResources;
import java.util.Comparator;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Model.CommandSpec;

@Component
class ListModulesCommand implements Seed4JCommand, Callable<Integer> {

  private static final int MINIMAL_SPACES_BETWEEN_SLUG_AND_DESCRIPTION = 2;

  private final Seed4JModulesApplicationService modules;

  public ListModulesCommand(Seed4JModulesApplicationService modules) {
    this.modules = modules;
  }

  @Override
  public CommandSpec spec() {
    CommandSpec spec = CommandSpec.wrapWithoutInspection(this).name("list");
    spec.usageMessage().description("List available seed4j modules");

    return spec;
  }

  @Override
  public String name() {
    return "list";
  }

  @Override
  public Integer call() {
    Seed4JModulesResources modulesResources = modules.resources();
    System.out.printf("Available seed4j modules (%s):%n", modulesResources.stream().count());
    modulesResources.stream().sorted(byModuleSlug()).forEach(printModule(maxSlugLength(modulesResources)));

    return ExitCode.OK;
  }

  private static Comparator<Seed4JModuleResource> byModuleSlug() {
    return Comparator.comparing(moduleResource -> moduleResource.slug().get());
  }

  private int maxSlugLength(Seed4JModulesResources modulesResources) {
    return modulesResources
      .stream()
      .map(Seed4JModuleResource::slug)
      .map(slug -> slug.get())
      .mapToInt(String::length)
      .max()
      .orElse(0);
  }

  private Consumer<? super Seed4JModuleResource> printModule(int maxSlugLength) {
    return moduleResource -> {
      String spacesBetweenSlugAndDescription = " ".repeat(
        maxSlugLength - moduleResource.slug().get().length() + MINIMAL_SPACES_BETWEEN_SLUG_AND_DESCRIPTION
      );
      System.out.printf("  %s%s%s%n", moduleResource.slug(), spacesBetweenSlugAndDescription, moduleResource.apiDoc().operation());
    };
  }
}
